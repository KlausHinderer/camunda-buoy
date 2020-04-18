package de.metaphisto.buoy;

import de.metaphisto.buoy.delegate.FirstDelegate;
import org.camunda.bpm.engine.runtime.ProcessInstanceWithVariables;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class CamundaProcessLogfileTest {


    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();

    @Test
    @Deployment(resources = {"test1.bpmn"})
    public void testWithCamunda() {
        FirstDelegate.setDeprecatedMode(true);
        if(! IdempotenceWithLogfile.isInitialized()) {
            IdempotenceWithLogfile.initialize("target/testWithCamunda");
        }


        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("ID","123"+System.nanoTime());
        processEngineRule.getRuntimeService().createProcessInstanceByKey("Process_buoy1").setVariables(processVariables).executeWithVariablesInReturn();


        // check if process instance ended
        assertNull(processEngineRule.getRuntimeService().createProcessInstanceQuery().singleResult());

        //Restart Process to test idempotence
        ProcessInstanceWithVariables restarted = processEngineRule.getRuntimeService().createProcessInstanceByKey("Process_buoy1").setVariables(processVariables).executeWithVariablesInReturn();

        // check if process instance ended
        assertNull(processEngineRule.getRuntimeService().createProcessInstanceQuery().singleResult());

        Object written = restarted.getVariables().get("written");
        assertNotNull(written);
    }
}
