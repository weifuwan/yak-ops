package io.yak.ops.security.common.enums.resource;

public enum ShowLevelCode {
  PROJECT(1, "项目级别"),
  RESOURCE_TYPE(2, "资源类别级别"),
  RESOURCE(3, "资源级别");

  private final Integer type;
  private final String info;

  private ShowLevelCode(Integer type, String info) {
    this.type = type;
    this.info = info;
  }

  public static ShowLevelCode getByType(Integer type) {
    ShowLevelCode[] showLevelCodes;
    for (ShowLevelCode showLevelCode :
         showLevelCodes = ShowLevelCode.values()) {
      if (!showLevelCode.type.equals(type))
        continue;
      return showLevelCode;
    }
    return null;
  }

  public Integer getType() { return this.type; }

  public String getInfo() { return this.info; }
}
