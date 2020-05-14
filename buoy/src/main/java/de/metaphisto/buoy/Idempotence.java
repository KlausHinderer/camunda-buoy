package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.AbstractPersistenceTechnology;

/**
 * This is the class a ServiceTask can use to implement its idempotence.
 */
public class Idempotence extends AbstractIdempotence {

    private static Idempotence instance = null;

    private Idempotence(AbstractPersistenceTechnology abstractPersistenceTechnology) {
        persistenceTechnology = abstractPersistenceTechnology;
    }

    public static synchronized void initialize(AbstractPersistenceTechnology abstractPersistenceTechnology) {
        if(instance != null) {
            instance.persistenceTechnology.unregister();
            instance.persistenceTechnology.close();
        }
        instance = new Idempotence(abstractPersistenceTechnology);
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
}
