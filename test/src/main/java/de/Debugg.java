package de;

import de.metaphisto.CamundaTest;
import de.metaphisto.buoy.Idempotence;
import org.camunda.bpm.engine.impl.bpmn.helper.BpmnProperties;
import org.camunda.bpm.engine.impl.cfg.*;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.core.model.Properties;
import org.camunda.bpm.engine.impl.db.DbEntity;
import org.camunda.bpm.engine.impl.db.entitymanager.DbEntityManager;
import org.camunda.bpm.engine.impl.history.HistoryLevel;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceManager;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.openjdk.jcstress.infra.results.StringResult2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Debugg {
    private static TransactionContextFactory transactionContextFactory = new TransactionContextFactory() {
        @Override
        public TransactionContext openTransactionContext(CommandContext commandContext) {
            return new TransactionContext() {
                @Override
                public void commit() {

                }

                @Override
                public void rollback() {

                }

                @Override
                public void addTransactionListener(TransactionState transactionState, TransactionListener transactionListener) {

                }

                @Override
                public boolean isTransactionActive() {
                    return false;
                }
            };
        }
    };
    private static CommandContext commandContext = new CommandContext(new ProcessEngineConfig(), transactionContextFactory) {
        @Override
        public DbEntityManager getDbEntityManager() {
            return new DbEntityManager(null, null) {
                @Override
                public void insert(DbEntity dbEntity) {
                }
            };
        }
    };

    private static ExecutionEntity getDelegateExecution() {
        ExecutionEntity delegateExecution = new ExecutionEntity();
        delegateExecution.setActivityId("ACTIVITY");
        delegateExecution.setParentExecution(new ExecutionEntity());
        initEngine();
        delegateExecution.getParent().setProcessDefinition(new ProcessDefinitionEntity() {
            @Override
            public Properties getProperties() {
                Properties properties = new Properties();
                properties.set(BpmnProperties.HAS_CONDITIONAL_EVENTS, false);
                return properties;
            }
        });
        return delegateExecution;
    }

    private static void initEngine() {
        Context.setProcessEngineConfiguration(new ProcessEngineConfig());
        Context.getProcessEngineConfiguration().setHistoryLevel(HistoryLevel.HISTORY_LEVEL_NONE);
        Context.setCommandContext(commandContext);
        commandContext.getSessions().put(VariableInstanceManager.class, new VariableInstanceManager());
        commandContext.getSessions().put(DbEntityManager.class, new DbEntityManager(null, null) {
            public List selectList(String statement, Object parameter) {
                ArrayList arrayList = new ArrayList();
                arrayList.add(new VariableInstanceEntity("var1", new PrimitiveTypeValueImpl.StringValueImpl("aaaaaa"), false));
                return arrayList;
            }
        });
    }

    public static void main(String[] args) throws IOException {
        Idempotence.initialize("test/target/ankerDebugg");
        CamundaTest camundaTest = new CamundaTest();
        camundaTest.actor1(new StringResult2());

        Idempotence idempotence = Idempotence.getInstance();

        ExecutionEntity delegateExecution = getDelegateExecution();
        String wert = "abcdefg" + System.currentTimeMillis();
        TypedValue typedValue = new PrimitiveTypeValueImpl.StringValueImpl(wert);
        delegateExecution.getParent().setVariableLocal("var", typedValue, delegateExecution.getParent());
        idempotence.putBuoy(1 + "", delegateExecution);
        idempotence.readBuoyStateIntoProcessVariables(1 + "", getDelegateExecution());
    }

    static class ProcessEngineConfig extends ProcessEngineConfigurationImpl {

        ProcessEngineConfig() {
            initSerialization();
        }

        @Override
        protected Collection<? extends CommandInterceptor> getDefaultCommandInterceptorsTxRequired() {
            return null;
        }

        @Override
        protected Collection<? extends CommandInterceptor> getDefaultCommandInterceptorsTxRequiresNew() {
            return null;
        }

    }
}
