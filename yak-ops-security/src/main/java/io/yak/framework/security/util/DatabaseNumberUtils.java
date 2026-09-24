package io.yak.framework.security.util;

/**
 * 数据库数字类型转换工具。
 *
 * <p>用于统一处理数据库驱动返回的数字标量值。
 *
 * @author weifuwan
 */
public final class DatabaseNumberUtils {

  /**
   * 禁止实例化工具类。
   */
  private DatabaseNumberUtils() {}

  /**
   * 将数据库返回的可空数字值转换为 {@link Long}。
   *
   * @param value 数据库返回的标量值
   * @return 转换后的 Long 值，输入为 null 时返回 null
   * @throws IllegalStateException 当数据库驱动返回非数字类型时抛出
   */
  public static Long toLong(Object value) {
    if (value == null) {
      return null;
    }

    if (value instanceof Number) {
      return ((Number) value).longValue();
    }

    throw new IllegalStateException(
            "数据库 ID 查询结果不是数字类型，实际类型=" + value.getClass().getName());
  }
}
