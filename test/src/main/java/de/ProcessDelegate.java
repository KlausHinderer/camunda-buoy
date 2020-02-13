package de;

import de.metaphisto.buoy.AnkerManager;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class ProcessDelegate implements JavaDelegate {
    private static AnkerManager ankerManager = AnkerManager.getInstance();

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String id = (String) execution.getVariable("ID");
        if (ankerManager.ankerExists(id, execution)) {
            ankerManager.leseAnkerInProzessVariablen(id, execution);
            execution.setVariable("done", "lazy");
        } else {
            execution.setVariable("done", "with the work");
            ankerManager.schreibeAnker(id, execution);
        }
    }
}
