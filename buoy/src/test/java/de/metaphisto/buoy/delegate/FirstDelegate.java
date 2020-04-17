package de.metaphisto.buoy.delegate;

import de.metaphisto.buoy.AbstractIdempotence;
import de.metaphisto.buoy.Idempotence;
import de.metaphisto.buoy.IdempotenceWithLogfile;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class FirstDelegate implements JavaDelegate {
    private static boolean deprecatedMode;

    public static void setDeprecatedMode(boolean deprecated) {
        deprecatedMode = deprecated;
        nonXAResource.clear();
    }

    private static Map<String, String> nonXAResource = new HashMap<>();

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        AbstractIdempotence idempotence;
        if (deprecatedMode) {
            idempotence = IdempotenceWithLogfile.getInstance();
        } else {
            idempotence = Idempotence.getInstance();
        }

        String correlationId = (String) execution.getVariable("ID");
        if (idempotence.entryExists(correlationId, execution)) {
            idempotence.readBuoyStateIntoProcessVariables(correlationId, execution);
        } else {

            //begin custom block
            String put = nonXAResource.put(correlationId, correlationId);
            if (put != null) {
                throw new RuntimeException("Idempotence did not work");
            }
            execution.setVariable("written", "true");
            //end custom block

            long start = System.nanoTime();
            idempotence.putBuoy(correlationId, execution);
            System.out.println("Putting buoy took ns:" + (System.nanoTime() - start));
        }
    }
}
