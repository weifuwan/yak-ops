package io.yak.ops.common.util;

import java.util.List;
import org.springframework.beans.BeanUtils;

/**
 * 基于同名属性完成简单 Bean 复制，避免各领域重复维护无业务语义的映射样板代码。
 *
 * @author weifuwan
 * @since 2026-09-25
 */
public final class BeanCopyUtils {

    private BeanCopyUtils() {}

    public static <T> T copy(Object source, Class<T> target) {
        if (ObjectUtils.isNull(source)) return null;
        if (ObjectUtils.isNull(target)) throw new IllegalArgumentException("target must not be null");
        try {
            T result = target.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, result);
            return result;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Bean copy failed: "
                            + source.getClass().getName()
                            + " -> "
                            + target.getName()
                            + ". Target class must provide a no-args constructor.",
                    exception);
        }
    }

    public static <T, K> List<K> copyList(List<T> source, Class<K> target) {
        if (CollectionUtils.isEmpty(source)) return List.of();
        return source.stream()
                .filter(ObjectUtils::isNotNull)
                .map(value -> copy(value, target))
                .toList();
    }
}
