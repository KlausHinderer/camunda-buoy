package de.metaphisto.buoy.delegate;

import de.metaphisto.buoy.AnkerManager;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class FirstDelegate implements JavaDelegate {
    private AnkerManager ankerManager = AnkerManager.getInstance();

    private static Map<String, String> nonXAResource = new HashMap<>();

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String correlationId = (String) execution.getVariable("ID");
        if(ankerManager.ankerExists(correlationId,execution)) {
            ankerManager.leseAnkerInProzessVariablen(correlationId, execution);
        }else {

            //begin custom block
            String put = nonXAResource.put(correlationId, correlationId);
            if(put != null) {
                throw new RuntimeException("Idempotence did not work");
            }
            execution.setVariable("written","true");
            //end custom block

            ankerManager.schreibeAnker(correlationId, execution);
        }
    }
}
