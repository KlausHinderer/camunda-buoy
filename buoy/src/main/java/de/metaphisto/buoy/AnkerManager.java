package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.LogFilePersistence;
import de.metaphisto.buoy.persistence.PersistenceFormat;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.variable.impl.value.NullValueImpl;
import org.camunda.bpm.engine.variable.impl.value.ObjectValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Stack;
import java.util.concurrent.locks.LockSupport;

/**
 * Serializer:
 * start(String)
 * schreibe(String)
 * write(String)
 * end()
 */

public class AnkerManager {

    private static final Logger LOG = LoggerFactory.getLogger(AnkerManager.class);
    private static AnkerManager instance = null;
    private final String filePrefix;
    private LogFilePersistence output;
    private ByteBufferObjectPool byteBufferObjectPool = new ByteBufferObjectPool(10);
    private ExpiringCache expiringCache = new ExpiringCache(60 * 60 * 1000);

    private AnkerManager(String filePrefix) {
        this.filePrefix = filePrefix;
        try {
            output = new LogFilePersistence(getFilename());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized void initialize(String filePrefix) {
        if (instance == null) {
            instance = new AnkerManager(filePrefix);
        } else {
            throw new RuntimeException("Already initialized");
        }
    }

    public static synchronized AnkerManager getInstance() {
        if (instance == null) {
            throw new RuntimeException("not initialized");
        }
        return instance;
    }

    protected String getFilename() {
        return filePrefix + System.currentTimeMillis() + "_anker.out";
    }

    protected void rollover() throws IOException {
        LogFilePersistence rolledOverPersistence = new LogFilePersistence(
                getFilename());
        synchronized (this) {
            // Swap instances, current instance can have pending accesses.
            output.setRolloverHint();
            output = rolledOverPersistence;
        }
    }

    public boolean ankerExists(String correlationId, DelegateExecution delegateExecution) {
        boolean returnValue = false;
        String prozessSchrittId = delegateExecution.getCurrentActivityId();
        //TODO: Move into persistenceTechnology, this has to be cluster-wide
        if (expiringCache.get(baueSchluessel(correlationId, prozessSchrittId)) != null) {
            returnValue = true;
        }
        return returnValue;
    }

    private String baueSchluessel(String correlationId, String prozessSchrittId) {
        return String.join("_", correlationId, prozessSchrittId);
    }

    public void schreibeAnker(String correlationId, DelegateExecution delegateExecution) throws IOException {
        long start = System.nanoTime();
        ByteBuffer byteBuffer = byteBufferObjectPool.borrowObject();
        LogFilePersistence currentPersistence = output;
        synchronized (AnkerManager.class) {
            // verhindern, dass ein Rollover/Schließen des FileChannels stattfindet während des register
            currentPersistence.register();
        }
        try {
            String schluessel = baueSchluessel(correlationId, delegateExecution.getCurrentActivityId());
            DelegateExecutionSerializer.serialisiereAnker(((ExecutionEntity) delegateExecution).getParent(), schluessel, byteBuffer, currentPersistence, new PersistenceFormat());
            expiringCache.put(schluessel, currentPersistence.getAnkerPackageName());
        } finally {
            synchronized (AnkerManager.class) {
                currentPersistence.unregister();
            }
            byteBuffer.clear();
            byteBufferObjectPool.returnObject(byteBuffer);
            LOG.error("Anker in {} ns geschrieben für {}", (System.nanoTime() - start), delegateExecution.getCurrentActivityId());
        }
    }

    public void leseAnkerInProzessVariablen(String correlationId, DelegateExecution delegateExecution)
            throws IOException {
        String schluessel = baueSchluessel(correlationId, delegateExecution.getCurrentActivityId());
        String dateiName = expiringCache.get(schluessel);
        if (dateiName == null) {
            throw new RuntimeException("Dateiname für Schlüssel nicht gefunden: " + schluessel);
        }
        if (output.getAnkerPackageName().equals(dateiName)) {
            rollover();
        }
        String ankerEintrag = null;
        // TODO: Datei locken, damit der Abräumer sie nicht gleichzeitig entfernt?
        try (BufferedReader br = new BufferedReader(new FileReader(dateiName))) {
            while (br.ready()) {
                String zeile = br.readLine();
                if (zeile.startsWith(schluessel)) {
                    ankerEintrag = zeile;
                    break;
                }
            }
        }
        if (ankerEintrag != null) {
            schreibeProzessVariablen(ankerEintrag, ((ExecutionEntity) delegateExecution).getParent());
        }
    }


    private void schreibeProzessVariablen(String ankerEintrag, ExecutionEntity executionEntity) {
        String variablen = ankerEintrag.substring(ankerEintrag.indexOf('{') + 1);
        variablen = variablen.substring(0, variablen.lastIndexOf('}'));
        int ende;
        while ((ende = variablen.indexOf(']')) >= 0) {
            String name = variablen.substring(0, variablen.indexOf('['));
            String typ = variablen.substring(variablen.indexOf('[') + 1, variablen.indexOf(':'));
            String wertLength = variablen.substring(variablen.indexOf(':'), variablen.lastIndexOf(':'));
            String wert = variablen.substring(variablen.lastIndexOf(':') + 1, ende);
            TypedValue typedValue;
            switch (typ) {
                case "NullValueImpl":
                    typedValue = NullValueImpl.INSTANCE;
                    break;
                case "string":
                    typedValue = new PrimitiveTypeValueImpl.StringValueImpl(wert);
                    break;
                case "long":
                    typedValue = new PrimitiveTypeValueImpl.LongValueImpl(Long.valueOf(wert));
                    break;
                default:
                    typedValue = new ObjectValueImpl(null, wert, "application/x-java-serialized-object", typ, false);
            }
            executionEntity.setVariableLocal(name, typedValue, executionEntity);
            variablen = variablen.substring(ende + 1);
        }
    }
}

class ByteBufferObjectPool extends ObjectPool<ByteBuffer> {
    protected ByteBufferObjectPool(int maxSize) {
        super(maxSize);
    }

    protected ByteBuffer createObject() {
        return ByteBuffer.allocateDirect(65536);
    }

}

abstract class ObjectPool<T> {
    private int maxSize;
    private int objectCount = 0;
    private Stack<T> pool = new Stack<>();

    public ObjectPool(int maxSize) {
        this.maxSize = maxSize;
    }

    public T borrowObject() {
        T element = null;
        for (int i = 0; i < 10; i++) {
            element = tryGet();
            if (element == null) {
                LockSupport.parkNanos(100000);
            } else {
                return element;
            }
        }
        throw new RuntimeException("could not borrow object from pool");
    }

    protected synchronized T tryGet() {
        if (pool.empty()) {
            if (objectCount < maxSize) {
                objectCount++;
                pool.push(createObject());
            } else {
                return null;
            }
        }
        return pool.pop();
    }

    public synchronized void returnObject(T toReturn) {
        pool.push(toReturn);
    }

    protected abstract T createObject();
}
