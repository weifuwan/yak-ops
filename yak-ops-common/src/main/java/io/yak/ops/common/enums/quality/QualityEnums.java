package io.yak.ops.common.enums.quality;

/** 数据质量 API、领域与持久化共享的稳定枚举。 */
public final class QualityEnums {

  private QualityEnums() {}

  public enum RuleScope { TABLE, COLUMN }

  public enum RuleType {
    TABLE_ROW_COUNT(RuleScope.TABLE, "完整性", "行"),
    COLUMN_NOT_NULL(RuleScope.COLUMN, "完整性", "%"),
    COLUMN_UNIQUE(RuleScope.COLUMN, "唯一性", "%"),
    COLUMN_RANGE(RuleScope.COLUMN, "有效性", "条"),
    COLUMN_ENUM(RuleScope.COLUMN, "准确性", "条"),
    CUSTOM_SQL(RuleScope.TABLE, "自定义", null);

    private final RuleScope scope;
    private final String dimension;
    private final String unit;

    RuleType(RuleScope scope, String dimension, String unit) {
      this.scope = scope;
      this.dimension = dimension;
      this.unit = unit;
    }

    public RuleScope scope() { return scope; }
    public String dimension() { return dimension; }
    public String unit() { return unit; }
  }

  public enum ComparisonOperator {
    GT(">"), GTE(">="), EQ("="), LTE("<="), LT("<"), BETWEEN("BETWEEN");

    private final String symbol;

    ComparisonOperator(String symbol) {
      this.symbol = symbol;
    }

    public String symbol() { return symbol; }

    public static ComparisonOperator fromValue(String value) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("比较方式不能为空");
      }
      for (ComparisonOperator operator : values()) {
        if (operator.name().equalsIgnoreCase(value)
            || operator.symbol.equalsIgnoreCase(value)) {
          return operator;
        }
      }
      throw new IllegalArgumentException("不支持的比较方式：" + value);
    }
  }

  public enum ExecutionStatus { WAITING, RUNNING, SUCCESS, FAILED, CANCELED }
  public enum CheckResult { PASSED, NOT_PASSED, ERROR, RUNNING, NOT_RUN }
  public enum TriggerType { MANUAL, WORKFLOW, SCHEDULE }
  public enum RunMode { MANUAL, SCHEDULE }
  public enum ScheduleFrequency { DAILY, WEEKLY, CRON }
  public enum ScheduleWeekday { MON, TUE, WED, THU, FRI, SAT, SUN }
  public enum RuleFailureAction { CONTINUE, STOP }
  public enum NotifyChannel { MESSAGE, EMAIL, WEBHOOK }
  public enum AlertLevel { WARNING, CRITICAL }
  public enum CheckType { NUMERIC }
  public enum CheckMethod { FIXED_VALUE }
  public enum LogLevel { INFO, WARN, ERROR }
}
