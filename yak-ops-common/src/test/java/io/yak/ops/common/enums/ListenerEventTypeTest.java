package io.yak.ops.common.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ListenerEventTypeTest {

    @Test
    public void testGetCode() {
        Assertions.assertEquals(0, ListenerEventType.SERVER_DOWN.getCode());
        Assertions.assertEquals(1, ListenerEventType.PROCESS_DEFINITION_CREATED.getCode());
    }

    @Test
    public void testGetDesp() {
        Assertions.assertEquals("PROCESS_DEFINITION_UPDATED", ListenerEventType.PROCESS_DEFINITION_UPDATED.getDescp());
        Assertions.assertEquals("PROCESS_DEFINITION_DELETED", ListenerEventType.PROCESS_DEFINITION_DELETED.getDescp());
    }

    @Test
    public void testGetListenerEventTypeByCode() {
        Assertions.assertEquals(ListenerEventType.PROCESS_START, ListenerEventType.of(4));
        Assertions.assertNotEquals(ListenerEventType.PROCESS_END, ListenerEventType.of(6));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ListenerEventType.of(-1));
    }
}
