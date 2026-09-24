package io.yak.ops.security.common.enums.project;

public enum ProjectUserCode {
  NORMAL(0, "普通用户"),
  OWNER(1, "项目负责人");

  private final Integer type;
  private final String info;

  public Integer getType() { return this.type; }

  public String getInfo() { return this.info; }

  private ProjectUserCode(Integer type, String info) {
    this.type = type;
    this.info = info;
  }
}
