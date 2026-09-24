package io.yak.ops.security.common.vo.message;

import lombok.Data;

/**
 * 消息视图对象。
 *
 * <p>{@code readTag/oplogId} 保留旧接口兼容，{@code status/operationLogId}
 * 用于新的消息中心契约。</p>
 *
 * @author weifuwan
 */
@Data
public class MessageVO {
  /** 主键标识。 */
  private Long id;
  /** 消息标题。 */
  private String title;
  /** 列表摘要。 */
  private String summary;
  /** 消息内容。 */
  private String content;
  /** 消息类型。 */
  private String type;
  /** 消息级别。 */
  private String level;
  /** 消息范围。 */
  private String scope;
  /** 项目标识。 */
  private Long projectId;
  /** 业务来源类型。 */
  private String sourceType;
  /** 业务来源标识。 */
  private String sourceId;
  /** 前端详情跳转路径。 */
  private String actionPath;
  /** 新消息中心状态：UNREAD 或 READ。 */
  private String status;
  /** 旧接口已读标记。 */
  private Boolean readTag;
  /** 创建时间，Unix 毫秒。 */
  private Long createTime;
  /** 已读时间，Unix 毫秒。 */
  private Long readTime;
  /** 新消息中心操作日志标识。 */
  private Long operationLogId;
  /** 旧接口操作日志标识。 */
  private Long oplogId;
}
