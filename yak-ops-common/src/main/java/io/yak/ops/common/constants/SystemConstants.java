package io.yak.ops.common.constants;

import java.util.TimeZone;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SystemConstants {

    public static final TimeZone DEFAULT_TIME_ZONE = TimeZone.getDefault();

    static {
        log.info("init timezone: {}", DEFAULT_TIME_ZONE);
    }
}
