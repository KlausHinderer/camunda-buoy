package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.AbstractPersistenceTechnology;
import de.metaphisto.buoy.persistence.PersistenceFormat;
import de.metaphisto.buoy.persistence.ReadAction;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Stack;
import java.util.concurrent.locks.LockSupport;

/**
 *
 */
public abstract class AbstractIdempotence {
    private final Logger LOG = LoggerFactory.getLogger(getClass());
    private ByteBufferObjectPool byteBufferObjectPool = new ByteBufferObjectPool(100, 8192);
    protected AbstractPersistenceTechnology persistenceTechnology;

    public boolean entryExists(String correlationId, DelegateExecution delegateExecution) {
        String processStepId = delegateExecution.getCurrentActivityId();
        String key = constructIdempotenceKey(correlationId, processStepId);
        ByteBuffer byteBuffer = byteBufferObjectPool.borrowObject();
        try {
            return persistenceTechnology.entryExists(key, byteBuffer);
        } finally {
            byteBuffer.clear();
            byteBufferObjectPool.returnObject(byteBuffer);
        }
    }

    public abstract void shutdown();

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
        AbstractPersistenceTechnology currentPersistence = persistenceTechnology;
        synchronized (this) {
            currentPersistence.register();
        }
        try {
            String idempotenceKey = constructIdempotenceKey(correlationId, delegateExecution.getCurrentActivityId());
            DelegateExecutionSerializer.writeBuoy(delegateExecution, idempotenceKey, byteBuffer, currentPersistence, new PersistenceFormat(), true);
            currentPersistence.putCacheEntry(idempotenceKey);
        } finally {
            synchronized (this) {
                currentPersistence.unregister();
            }
            byteBuffer.clear();
            byteBufferObjectPool.returnObject(byteBuffer);
            LOG.info("Buoy put in {} ns for {}", (System.nanoTime() - start), delegateExecution.getCurrentActivityId());
        }
    }

    public void readBuoyStateIntoProcessVariables(String correlationId, DelegateExecution delegateExecution)
            throws IOException {
        String idempotenceKey = constructIdempotenceKey(correlationId, delegateExecution.getCurrentActivityId());
        ByteBuffer byteBuffer = byteBufferObjectPool.borrowObject();
        ReadAction readAction = persistenceTechnology.prepareForRead(idempotenceKey, false, byteBuffer);
        PersistenceFormat persistenceFormat = new PersistenceFormat();
        do {
            persistenceFormat.readChunk(idempotenceKey, byteBuffer, delegateExecution);
        } while (persistenceTechnology.readNext(readAction, byteBuffer) >= 0);

        if (readAction.isLocked()) {
            persistenceTechnology.unlock();
        }
        byteBuffer.clear();
        byteBufferObjectPool.returnObject(byteBuffer);
    }
}

class ByteBufferObjectPool extends ObjectPool<ByteBuffer> {
    private int bufferSize;

    protected ByteBufferObjectPool(int maxSize, int bufferSize) {
        super(maxSize);
        if (bufferSize < 512) {
            throw new RuntimeException("Buffersize '" + bufferSize + "' is too small, use at least 512");
        }
        this.bufferSize = bufferSize;
    }

    protected ByteBuffer createObject() {
        return ByteBuffer.allocateDirect(bufferSize);
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
