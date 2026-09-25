package io.yak.ops.common.util;

import java.util.Collection;

/**
 * 统一处理集合为空和非空判断。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class CollectionUtils {

    private CollectionUtils() {}

    public static boolean isEmpty(Collection<?> values) {
        return values == null || values.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> values) {
        return !isEmpty(values);
    }
}
