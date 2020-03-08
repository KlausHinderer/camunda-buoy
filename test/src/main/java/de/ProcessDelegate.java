package de;

import de.metaphisto.buoy.Idempotence;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public class ProcessDelegate implements JavaDelegate {
    private static Idempotence idempotence = Idempotence.getInstance();

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String id = (String) execution.getVariable("ID");
        if (idempotence.entryExists(id, execution)) {
            idempotence.readBuoyStateIntoProcessVariables(id, execution);
            execution.setVariable("done", "lazy");
        } else {
            execution.setVariable("done", "with the work");
            idempotence.putBuoy(id, execution);
        }
    }
}
