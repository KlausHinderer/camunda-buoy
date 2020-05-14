package de.metaphisto.buoy.shop;

import org.camunda.bpm.engine.delegate.DelegateExecution;

/**
 *
 */
public class PersistOrderService extends AbstractIdempotentDelegate {
    @Override
    protected String getKey(DelegateExecution execution) {
        return (String) execution.getVariable("orderid");
    }

    @Override
    protected void invokeService(DelegateExecution execution) {
        System.out.println("Persisting order with id "+execution.getVariable("orderid"));
        execution.setVariable("StringWithWhitespace", " a 1 ");
    }
}
