package io.yak.ops.common.util;

import java.time.LocalDateTime;

/**
 * LocalDateTime 时间工具。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class DateUtils {

    private DateUtils() {}

    /** 获取当前本地时间。 */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
