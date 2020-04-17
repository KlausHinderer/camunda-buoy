package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.AbstractPersistenceTechnology;
import de.metaphisto.buoy.persistence.PersistenceFormat;
import de.metaphisto.buoy.persistence.ReadAction;
import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 */
public class Idempotence extends AbstractIdempotence {

    private static Idempotence instance = null;
    private ByteBufferObjectPool byteBufferObjectPool = new ByteBufferObjectPool(10);

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

    @Override
    protected String constructIdempotenceKey(String correlationId, String processStepId) {
        return String.join("_", correlationId, processStepId);
    }

    @Override
    protected void putCacheEntry(String idempotenceKey, AbstractPersistenceTechnology currentPersistence) {
        expiringCache.put(idempotenceKey, "Buoy");
    }

    @Override
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
        byteBufferObjectPool.returnObject(byteBuffer);
    }
}
