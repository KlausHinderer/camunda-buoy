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
public abstract class AbstractIdempotence {
    private final Logger LOG = LoggerFactory.getLogger(getClass());
    private ByteBufferObjectPool byteBufferObjectPool = new ByteBufferObjectPool(10, 32768);
    protected AbstractPersistenceTechnology output;
    protected ExpiringCache expiringCache = new ExpiringCache(60 * 60 * 1000);

    public boolean entryExists(String correlationId, DelegateExecution delegateExecution) {
        boolean returnValue = false;
        String prozessSchrittId = delegateExecution.getCurrentActivityId();
        //TODO: Move into persistenceTechnology, this has to be cluster-wide
        if (expiringCache.get(constructIdempotenceKey(correlationId, prozessSchrittId)) != null) {
            returnValue = true;
        }
        return returnValue;
    }

    protected abstract String constructIdempotenceKey(String correlationId, String processStepId);

    /**
     * Puts out a buoy to mark the course a processInstance has taken
     *
     * @param correlationId     the correlationId
     * @param delegateExecution the delegateExecution the current step
     * @throws IOException
     */
    public void putBuoy(String correlationId, DelegateExecution delegateExecution) throws IOException {
        long start = System.nanoTime();
        ByteBuffer byteBuffer = byteBufferObjectPool.borrowObject();
        AbstractPersistenceTechnology currentPersistence = output;
        synchronized (this) {
            currentPersistence.register();
        }
        try {
            String idempotenceKey = constructIdempotenceKey(correlationId, delegateExecution.getCurrentActivityId());
            DelegateExecutionSerializer.writeBuoy(delegateExecution, idempotenceKey, byteBuffer, currentPersistence, new PersistenceFormat());
            putCacheEntry(idempotenceKey, currentPersistence);
        } finally {
            synchronized (this) {
                currentPersistence.unregister();
            }
            byteBuffer.clear();
            byteBufferObjectPool.returnObject(byteBuffer);
            LOG.info("Buoy put in {} ns for {}", (System.nanoTime() - start), delegateExecution.getCurrentActivityId());
        }
    }

    protected abstract void putCacheEntry(String idempotenceKey, AbstractPersistenceTechnology currentPersistence);

    public void readBuoyStateIntoProcessVariables(String correlationId, DelegateExecution delegateExecution)
            throws IOException {
        String idempotenceKey = constructIdempotenceKey(correlationId, delegateExecution.getCurrentActivityId());
        ByteBuffer byteBuffer = byteBufferObjectPool.borrowObject();
        ReadAction readAction = output.prepareForRead(idempotenceKey, false, byteBuffer);
        PersistenceFormat persistenceFormat = new PersistenceFormat();
        do {
            persistenceFormat.readChunk(idempotenceKey, byteBuffer, delegateExecution);
        } while (output.readNext(readAction, byteBuffer) >= 0);

        if (readAction.isLocked()) {
            output.unlock();
        }
        byteBuffer.clear();
        byteBufferObjectPool.returnObject(byteBuffer);
    }
}
