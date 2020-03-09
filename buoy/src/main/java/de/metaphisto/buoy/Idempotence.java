package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.AbstractPersistenceTechnology;
import de.metaphisto.buoy.persistence.PersistenceFormat;
import de.metaphisto.buoy.persistence.ReadAction;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 */
public class Idempotence {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotenceWithLogfile.class);
    private static Idempotence instance = null;
    private AbstractPersistenceTechnology output;
    private ByteBufferObjectPool byteBufferObjectPool = new ByteBufferObjectPool(10);
    private ExpiringCache expiringCache = new ExpiringCache(60 * 60 * 1000);

    private Idempotence(AbstractPersistenceTechnology abstractPersistenceTechnology) {
        output = abstractPersistenceTechnology;
    }

    public static synchronized void initialize(AbstractPersistenceTechnology abstractPersistenceTechnology) {
        if (instance == null) {
            instance = new Idempotence(abstractPersistenceTechnology);
        } else {
            throw new RuntimeException("Already initialized");
        }
    }

    public static synchronized boolean isInitialized() {
        return instance != null;
    }

    public static synchronized Idempotence getInstance() {
        if (instance == null) {
            throw new RuntimeException("not initialized");
        }
        return instance;
    }

    public boolean entryExists(String correlationId, DelegateExecution delegateExecution) {
        boolean returnValue = false;
        String prozessSchrittId = delegateExecution.getCurrentActivityId();
        //TODO: Move into persistenceTechnology, this has to be cluster-wide
        if (expiringCache.get(constructIdempotenceKey(correlationId, prozessSchrittId)) != null) {
            returnValue = true;
        }
        return returnValue;
    }

    private String constructIdempotenceKey(String correlationId, String processStepId) {
        return String.join("_", correlationId, processStepId);
    }

    /**
     * Puts out a buoy to mark the course a processinstance has taken
     * @param correlationId the correlationId
     * @param delegateExecution the delegateExecution the current step
     * @throws IOException
     */
    public void putBuoy(String correlationId, DelegateExecution delegateExecution) throws IOException {
        long start = System.nanoTime();
        ByteBuffer byteBuffer = byteBufferObjectPool.borrowObject();
        AbstractPersistenceTechnology currentPersistence = output;
        synchronized (Idempotence.class) {
            // verhindern, dass ein Rollover/Schließen des FileChannels stattfindet während des register
            currentPersistence.register();
        }
        try {
            String idempotenceKey = constructIdempotenceKey(correlationId, delegateExecution.getCurrentActivityId());
            DelegateExecutionSerializer.writeBuoy(delegateExecution, idempotenceKey, byteBuffer, currentPersistence, new PersistenceFormat());
            expiringCache.put(idempotenceKey, "Buoy");
        } finally {
            synchronized (Idempotence.class) {
                currentPersistence.unregister();
            }
            byteBuffer.clear();
            byteBufferObjectPool.returnObject(byteBuffer);
            LOG.error("Buoy put in {} ns for {}", (System.nanoTime() - start), delegateExecution.getCurrentActivityId());
        }
    }

    public void readBuoyStateIntoProcessVariables(String correlationId, DelegateExecution delegateExecution)
            throws IOException {
        String idempotenceKey = constructIdempotenceKey(correlationId, delegateExecution.getCurrentActivityId());
        ByteBuffer byteBuffer = byteBufferObjectPool.borrowObject();
        ReadAction readAction = output.prepareForRead(idempotenceKey, false, byteBuffer);
        PersistenceFormat persistenceFormat = new PersistenceFormat();
        do {
            persistenceFormat.readChunk(idempotenceKey,byteBuffer,delegateExecution);
        } while (output.readNext(readAction, byteBuffer) >= 0);

        if(readAction.isLocked()){
            output.unlock();
        }
        byteBufferObjectPool.returnObject(byteBuffer);
    }
}
