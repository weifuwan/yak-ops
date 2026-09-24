package io.yak.ops.security.common.enums.resource;

public enum ControlLevelCode {
  NONE(0, "无权限"),
  VIEW(1, "查看权限"),
  ADMIN(2, "管理权限");

  private final Integer type;
  private final String info;

  private ControlLevelCode(Integer type, String info) {
    this.type = type;
    this.info = info;
  }

  public static ControlLevelCode getByType(Integer type) {
    ControlLevelCode[] controlLevelCodes;
    for (ControlLevelCode controlLevelCode :
         controlLevelCodes = ControlLevelCode.values()) {
      if (!controlLevelCode.type.equals(type))
        continue;
      return controlLevelCode;
    }
    return null;
  }

  public Integer getType() { return this.type; }

  public String getInfo() { return this.info; }
}
