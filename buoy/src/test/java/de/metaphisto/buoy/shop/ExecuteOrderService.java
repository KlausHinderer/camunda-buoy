package de.metaphisto.buoy.shop;

import org.camunda.bpm.engine.delegate.DelegateExecution;

/**
 *
 */
public class ExecuteOrderService extends AbstractIdempotentDelegate {

    public static boolean throwException = false;

    @Override
    protected String getKey(DelegateExecution execution) {
        return (String) execution.getVariable("orderid");
    }

    @Override
    protected void invokeService(DelegateExecution execution) {
        if (throwException) {
            throw new RuntimeException();
        }
        execution.setVariable("status", "Order delivered");
    }
}
