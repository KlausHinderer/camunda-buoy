package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.AbstractPersistenceTechnology;
import de.metaphisto.buoy.persistence.LogFilePersistence;
import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Stack;
import java.util.concurrent.locks.LockSupport;

public class IdempotenceWithLogfile extends AbstractIdempotence {

    private static IdempotenceWithLogfile instance = null;
    private final String filePrefix;

    private IdempotenceWithLogfile(String filePrefix) {
        this.filePrefix = filePrefix;
        try {
            output = new LogFilePersistence(getFilename(), expiringCache);
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
        return filePrefix + System.currentTimeMillis() + System.nanoTime() + "_anker.out";
    }

    @Override
    public void readBuoyStateIntoProcessVariables(String correlationId, DelegateExecution delegateExecution) throws IOException {
        rollover();
        super.readBuoyStateIntoProcessVariables(correlationId, delegateExecution);
    }

    protected void rollover() throws IOException {
        LogFilePersistence rolledOverPersistence = new LogFilePersistence(
                getFilename(), expiringCache);
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

}

//TODO: move this to AbstractIdempotence or a top-level file
class ByteBufferObjectPool extends ObjectPool<ByteBuffer> {
    private int bufferSize;

    protected ByteBufferObjectPool(int maxSize, int bufferSize) {
        super(maxSize);
        if(bufferSize<512) {
            throw new RuntimeException("Buffersize '"+bufferSize+"' is too small, use at least 512");
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
