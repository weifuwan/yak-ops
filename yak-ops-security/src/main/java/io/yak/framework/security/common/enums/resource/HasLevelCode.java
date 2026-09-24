package io.yak.framework.security.common.enums.resource;

public enum HasLevelCode {
  NONE(0, "不拥有"),
  HALF(1, "半拥有"),
  ALL(2, "全拥有");

  private final Integer type;
  private final String info;

  private HasLevelCode(Integer type, String info) {
    this.type = type;
    this.info = info;
  }

  public static HasLevelCode getByType(Integer type) {
    HasLevelCode[] hasLevelCodes;
    for (HasLevelCode hasLevelCode : hasLevelCodes = HasLevelCode.values()) {
      if (!hasLevelCode.type.equals(type))
        continue;
      return hasLevelCode;
    }
    return null;
  }

  public Integer getType() { return this.type; }

  public String getInfo() { return this.info; }
}
