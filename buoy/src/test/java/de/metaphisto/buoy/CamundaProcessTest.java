package de.metaphisto.buoy;

import de.metaphisto.buoy.delegate.FirstDelegate;
import de.metaphisto.buoy.persistence.RedisPersistence;
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
public class CamundaProcessTest {


    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();

    private RedisServer redisServer;

    @Before
    public void setUp() throws IOException {
        redisServer = new RedisServer(6380);
        redisServer.start();
    }

    @After
    public void tearDown() {
        redisServer.stop();
    }

    @Test
    @Deployment(resources = {"test1.bpmn"})
    public void testWithCamunda() {
        FirstDelegate.setDeprecatedMode(false);
        RedisPersistence redisPersistence = new RedisPersistence();

        if (!Idempotence.isInitialized()) {
            Idempotence.initialize(redisPersistence);
        }
        for (int i = 0; i < 100; i++) {

            Map<String, Object> processVariables = new HashMap<>();
            processVariables.put("ID", "123"+i);
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
}
