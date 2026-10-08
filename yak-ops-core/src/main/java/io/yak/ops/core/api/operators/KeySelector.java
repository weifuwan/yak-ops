package io.yak.ops.core.api.operators;

/**
 * 为记录提供稳定的分区键。
 *
 * <p>键必须非空，equals/hashCode 必须依据业务身份稳定（不使用数组或对象默认身份哈希）。
 * 对于 CDC 应使用表主键；KEYED 仅保证同键路由到相同子任务，不提供端到端事务顺序保证。
 *
 * @param <T> 上游记录类型
 */
@FunctionalInterface
public interface KeySelector<T> {

    Object getKey(T record) throws Exception;
}
