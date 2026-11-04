package org.apache.flink.cdc.common.data;

import java.time.LocalTime;
import java.util.Objects;

/**
 * An internal data structure representing data of {@link
 * org.apache.flink.cdc.common.types.TimeType}.
 */
public class TimeData implements Comparable<TimeData> {

    private static final int SECONDS_TO_MILLIS = 1000;
    private static final int MILLIS_TO_MICRO = 1000;
    private static final int MILLIS_TO_NANO = 1_000_000;

    private final int millisOfDay;

    private TimeData(int millisOfDay) {
        this.millisOfDay = millisOfDay;
    }

    public static TimeData fromSecondOfDay(int secondOfDay) {
        return new TimeData(secondOfDay * SECONDS_TO_MILLIS);
    }

    public static TimeData fromMillisOfDay(int millisOfDay) {
        return new TimeData(millisOfDay);
    }

    public static TimeData fromMicroOfDay(long microOfDay) {
        return new TimeData((int) (microOfDay / MILLIS_TO_MICRO));
    }

    public static TimeData fromNanoOfDay(long nanoOfDay) {
        // millisOfDay should not exceed 86400000, which is safe to fit into INT.
        return new TimeData((int) (nanoOfDay / MILLIS_TO_NANO));
    }

    public static TimeData fromLocalTime(LocalTime localTime) {
        return fromNanoOfDay(localTime.toNanoOfDay());
    }

    public static TimeData fromIsoLocalTimeString(String timeString) {
        return fromLocalTime(LocalTime.parse(timeString));
    }

    public int toMillisOfDay() {
        return millisOfDay;
    }

    public LocalTime toLocalTime() {
        return LocalTime.ofNanoOfDay((long) millisOfDay * MILLIS_TO_NANO);
    }

    public String toString() {
        return toLocalTime().toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof TimeData)) {
            return false;
        }

        TimeData timeData = (TimeData) o;
        return millisOfDay == timeData.millisOfDay;
    }

    @Override
    public int compareTo(TimeData other) {
        return Long.compare(millisOfDay, other.millisOfDay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(millisOfDay);
    }
}
