/*
 * Copyright (c) 2017, Red Hat Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.metaphisto;

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
import org.openjdk.jcstress.annotations.*;
import org.openjdk.jcstress.infra.results.StringResult4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// See jcstress-samples or existing tests for API introduction and testing guidelines

@JCStressTest
// Outline the outcomes here. The default outcome is provided, you need to remove it:
@Outcome(id = "OK, OK, nicht gefunden, OK", expect = Expect.ACCEPTABLE, desc = "Default outcome.")
@Outcome(id = "OK, OK, gefunden, OK", expect = Expect.ACCEPTABLE, desc = "Default outcome.")
@State
public class ConcurrencyTest {

    public static final String ACTIVITY = "Activity";
    private static Idempotence idempotence;
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

    static {
        try {
            Idempotence.initialize("target/anker");
        } catch (RuntimeException ex) {
            //ignore already-initialized exceptions
        }
        idempotence = Idempotence.getInstance();
    }

    private String id1, id2;

    /*    @Actor
        public void actor3(StringResult4 r) {
            ExecutionEntity delegateExecution = getDelegateExecution();
            int id = (int) (Math.random()*100000);
            try {
                AnkerManager.readBuoyStateIntoProcessVariables(id+"",null,null,delegateExecution);
            } catch (Exception e) {
                r.r3 = "nicht gefunden";
                return;
            }
            if(delegateExecution.getParent().getVariableInstancesLocal().isEmpty()) {
                r.r3 = "nicht gefunden";
            }else {
                r.r3 = "gefunden";
            }
        }
    */
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

    @Actor
    public void actor1(StringResult4 r) {
        r.r1 = "OK";
        id1 = String.valueOf(((int) (Math.random() * 50000)) * 2);
        ExecutionEntity delegateExecution = getDelegateExecution();
        String wert = "abcdefg" + System.currentTimeMillis();
        TypedValue typedValue = new PrimitiveTypeValueImpl.StringValueImpl(wert);
        delegateExecution.getParent().setVariableLocal("var", typedValue, delegateExecution.getParent());
        try {
            idempotence.putBuoy(id1, delegateExecution);
        } catch (IOException e) {
            r.r1 = e.getMessage();
        }
    }

    @Actor
    public void actor2(StringResult4 r) {
        r.r2 = "OK";
        id2 = String.valueOf((((int) (Math.random() * 50000)) * 2) + 1);
        ExecutionEntity delegateExecution = getDelegateExecution();
        String wert = "abcdefg" + System.currentTimeMillis();
        TypedValue typedValue = new PrimitiveTypeValueImpl.StringValueImpl(wert);
        delegateExecution.getParent().setVariableLocal("var", typedValue, delegateExecution.getParent());
        try {
            idempotence.putBuoy(id2, delegateExecution);
        } catch (IOException e) {
            r.r2 = e.getMessage();
        }
    }

    @Arbiter
    public void verify(StringResult4 r) {
        r.r4 = "OK";
        ExecutionEntity delegateExecution = getDelegateExecution();
        try {
            idempotence.readBuoyStateIntoProcessVariables(id1, delegateExecution);
            idempotence.readBuoyStateIntoProcessVariables(id2, delegateExecution);
        } catch (Exception e) {
            r.r4 = "NOK";
            return;
        }
        if (delegateExecution.getParent().getVariableInstancesLocal().isEmpty()) {
            r.r3 = "nicht gefunden";
            r.r4 = "NOK";
        } else {
            r.r3 = "gefunden";
        }
    }

    static class ProcessEngineConfig extends ProcessEngineConfigurationImpl {

        public ProcessEngineConfig() {
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
