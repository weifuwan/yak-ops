package io.yak.ops.connector.cdc.mysql.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.core.api.connector.source.Boundedness;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class MySqlDataSourceFactoryTest {
    private final MySqlDataSourceFactory factory = new MySqlDataSourceFactory();

    @Test
    void boundednessFollowsStartupModeInsteadOfTheConnectorIdentifier() {
        assertEquals(Boundedness.BOUNDED, factory.boundedness(options("snapshot")));
        org.junit.jupiter.api.Assertions.assertFalse(factory.producesChangelog(options("snapshot")));
        for (String mode : new String[] {"initial", "latest-offset", "earliest-offset"}) {
            assertEquals(Boundedness.CONTINUOUS_UNBOUNDED, factory.boundedness(options(mode)));
            assertTrue(factory.producesChangelog(options(mode)));
        }
        assertThrows(IllegalArgumentException.class, () -> factory.boundedness(options("invalid")));
    }

    @Test
    void keepsReplicationIdentityStableAndPreservesThePublishedAllocationAlgorithm() {
        ReadonlyConfig first = factory.prepareExecutionOptions(options("initial"), "instance-123");
        ReadonlyConfig next = factory.prepareExecutionOptions(options("initial"), "instance-123");
        assertEquals(first, next);
        int expected = Math.floorMod("instance-123".hashCode(), 62_500_000) * 32 + 1;
        assertEquals(Integer.toString(expected), first.get(MySqlSourceOptions.SERVER_ID));
        assertEquals("yakflow_instance-123", first.get(MySqlSourceOptions.TOPIC_PREFIX));
        assertEquals(Map.of("MYSQL_CDC_SERVER_ID", Integer.toString(expected)), factory.exclusiveResources("instance-123"));
        assertEquals("initial", first.get(MySqlSourceOptions.SCAN_STARTUP_MODE));
    }

    @Test
    void exposesHashCollisionsAsTheSameExclusiveAdmissionClaim() {
        ReadonlyConfig first = factory.prepareExecutionOptions(options("initial"), "Aa");
        ReadonlyConfig second = factory.prepareExecutionOptions(options("initial"), "BB");
        assertEquals(first.get(MySqlSourceOptions.SERVER_ID), second.get(MySqlSourceOptions.SERVER_ID));
        assertEquals(factory.exclusiveResources("Aa"), factory.exclusiveResources("BB"));
        assertNotEquals(first.get(MySqlSourceOptions.TOPIC_PREFIX), second.get(MySqlSourceOptions.TOPIC_PREFIX));
    }

    @Test
    void allocatedReplicationBlocksCannotPartiallyOverlap() {
        TreeSet<Integer> blocks = new TreeSet<>();
        for (int index = 0; index < 10_000; index++) {
            int base = Integer.parseInt(factory.prepareExecutionOptions(options("initial"), "instance-" + index)
                    .get(MySqlSourceOptions.SERVER_ID));
            assertEquals(1, base % 32);
            assertTrue(base > 0 && base <= Integer.MAX_VALUE - 16);
            blocks.add(base);
        }
        Integer previous = null;
        for (int base : blocks) {
            if (previous != null) {
                assertTrue(base - previous >= 32);
            }
            previous = base;
        }
    }

    private static ReadonlyConfig options(String startup) {
        return ReadonlyConfig.fromMap(Map.of(MySqlSourceOptions.SCAN_STARTUP_MODE.key(), startup));
    }
}
