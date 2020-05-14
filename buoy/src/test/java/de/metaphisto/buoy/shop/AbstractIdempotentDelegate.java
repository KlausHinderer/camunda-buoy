package de.metaphisto.buoy.shop;

import de.metaphisto.buoy.Idempotence;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

/**
 *
 */
public abstract class AbstractIdempotentDelegate implements JavaDelegate {

    public static boolean idempotenceMode = true;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        if(idempotenceMode) {
            Idempotence idempotence = Idempotence.getInstance();
            String key = getKey(execution);
            if (idempotence.entryExists(key, execution)) {
                idempotence.readBuoyStateIntoProcessVariables(key, execution);
            } else {
                invokeService(execution);
                idempotence.putBuoy(key, execution);
            }
        }else {
            invokeService(execution);
        }
    }

    /**
     * This builds a unique correlationId for this delegate. For activities in loops, the correlationId must contain a mark for the iteration (e.g. the loop variable value).
     * @param execution
     * @return
     */
    protected abstract String getKey(DelegateExecution execution);

    protected abstract void invokeService(DelegateExecution execution);
}
