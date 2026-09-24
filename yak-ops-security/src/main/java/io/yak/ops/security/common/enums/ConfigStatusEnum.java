package io.yak.ops.security.common.enums;

public enum ConfigStatusEnum {
  NORMAL(1, "正常"),
  DISABLE(2, "禁用");

  private int code;
  private String desc;

  private ConfigStatusEnum(int code, String desc) {
    this.code = code;
    this.desc = desc;
  }

  public int getCode() { return this.code; }

  public String getDesc() { return this.desc; }

  public static ConfigStatusEnum valueOf(Integer code) {
    if (code == null) {
      return null;
    }
    for (ConfigStatusEnum state : ConfigStatusEnum.values()) {
      if (state.getCode() != code.intValue())
        continue;
      return state;
    }
    return null;
  }
}
