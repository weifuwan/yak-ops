package io.yak.ops.security.util;

import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具类。
 *
 * <p>用于在审计日志持久化前，对文本中的密码、令牌、授权信息和 Cookie 等敏感数据进行脱敏。
 *
 * @author weifuwan
 */
public final class SensitiveDataSanitizer {

  /**
   * JSON、表单或键值文本中的敏感字段匹配规则。
   */
  private static final Pattern JSON_SECRET =
          Pattern.compile(
                  "(?i)(\\\"?(?:password|passwd|pw|salt|token|secret|authorization|cookie)"
                          + "\\\"?\\s*[:=]\\s*\\\"?)[^\\\",}\\s]*");

  /**
   * Bearer 认证令牌匹配规则。
   */
  private static final Pattern BEARER =
          Pattern.compile("(?i)Bearer\\s+[^\\s,;}]+");

  /**
   * 禁止实例化工具类。
   */
  private SensitiveDataSanitizer() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * 对文本中的敏感数据进行脱敏处理。
   *
   * <p>当前支持处理密码、盐值、令牌、密钥、授权信息、Cookie 和 Bearer Token。
   *
   * @param value 待脱敏的文本
   * @return 脱敏后的文本，输入为 null 时返回 null
   */
  public static String sanitize(String value) {
    if (value == null) {
      return null;
    }

    String sanitized =
            BEARER.matcher(value).replaceAll("Bearer [REDACTED]");

    return JSON_SECRET
            .matcher(sanitized)
            .replaceAll("$1[REDACTED]");
  }
}