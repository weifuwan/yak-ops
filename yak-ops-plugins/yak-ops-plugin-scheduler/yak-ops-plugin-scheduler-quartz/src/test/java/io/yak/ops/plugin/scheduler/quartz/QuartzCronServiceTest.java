package io.yak.ops.plugin.scheduler.quartz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.quartz.CronScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.spi.OperableTrigger;

/** Compares UTC previews with the same Quartz Trigger sequence without starting a scheduler. */
class QuartzCronServiceTest {
    private final QuartzCronService cron = new QuartzCronService();

    @Test
    void namedZoneControlsUtcTimesAndTheLowerBoundIsExclusive() {
        Instant after = Instant.parse("2026-10-13T00:00:00Z");
        assertEquals(
                List.of(Instant.parse("2026-10-14T00:00:00Z")),
                cron.nextFireTimes("0 0 8 * * ?", "Asia/Shanghai", after, 1));
        assertEquals(
                List.of(Instant.parse("2026-10-13T08:00:00Z")), cron.nextFireTimes("0 0 8 * * ?", "UTC", after, 1));
        assertEquals(
                Instant.parse("2026-10-13T00:01:00Z"),
                cron.nextFireTimes("0 * * * * ?", "UTC", after.plusMillis(123), 1)
                        .getFirst());
    }

    @Test
    void validatesExpiredExpressionsWithoutPretendingTheyHaveFutureTimes() {
        cron.validate("0 0 0 1 1 ? 2020", "UTC");
        assertTrue(cron.nextFireTimes("0 0 0 1 1 ? 2020", "UTC", Instant.parse("2026-01-01T00:00:00Z"), 5)
                .isEmpty());
    }

    @Test
    void rejectsInvalidCronAndTimeZonesWithoutGmtFallback() {
        for (String zone : List.of("Asia/NotAZone", "", "+08:00")) {
            assertThrows(IllegalArgumentException.class, () -> cron.validate("0 * * * * ?", zone));
        }
        for (String expression : List.of("", "not a cron", "0 80 * * * ?", "x".repeat(257))) {
            assertThrows(IllegalArgumentException.class, () -> cron.validate(expression, "UTC"));
        }
        assertThrows(IllegalArgumentException.class, () -> cron.validate(null, "UTC"));
        assertThrows(IllegalArgumentException.class, () -> cron.validate("0 * * * * ?", null));
    }

    @Test
    void boundsPreviewSizeAndReturnsAnImmutableList() {
        Instant after = Instant.parse("2026-10-13T00:00:00Z");
        for (int count : List.of(0, 6, Integer.MAX_VALUE)) {
            assertThrows(IllegalArgumentException.class, () -> cron.nextFireTimes("0 * * * * ?", "UTC", after, count));
        }
        assertThrows(IllegalArgumentException.class, () -> cron.nextFireTimes("0 * * * * ?", "UTC", null, 1));
        List<Instant> times = cron.nextFireTimes("0 * * * * ?", "UTC", after, 5);
        assertEquals(5, times.size());
        assertThrows(UnsupportedOperationException.class, () -> times.add(after));
    }

    @Test
    void preservesAdvancedQuartzSyntax() {
        assertEquals(
                Instant.parse("2026-10-31T09:15:00Z"),
                cron.nextFireTimes("0 15 9 L * ?", "UTC", Instant.parse("2026-10-01T00:00:00Z"), 1)
                        .getFirst());
        assertEquals(
                Instant.parse("2026-10-12T09:15:00Z"),
                cron.nextFireTimes("0 15 9 ? * MON#2", "UTC", Instant.parse("2026-10-01T00:00:00Z"), 1)
                        .getFirst());
    }

    @Test
    void dstPreviewMatchesTheActualQuartzTriggerSequence() {
        for (Instant after : List.of(Instant.parse("2026-03-07T00:00:00Z"), Instant.parse("2026-10-31T00:00:00Z"))) {
            for (String expression : List.of("0 30 1 * * ?", "0 30 2 * * ?")) {
                OperableTrigger trigger = (OperableTrigger) TriggerBuilder.newTrigger()
                        .withIdentity("preview")
                        .startAt(Date.from(after.plusMillis(1)))
                        .withSchedule(CronScheduleBuilder.cronSchedule(expression)
                                .inTimeZone(TimeZone.getTimeZone(ZoneId.of("America/New_York")))
                                .withMisfireHandlingInstructionDoNothing())
                        .build();
                trigger.computeFirstFireTime(null);
                for (Instant time : cron.nextFireTimes(expression, "America/New_York", after, 5)) {
                    assertEquals(time, trigger.getNextFireTime().toInstant());
                    trigger.triggered(null);
                }
            }
        }
    }
}
