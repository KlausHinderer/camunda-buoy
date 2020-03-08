package de.metaphisto;

import de.metaphisto.buoy.Idempotence;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.runtime.ProcessInstanceWithVariables;
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.StringResult2;

import java.util.HashMap;
import java.util.Map;

@JCStressTest
// Outline the outcomes here. The default outcome is provided, you need to remove it:
@Outcome(id = "with the work, with the work", expect = Expect.ACCEPTABLE, desc = "Default outcome.")
@Outcome(id = "lazy, with the work", expect = Expect.ACCEPTABLE, desc = "Default outcome.")
@Outcome(id = "with the work, lazy", expect = Expect.ACCEPTABLE, desc = "Default outcome.")
@Outcome(id = "lazy, lazy", expect = Expect.ACCEPTABLE, desc = "Default outcome.")
@State
public class CamundaTest {
    private static ProcessEngineConfiguration processEngineConfiguration = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
            .setJdbcUrl("jdbc:h2:mem:camunda" + System.currentTimeMillis());
    ;
    private static final ProcessEngine engine = processEngineConfiguration.buildProcessEngine();

    static {
        try {
            Idempotence.initialize("target/anker");
        } catch (RuntimeException ex) {
            //ignore already-initialized exceptions
        }
        engine.getRepositoryService().createDeployment().addClasspathResource("process.bpmn").deploy();
    }

    @Actor
    public void actor1(StringResult2 result) {
        Map<String, Object> variables = new HashMap<>();
        int id = (int) (Math.random() * 100000);
        variables.put("ID", id + "");
        ProcessInstanceWithVariables process = engine.getRuntimeService().createProcessInstanceByKey("process").setVariables(variables).executeWithVariablesInReturn();
        result.r1 = (String) process.getVariables().get("done");
    }

    @Actor
    public void actor2(StringResult2 result) {
        Map<String, Object> variables = new HashMap<>();
        int id = (int) (Math.random() * 100000);
        variables.put("ID", id + "");
        ProcessInstanceWithVariables process = engine.getRuntimeService().createProcessInstanceByKey("process").setVariables(variables).executeWithVariablesInReturn();
        result.r2 = (String) process.getVariables().get("done");
    }


}
