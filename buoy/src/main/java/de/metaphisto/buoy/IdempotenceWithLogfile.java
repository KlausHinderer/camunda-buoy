package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.AbstractPersistenceTechnology;
import de.metaphisto.buoy.persistence.LogFilePersistence;
import de.metaphisto.buoy.persistence.PersistenceFormat;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.variable.impl.value.NullValueImpl;
import org.camunda.bpm.engine.variable.impl.value.ObjectValueImpl;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl;
import org.camunda.bpm.engine.variable.value.TypedValue;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Stack;
import java.util.concurrent.locks.LockSupport;

/**
 * Use Idempotence instead, or make this class use PersistenceFormat etc.
 */
@Deprecated
public class IdempotenceWithLogfile extends AbstractIdempotence {

    private static IdempotenceWithLogfile instance = null;
    private final String filePrefix;

    private IdempotenceWithLogfile(String filePrefix) {
        this.filePrefix = filePrefix;
        try {
            output = new LogFilePersistence(getFilename());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized void initialize(String filePrefix) {
        if (instance == null) {
            instance = new IdempotenceWithLogfile(filePrefix);
        } else {
            throw new RuntimeException("Already initialized");
        }
    }

    public static synchronized boolean isInitialized() {
        return instance != null;
    }

    public static synchronized IdempotenceWithLogfile getInstance() {
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
            ((LogFilePersistence) output).setRolloverHint();
            output = rolledOverPersistence;
        }
    }

    protected String constructIdempotenceKey(String correlationId, String processStepId) {
        return String.join("_", correlationId, processStepId);
    }

    @Override
    protected void putCacheEntry(String idempotenceKey, AbstractPersistenceTechnology currentPersistence) {
        expiringCache.put(idempotenceKey, ((LogFilePersistence) currentPersistence).getAnkerPackageName());
    }

    public void readBuoyStateIntoProcessVariables(String correlationId, DelegateExecution delegateExecution)
            throws IOException {
        String idempotenceKey = constructIdempotenceKey(correlationId, delegateExecution.getCurrentActivityId());
        String filename = expiringCache.get(idempotenceKey);
        if (filename == null) {
            throw new RuntimeException("File not found for idempotenceKey: " + idempotenceKey);
        }
        if (((LogFilePersistence) output).getAnkerPackageName().equals(filename)) {
            rollover();
        }
        String buoy = null;
        // TODO: Datei locken, damit der Abräumer sie nicht gleichzeitig entfernt?
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            while (br.ready()) {
                String line = br.readLine();
                if (line.startsWith(idempotenceKey)) {
                    buoy = line;
                    break;
                }
            }
        }
        if (buoy != null) {
            writeSavedStateToProcessVariables(buoy, ((ExecutionEntity) delegateExecution));
        }
    }


    private void writeSavedStateToProcessVariables(String buoy, ExecutionEntity executionEntity) {
        PersistenceFormat persistenceFormat = new PersistenceFormat();
        //TODO: use Buffer instead of substrings
        String variablen = buoy.substring(buoy.indexOf('{') + 1);
        variablen = variablen.substring(0, variablen.lastIndexOf('}'));
        ByteBuffer byteBuffer = ByteBuffer.wrap(variablen.getBytes());
        persistenceFormat.readChunk("key", byteBuffer, executionEntity);
        for (int i = 0; i + 2 < persistenceFormat.readValues.size(); i += 3) {
            String name = persistenceFormat.readValues.get(i);
            String type = persistenceFormat.readValues.get(i + 1);
            String wert = persistenceFormat.readValues.get(i + 2);
            TypedValue typedValue;
            switch (type) {
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
                    typedValue = new ObjectValueImpl(null, wert, "application/x-java-serialized-object", type, false);
            }
            executionEntity.setVariableLocal(name, typedValue, executionEntity);
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
        T element;
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
