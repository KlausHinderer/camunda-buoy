package de.metaphisto.buoy.shop;

import de.metaphisto.buoy.Idempotence;
import de.metaphisto.buoy.persistence.AbstractPersistenceTechnology;
import de.metaphisto.buoy.persistence.RedisPersistence;
import org.camunda.bpm.engine.runtime.ProcessInstanceWithVariables;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.*;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ShopTest {
    public static final String CHEESE = "cheese";
    public static final String BREAD = "bread";
    public static final String BEER = "beer";
    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();

    private static RedisServer redisServer;

    @BeforeClass
    public static void setUp() throws IOException {
        redisServer = new RedisServer(6381);
        redisServer.start();
    }

    @AfterClass
    public static void tearDown() {
        redisServer.stop();
    }

    public void placeOrder() {
        AbstractPersistenceTechnology redisPersistence = new RedisPersistence(null, 6381);

        if (!Idempotence.isInitialized()) {
            Idempotence.initialize(redisPersistence);
        }

        //Add limited stock so a reorder will lead to errors
        CheckAvailabilityService.addStock(CHEESE, 1);
        CheckAvailabilityService.addStock(BREAD, 1);
        CheckAvailabilityService.addStock(BEER, 2);

        Map<String, Object> processvariables = new HashMap<>();
        processvariables.put("orderid", "1234567");
        List<OrderPosition> orderPositions = new ArrayList<>();
        orderPositions.add(new OrderPosition(CHEESE, 3.67));
        orderPositions.add(new OrderPosition(BREAD, 1.33));
        orderPositions.add(new OrderPosition(BEER, 4.00));
        processvariables.put("order", orderPositions);

        ExecuteOrderService.throwException = true;
        try {
            ProcessInstanceWithVariables firstOrder = processEngineRule.getRuntimeService().createProcessInstanceByKey("Process_toplevel").setVariables(processvariables).executeWithVariablesInReturn();
        }catch (RuntimeException expected) {}
        System.out.println("restarting process");

        //start again with the same orderid
        ExecuteOrderService.throwException = false;
        ProcessInstanceWithVariables reorder = processEngineRule.getRuntimeService().createProcessInstanceByKey("Process_toplevel").setVariables(processvariables).executeWithVariablesInReturn();
        assertTrue((Boolean) reorder.getVariables().get("available"));
        assertEquals("Order delivered", reorder.getVariables().get("status"));
        assertEquals(" a 1 ", reorder.getVariables().get("StringWithWhitespace"));
    }

    @Test
    @Deployment(resources = {"toplevel.bpmn", "calculateprice.bpmn"})
    public void testPlaceOrderWithIdempotence(){
        AbstractIdempotentDelegate.idempotenceMode = true;
        placeOrder();
    }


    @Test(expected = AssertionError.class)
    @Deployment(resources = {"toplevel.bpmn", "calculateprice.bpmn"})
    public void testPlaceOrderWithoutIdempotence(){
        AbstractIdempotentDelegate.idempotenceMode = false;
        placeOrder();
    }
}
