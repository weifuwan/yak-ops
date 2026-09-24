package io.yak.ops.security.common.enums.message;

public enum MessageCode {
  ROLE_ADD_MESSAGE("角色权限变动通知",
                   "管理员于%" +
                   "s，为您分配了角色{%s}" +
                   "，请知悉；"),
  ROLE_REMOVE_MESSAGE(
      "角色权限变动通知",
      "管理员于%s，移除了角色{%s}" +
      "，请知悉；");

  private final String title;
  private final String content;

  private MessageCode(String title, String content) {
    this.title = title;
    this.content = content;
  }

  public String getTitle() { return this.title; }

  public String getContent() { return this.content; }
}
