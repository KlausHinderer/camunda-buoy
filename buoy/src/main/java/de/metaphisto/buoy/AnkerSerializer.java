package de.metaphisto.buoy;

import de.metaphisto.buoy.persistence.AbstractPersistenceTechnology;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.variable.impl.value.NullValueImpl;
import org.camunda.bpm.engine.variable.value.ObjectValue;
import org.camunda.bpm.engine.variable.value.PrimitiveValue;
import org.camunda.bpm.engine.variable.value.TypedValue;

import java.io.IOException;
import java.nio.ByteBuffer;

import static de.metaphisto.buoy.persistence.AbstractStoreHolder.WriteMode.ONLY_FLUSH_IF_BUFFER_FULL;

public class AnkerSerializer {

    private static final boolean WRITE_PARENT = false;

    /**
     * Schreibt die Variablen der DelegateExecution unter Verwendung des ByteBuffers in den FileChannel.
     *
     * @param execution  die zu schreibenden Variablen
     * @param schluessel
     * @param byteBuffer der zu benutzende Puffer
     * @param ziel       der Channel, in den geschrieben wird
     * @throws IOException wenn etwas schiefgeht
     */
    public static void serialisiereAnker(DelegateExecution execution, String schluessel, ByteBuffer byteBuffer, AbstractPersistenceTechnology ziel) throws IOException {
        // Benutzt Locks, weil die Schreiboperationen hintereinander passieren m�ssen
        boolean locked = false;
        try {
            locked = ziel.beforeFirstWriteCommand(byteBuffer, schluessel, locked);
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
                locked = ziel.appendNext(variable + "[" + variableType + ":" + value.length() + ":", schluessel, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
                locked = ziel.appendNext(value, schluessel, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
                locked = ziel.appendNext("]", schluessel, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
            }
            locked = locked | ziel.appendNext("}", schluessel, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
            if (WRITE_PARENT) {
                //TODO: die aufrufenden Prozesse �ber this.parent.super.parent.parent.super.... holen
                ExecutionEntity parent = ((ExecutionEntity) execution).getParent();
                if (parent != null) {
                    ziel.appendNext("parent:", schluessel, byteBuffer, locked, ONLY_FLUSH_IF_BUFFER_FULL);
                    serialisiereAnker(parent, schluessel, byteBuffer, ziel);
                }
            }
            locked = ziel.afterLastWriteCommand(byteBuffer, schluessel, locked);
        } finally {
            if (locked) {
                ziel.unlock();
            }
        }
    }
}
