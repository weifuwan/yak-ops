package io.yak.ops.security.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 数学计算工具类。
 *
 * <p>用于生成指定长度的随机数以及计算集合交集。
 *
 * @author weifuwan
 */
public final class MathUtil {

    /**
     * 禁止实例化工具类。
     */
    private MathUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 生成指定长度的正整数随机数。
     *
     * @param len 随机数长度，取值范围为 1 至 18
     * @return 指定长度的随机数，长度不合法时返回 0
     */
    public static long getRandomNumber(int len) {
        if (len <= 0 || len > 18) {
            return 0L;
        }

        if (len == 1) {
            return ThreadLocalRandom.current().nextLong(1L, 10L);
        }

        long minValue = (long) Math.pow(10, len - 1);
        long maxValue = (long) Math.pow(10, len);

        return ThreadLocalRandom.current().nextLong(minValue, maxValue);
    }

    /**
     * 获取两个 Long 集合的交集。
     *
     * @param list1 第一个集合
     * @param list2 第二个集合
     * @return 两个集合的交集，任一集合为空时返回空集合
     */
    public static Set<Long> getIntersection(
            List<Long> list1,
            List<Long> list2) {

        if (list1 == null
                || list1.isEmpty()
                || list2 == null
                || list2.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> result = new HashSet<>();
        Set<Long> targetSet = new HashSet<>(list2);

        for (Long number : list1) {
            if (targetSet.contains(number)) {
                result.add(number);
            }
        }

        return result;
    }
}
