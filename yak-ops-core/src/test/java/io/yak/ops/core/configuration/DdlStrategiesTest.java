package io.yak.ops.core.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.configuration.DdlStrategies.Action;
import io.yak.ops.core.configuration.DdlStrategies.Event;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

/** Verifies the persisted v9 matrix remains complete and rejects unsupported destructive policies. */
class DdlStrategiesTest {
    @Test
    void requiresAllEightEvents() {
        assertThrows(IllegalArgumentException.class, () -> new DdlStrategies(new EnumMap<>(Event.class)));
        assertThrows(IllegalArgumentException.class, () -> DdlStrategies.parse("add-column=NORMAL"));
    }

    @Test
    void encodesAndDecodesWithoutLosingEventIdentity() {
        EnumMap<Event, Action> choices = new EnumMap<>(Event.class);
        for (Event event : Event.values()) choices.put(event, Action.ERROR);
        choices.put(Event.ADD_COLUMN, Action.NORMAL);
        choices.put(Event.DROP_COLUMN, Action.IGNORE);
        choices.put(Event.MODIFY_COLUMN_TYPE, Action.NORMAL);
        DdlStrategies original = new DdlStrategies(choices);
        DdlStrategies restored = DdlStrategies.parse(original.encode());

        assertEquals(8, restored.toJsonValues().size());
        assertEquals(original.toJsonValues(), restored.toJsonValues());
        assertEquals(Action.IGNORE, restored.action(Event.DROP_COLUMN));
        assertTrue(restored.encode().contains("modify-column-type=NORMAL"));
    }

    @Test
    void neverAdmitsUnimplementedRenameOrTruncateActions() {
        EnumMap<Event, Action> choices = new EnumMap<>(Event.class);
        for (Event event : Event.values()) choices.put(event, Action.ERROR);
        for (Event event : new Event[] {
            Event.DROP_TABLE, Event.RENAME_TABLE, Event.RENAME_COLUMN, Event.TRUNCATE_TABLE
        }) {
            choices.put(event, Action.NORMAL);
            assertThrows(UnsupportedOperationException.class, () -> new DdlStrategies(choices));
            choices.put(event, Action.IGNORE);
            assertThrows(UnsupportedOperationException.class, () -> new DdlStrategies(choices));
            choices.put(event, Action.ERROR);
        }
    }

    @Test
    void rejectsDuplicateAndInvalidEncodedKeys() {
        EnumMap<Event, Action> choices = new EnumMap<>(Event.class);
        for (Event event : Event.values()) choices.put(event, Action.ERROR);
        String encoded = new DdlStrategies(choices).encode();
        assertThrows(IllegalArgumentException.class, () -> DdlStrategies.parse(encoded + ",add-column=ERROR"));
        assertThrows(IllegalArgumentException.class, () -> DdlStrategies.parse(encoded.replace("add-column=ERROR", "unknown=ERROR")));
        assertThrows(IllegalArgumentException.class, () -> DdlStrategies.parse(encoded.replace("add-column=ERROR", "add-column=NOPE")));
    }
}
