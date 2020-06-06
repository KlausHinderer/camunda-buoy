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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class ParallelismTest {

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

    private final AtomicInteger executionCount = new AtomicInteger(0);
    private final AtomicInteger buoysDetected = new AtomicInteger(0);

    @Test
    @Deployment(resources = {"test1.bpmn"})
    public void testWithCamunda() throws InterruptedException {
        FirstDelegate.setDeprecatedMode(false);
        RedisPersistence redisPersistence = new RedisPersistence();

        if (!Idempotence.isInitialized()) {
            Idempotence.initialize(redisPersistence);
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 100; i++) {
            final int j = i;
            executor.execute(() -> {
                Map<String, Object> processVariables = new HashMap<>();
                processVariables.put("ID", "123" + j);
                ProcessInstanceWithVariables processResult = processEngineRule.getRuntimeService().createProcessInstanceByKey("Process_buoy1").setVariables(processVariables).executeWithVariablesInReturn();

                // check if process instance ended
                assertNull(processEngineRule.getRuntimeService().createProcessInstanceQuery().singleResult());
                Object idempotence = processResult.getVariables().get("idempotence");
                if (idempotence != null) {
                    buoysDetected.incrementAndGet();
                } else {
                    executionCount.incrementAndGet();
                }
            });
        }
        for (int i = 0; i < 100; i++) {
            final int j = i;
            executor.execute(() -> {
                Map<String, Object> processVariables = new HashMap<>();
                processVariables.put("ID", "123" + j);
                ProcessInstanceWithVariables processResult = processEngineRule.getRuntimeService().createProcessInstanceByKey("Process_buoy1").setVariables(processVariables).executeWithVariablesInReturn();

                // check if process instance ended
                assertNull(processEngineRule.getRuntimeService().createProcessInstanceQuery().singleResult());
                Object idempotence = processResult.getVariables().get("idempotence");
                if (idempotence != null) {
                    buoysDetected.incrementAndGet();
                } else {
                    executionCount.incrementAndGet();
                }
            });
        }
        executor.shutdown();
        executor.awaitTermination(20, TimeUnit.SECONDS);
        assertEquals(executionCount.get(), buoysDetected.get());
        Idempotence.getInstance().shutdown();
    }

}
