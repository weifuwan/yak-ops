package io.yak.ops.boot.log;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

/** Verifies sensitive message and exception stack masking before file output. */
class SensitiveDataConverterTest {

    @Test
    void masksConnectionAndStructuredSecrets() {
        String message = "jdbc:mysql://user:swordfish@db/orders?password=private&token=xyz "
                + "{\"password\":\"json-secret\"}";
        String hidden = SensitiveDataConverter.mask(message);

        assertFalse(hidden.contains("swordfish"));
        assertFalse(hidden.contains("private"));
        assertFalse(hidden.contains("xyz"));
        assertFalse(hidden.contains("json-secret"));
        assertTrue(hidden.contains("******"));
    }

    @Test
    void masksThrowableMessagesAsPartOfTheSameFormattedEvent() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("Task failed, password=message-secret");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("token=stack-secret")));
        String output = new SensitiveDataConverter().convert(event);

        assertFalse(output.contains("message-secret"));
        assertFalse(output.contains("stack-secret"));
        assertTrue(output.contains("IllegalStateException"));
    }
}
