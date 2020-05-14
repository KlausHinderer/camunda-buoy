package de.metaphisto.buoy.shop;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;

/**
 *
 */
public class AddPositionToSumService extends AbstractIdempotentDelegate{

    @Override
    protected String getKey(DelegateExecution execution) {
        String orderid = (String) execution.getVariable("orderid");
        OrderPosition orderPosition = (OrderPosition) execution.getVariable("position");
        return orderid+"_"+orderPosition.getOffer()+"_"+orderPosition.getPrice();
    }

    @Override
    protected void invokeService(DelegateExecution execution) {
        OrderPosition orderPosition = (OrderPosition) execution.getVariable("position");
        Double sum  = (Double) execution.getVariable("sum");
        sum = sum + orderPosition.getPrice();
        execution.setVariable("sum", sum);
    }
}
