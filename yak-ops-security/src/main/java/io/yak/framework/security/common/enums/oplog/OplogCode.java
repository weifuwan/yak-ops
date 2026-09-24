package io.yak.framework.security.common.enums.oplog;

public enum OplogCode {
  OPERATE_PAGE(1, "操作页面"),
  OPERATE_TYPE(2, "操作类型"),
  TARGET_TYPE(3, "对象分类");

  private final Integer type;
  private final String info;

  private OplogCode(Integer type, String info) {
    this.type = type;
    this.info = info;
  }

  public Integer getType() { return this.type; }

  public String getInfo() { return this.info; }
}
