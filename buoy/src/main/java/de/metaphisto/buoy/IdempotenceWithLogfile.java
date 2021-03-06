package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.LogFilePersistence;
import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Stack;
import java.util.concurrent.locks.LockSupport;

/**
 * Uses a Logfile to store Idempotence information.
 * This implementation works, however there are reasons to prefer Redis:
 * - LogFiles are node-local, if a process has a technical error it has to be restarted on the same node
 * - Current Implementation uses an im-memory cache that won't survive a server restart
 * <p>
 * If you want to use Logfile-based idempotence in production and you can accept that the idempotence is per-node,
 * you can prefill the in-memory cache ot server start by reading the files and putting each key with the filename in the expiringCache.
 *
 * Recommendation: Use log-based idempotence in your test-stages where redis isn't available and use redis-based idempotence in production.
 */
public class IdempotenceWithLogfile extends AbstractIdempotence {

    private static IdempotenceWithLogfile instance = null;
    private final String filePrefix;
    private ExpiringCache expiringCache = new ExpiringCache(360000);

    private IdempotenceWithLogfile(String filePrefix) {
        this.filePrefix = filePrefix;
        try {
            persistenceTechnology = new LogFilePersistence(getFilename(), expiringCache);
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

    void rollover() throws IOException {
        LogFilePersistence rolledOverPersistence = new LogFilePersistence(
                getFilename(), expiringCache);
        synchronized (this) {
            // Swap instances, current instance can have pending accesses.
            ((LogFilePersistence) persistenceTechnology).setRolloverHint();
            persistenceTechnology = rolledOverPersistence;
        }
    }

    @Override
    public void shutdown() {
        synchronized (IdempotenceWithLogfile.class) {
            persistenceTechnology.close();
            instance = null;
        }
    }

    protected String constructIdempotenceKey(String correlationId, String processStepId) {
        return String.join("_", correlationId, processStepId);
    }

}
