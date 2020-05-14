package de.metaphisto.buoy.shop;

import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class CheckAvailabilityService extends AbstractIdempotentDelegate {

    private static Map<String, Integer> stock = new HashMap<>();

    public static void addStock(String offer, int quantity) {
        stock.put(offer,quantity);
    }

    @Override
    protected String getKey(DelegateExecution execution) {
        String orderid = (String) execution.getVariable("orderid");
        OrderPosition position = (OrderPosition) execution.getVariable("position");
        return orderid+"_"+position.getOffer()+"_"+position.getPrice();
    }

    @Override
    protected void invokeService(DelegateExecution execution) {
        OrderPosition position = (OrderPosition) execution.getVariable("position");
        Integer inStock = stock.get(position.getOffer());
        if(inStock>0) {
            inStock--;
            stock.put(position.getOffer(), inStock);
            if(! execution.hasVariable("available")) {
                execution.setVariable("available", true);
            }
        } else {
            execution.setVariable("available", false);
        }
    }
}
