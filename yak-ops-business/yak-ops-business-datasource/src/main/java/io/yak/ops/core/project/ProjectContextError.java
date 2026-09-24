package io.yak.ops.core.project;

/** Stable Project Space context failures shared by web and business layers. */
public enum ProjectContextError {
  PROJECT_REQUIRED(40010, "当前操作需要选择项目空间"),
  PROJECT_NOT_FOUND(40410, "项目空间不存在或当前用户不可访问"),
  PROJECT_UNAVAILABLE(40310, "项目空间当前不可用");

  private final int code;
  private final String message;

  ProjectContextError(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
