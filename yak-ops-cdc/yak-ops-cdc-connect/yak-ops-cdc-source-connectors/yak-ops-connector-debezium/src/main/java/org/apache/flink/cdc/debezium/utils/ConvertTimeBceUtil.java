package org.apache.flink.cdc.debezium.utils;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.chrono.IsoEra;

/** Convert And Check TimeBce Util. */
public class ConvertTimeBceUtil {

    private static final Date ONE_CE = Date.valueOf("0001-01-01");

    public static String resolveEra(boolean isBce, String value) {
        String mangledValue = value;
        if (isBce) {
            if (mangledValue.startsWith("-")) {
                mangledValue = mangledValue.substring(1);
            }
            if (!mangledValue.endsWith(" BC")) {
                mangledValue += " BC";
            }
        }
        return mangledValue;
    }

    public static boolean isBce(LocalDate date) {
        return date.getEra() == IsoEra.BCE;
    }

    public static String resolveEra(LocalDate date, String value) {
        return resolveEra(isBce(date), value);
    }

    public static String resolveEra(Date date, String value) {
        return resolveEra(date.before(ONE_CE), value);
    }

    public static String resolveEra(Timestamp timestamp, String value) {
        return resolveEra(timestamp.before(ONE_CE), value);
    }
}
