package io.yak.ops.plugin.scheduler.quartz;

import io.yak.ops.plugin.scheduler.api.CronService;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.quartz.CronExpression;

/** Evaluates Quartz Cron rules without initializing a Scheduler or a JobStore. */
public final class QuartzCronService implements CronService {
    @Override
    public void validate(String cronExpression, String timeZone) {
        expression(cronExpression, timeZone);
    }

    @Override
    public List<Instant> nextFireTimes(String cronExpression, String timeZone, Instant after, int count) {
        if (after == null || count < 1 || count > 5) {
            throw new IllegalArgumentException("A preview requires a time and one to five results");
        }
        CronExpression expression = expression(cronExpression, timeZone);
        Date cursor;
        try {
            cursor = Date.from(after);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Preview time is outside the supported range", failure);
        }
        List<Instant> times = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Date next = expression.getNextValidTimeAfter(cursor);
            if (next == null) {
                break;
            }
            times.add(next.toInstant());
            cursor = next;
        }
        return List.copyOf(times);
    }

    private CronExpression expression(String cron, String zone) {
        if (cron == null
                || cron.isBlank()
                || cron.length() > 256
                || zone == null
                || !ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new IllegalArgumentException("A valid Cron expression and named time zone are required");
        }
        try {
            CronExpression expression = new CronExpression(cron);
            expression.setTimeZone(TimeZone.getTimeZone(ZoneId.of(zone)));
            return expression;
        } catch (ParseException failure) {
            throw new IllegalArgumentException("Invalid Quartz Cron expression", failure);
        }
    }
}
