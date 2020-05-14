package de.metaphisto.buoy.shop;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.ExecutionListener;

/**
 *
 */
public class ZeroSumListener implements ExecutionListener {
    @Override
    public void notify(DelegateExecution execution) throws Exception {
        execution.setVariable("sum", 0.0D);
    }
}
