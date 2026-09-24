package io.yak.ops.security.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;

/**
 * Bean 属性复制工具。
 *
 * <p>统一处理单个对象、集合及分页对象之间的同名属性转换。
 *
 * @author weifuwan
 */
public final class CopyBeanUtil {

    /**
     * 禁止实例化工具类。
     */
    private CopyBeanUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 将源对象的同名属性复制到指定类型的新对象中。
     *
     * @param source 源对象
     * @param target 目标类型
     * @param <T> 目标对象类型
     * @return 属性复制后的目标对象，复制失败时返回 null
     */
    public static <T> T copy(Object source, Class<T> target) {
        if (source == null || target == null) {
            return null;
        }

        try {
            T newInstance = target.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, newInstance);
            return newInstance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Bean copy failed: "
                            + source.getClass().getName()
                            + " -> "
                            + target.getName()
                            + ". Target class must provide a no-args constructor.",
                    e);
        }
    }

    /**
     * 将源对象集合复制为指定类型的目标对象集合。
     *
     * @param source 源对象集合
     * @param target 目标类型
     * @param <T> 源对象类型
     * @param <K> 目标对象类型
     * @return 属性复制后的目标对象集合
     */
    public static <T, K> List<K> copyList(List<T> source, Class<K> target) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        return source.stream()
                .filter(Objects::nonNull)
                .map(element -> CopyBeanUtil.copy(element, target))
                .collect(Collectors.toList());
    }

    /**
     * 将源对象集合复制为目标对象集合，并对每个目标对象执行自定义处理。
     *
     * @param source 源对象集合
     * @param target 目标类型
     * @param consumer 目标对象处理逻辑
     * @param <T> 源对象类型
     * @param <K> 目标对象类型
     * @return 处理后的目标对象集合
     */
    public static <T, K> List<K> copyList(List<T> source, Class<K> target, Consumer<K> consumer) {

        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        return source.stream()
                .map(element -> CopyBeanUtil.copy(element, target))
                .peek(consumer)
                .collect(Collectors.toList());
    }

    /**
     * 将源分页对象复制为指定记录类型的目标分页对象。
     *
     * @param source 源分页对象
     * @param target 目标记录类型
     * @param <T> 源记录类型
     * @param <K> 目标记录类型
     * @return 属性复制后的目标分页对象
     */
    public static <T, K> IPage<K> copyPage(IPage<T> source, Class<K> target) {

        if (source == null || target == null) {
            return null;
        }

        Page<K> targetPage = new Page<>();
        BeanUtils.copyProperties(source, targetPage);
        targetPage.setTotal(source.getTotal());
        targetPage.setRecords(CopyBeanUtil.copyList(source.getRecords(), target));

        return targetPage;
    }

    /**
     * 复制分页信息，但不复制分页中的记录集合。
     *
     * @param source 源分页对象
     * @param <T> 源记录类型
     * @param <K> 目标记录类型
     * @return 不包含记录数据的目标分页对象
     */
    @SuppressWarnings("unchecked")
    public static <T, K> IPage<K> copyPageExcludeList(IPage<T> source) {
        if (source == null) {
            return null;
        }

        return (IPage<K>) CopyBeanUtil.copy(source, Page.class);
    }
}
