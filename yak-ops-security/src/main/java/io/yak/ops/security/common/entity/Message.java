package io.yak.ops.security.common.entity;

import java.util.Date;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户消息实体。
 *
 * <p>消息既可以是系统级通知，也可以归属某个项目；业务来源通过
 * {@code sourceType + sourceId} 建立弱关联，避免消息中心反向依赖具体业务模块。</p>
 *
 * @author weifuwan
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Message extends BaseEntity {
  /** 消息标题。 */
  private String title;
  /** 列表摘要。 */
  private String summary;
  /** 消息正文。 */
  private String content;
  /** 消息类型，例如 TASK、QUALITY、SECURITY、SYSTEM。 */
  private String type;
  /** 消息级别，例如 INFO、SUCCESS、WARNING、ERROR。 */
  private String level;
  /** 消息范围：SYSTEM 或 PROJECT。 */
  private String scope;
  /** 项目标识；系统级消息为空。 */
  private Long projectId;
  /** 业务来源类型。 */
  private String sourceType;
  /** 业务来源标识。 */
  private String sourceId;
  /** 前端详情跳转路径。 */
  private String actionPath;
  /** 已读标记。 */
  private Boolean readTag;
  /** 已读时间。 */
  private Date readTime;
  /** 接收用户标识。 */
  private Long userId;
  /** 操作日志标识。 */
  private Long oplogId;
}
