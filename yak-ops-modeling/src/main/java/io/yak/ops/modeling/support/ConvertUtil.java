package io.yak.ops.modeling.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.beans.BeanUtils;

/**
 * Small bean conversion helper retained from the original modeling module.
 */
public final class ConvertUtil {

    private ConvertUtil() {
    }

    public static <T> T sourceToTarget(Object source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        try {
            T target = targetType.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to convert modeling object to " + targetType.getName(), ex);
        }
    }

    public static <T> List<T> sourceListToTarget(Collection<?> source, Class<T> targetType) {
        if (source == null) {
            return null;
        }
        List<T> result = new ArrayList<>(source.size());
        for (Object item : source) {
            result.add(sourceToTarget(item, targetType));
        }
        return result;
    }
}
