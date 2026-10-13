package io.yak.ops.business.task.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DispatchTicketTest {

    @Test
    void retainsDispatchIdentityAndValueEquality() {
        DispatchTicket ticket = ticket();
        assertEquals("workspace-one", ticket.workspaceId());
        assertEquals("instance-one", ticket.instanceId());
        assertEquals("definition-one", ticket.definitionId());
        assertEquals(2, ticket.definitionVersion());
        assertEquals("DATA_SYNC", ticket.taskType());
        assertEquals("revision-one", ticket.resourceRevision());
        assertEquals("opaque-dispatch-token", ticket.dispatchToken());
        assertEquals(false, ticket.debug());
        assertEquals(ticket, ticket());
        assertEquals(ticket.hashCode(), ticket().hashCode());
    }

    @Test
    void diagnosticStringRedactsOneTimeDispatchToken() {
        String diagnostic = ticket().toString();
        assertTrue(diagnostic.contains("instance-one"));
        assertTrue(diagnostic.contains("dispatchToken=redacted"));
        assertFalse(diagnostic.contains("opaque-dispatch-token"));
    }

    private static DispatchTicket ticket() {
        return new DispatchTicket(
                "workspace-one",
                "instance-one",
                "definition-one",
                2,
                "DATA_SYNC",
                "revision-one",
                "opaque-dispatch-token",
                false);
    }
}
