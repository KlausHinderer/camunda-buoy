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
import java.util.Base64;

import static de.metaphisto.buoy.persistence.AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL;

public class DelegateExecutionSerializer {

    private static final boolean WRITE_PARENT = true;

    /**
     * Schreibt die Variablen der DelegateExecution unter Verwendung des ByteBuffers in den FileChannel.
     *
     * @param execution  die zu schreibenden Variablen
     * @param key
     * @param byteBuffer der zu benutzende Puffer
     * @param target     der Channel, in den geschrieben wird
     * @throws IOException wenn etwas schiefgeht
     */
    public static void writeBuoy(DelegateExecution execution, String key, ByteBuffer byteBuffer, AbstractPersistenceTechnology target, PersistenceFormat persistenceFormat, boolean istopLevel) throws IOException {
        // Benutzt Locks, weil die Schreiboperationen hintereinander passieren müssen
        boolean locked = false;
        try {
            if (istopLevel) {
                locked = target.beforeFirstWriteCommand(byteBuffer, key, locked);
            }
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
                    if (value.contains(" ")) {
                        //Redis can't handle blanks in Append-Values in Telnet-Protocol
                        value = Base64.getEncoder().encodeToString(value.getBytes());
                        variableType += PersistenceFormat.ENCODED;
                    }
                } else if (typedValue instanceof NullValueImpl) {
                    variableType = "NullValueImpl";
                    value = "null";
                } else {
                    variableType = "unexpected type: " + typedValue.getClass().getName();
                    value = typedValue.toString();
                }
                locked = persistenceFormat.writeVariable(variable, variableType, value, key, byteBuffer, target, locked);
            }
            if (WRITE_PARENT) {
                ExecutionEntity parent = ((ExecutionEntity) execution).getParent();
                if (parent != null) {
                    persistenceFormat.startScope(key, byteBuffer, target, locked);
                    writeBuoy(parent, key, byteBuffer, target, persistenceFormat, false);
                }
            }
            locked = locked | target.appendNext("}", key, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
            if (istopLevel) {
                locked = target.afterLastWriteCommand(byteBuffer, key, locked);
            }
        } finally {
            if (locked) {
                target.unlock();
            }
        }
    }
}
