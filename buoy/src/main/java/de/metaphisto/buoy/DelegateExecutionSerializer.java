package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.AbstractPersistenceTechnology;
import de.metaphisto.buoy.persistence.PersistenceFormat;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.variable.impl.value.NullValueImpl;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.bpm.engine.variable.value.PrimitiveValue;
import org.camunda.bpm.engine.variable.value.TypedValue;

import java.io.IOException;
import java.nio.ByteBuffer;

import static de.metaphisto.buoy.persistence.AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL;

public class DelegateExecutionSerializer {

    private static final boolean WRITE_PARENT = false;

    /**
     * Schreibt die Variablen der DelegateExecution unter Verwendung des ByteBuffers in den FileChannel.
     *
     * @param execution  die zu schreibenden Variablen
     * @param key
     * @param byteBuffer der zu benutzende Puffer
     * @param ziel       der Channel, in den geschrieben wird
     * @throws IOException wenn etwas schiefgeht
     */
    public static void serialisiereAnker(DelegateExecution execution, String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology ziel, PersistenceFormat persistenceFormat) throws IOException {
        // Benutzt Locks, weil die Schreiboperationen hintereinander passieren m�ssen
        boolean locked = false;
        try {
            locked = ziel.beforeFirstWriteCommand(byteBuffer, key, locked);
            for (String variable : execution.getVariableNamesLocal()) {
                String variableType;
                String value;
                TypedValue typedValue = ((ExecutionEntity) execution).getVariableInstance(variable).getTypedValue(false);
                if (typedValue instanceof ObjectValue) {
                    variableType = ((ObjectValue) typedValue).getObjectTypeName();
                    value = ((ObjectValue) typedValue).getValueSerialized();
                } else if (typedValue instanceof PrimitiveValue) {
                    variableType = typedValue.getType().getName();
                    value = typedValue.getValue().toString();
                } else if (typedValue instanceof NullValueImpl) {
                    variableType = "NullValueImpl";
                    value = "null";
                } else {
                    variableType = "nicht erwarteter Typ: " + typedValue.getClass().getSimpleName();
                    value = typedValue.toString();
                }
                locked = persistenceFormat.writeVariable(variable, variableType, value, key, byteBuffer, ziel, locked);
            }
            locked = locked | ziel.appendNext("}", key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
            if (WRITE_PARENT) {
                //TODO: die aufrufenden Prozesse �ber this.parent.super.parent.parent.super.... holen
                ExecutionEntity parent = ((ExecutionEntity) execution).getParent();
                if (parent != null) {
                    ziel.appendNext("parent:", key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
                    serialisiereAnker(parent, key, byteBuffer, ziel, persistenceFormat);
                }
            }
            locked = ziel.afterLastWriteCommand(byteBuffer, key, locked);
        } finally {
            if (locked) {
                ziel.unlock();
            }
        }
    }
}
