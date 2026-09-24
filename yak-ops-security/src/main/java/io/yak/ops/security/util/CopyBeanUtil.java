package io.yak.ops.security.util;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.BeanUtils;

/** Copies same-named bean properties for current Security DTO/VO mappings. */
public final class CopyBeanUtil {

    private CopyBeanUtil() {}

    public static <T> T copy(Object source, Class<T> target) {
        if (source == null) {
            return null;
        }
        Objects.requireNonNull(target, "target must not be null");
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
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(value -> copy(value, target))
                .toList();
    }
}
