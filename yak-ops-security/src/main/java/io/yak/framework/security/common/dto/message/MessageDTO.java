package io.yak.framework.security.common.dto.message;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息数据传输对象。
 *
 * <p>业务模块只需要描述通知本身，不需要了解消息表结构。Project 归属以
 * {@code projectId} 为唯一事实来源；{@code scope} 仅为兼容字段，持久化前会由
 * MessageService 根据 projectId 重新推导。</p>
 *
 * @author weifuwan
 */
@Data
@NoArgsConstructor
public class MessageDTO {
  /** 消息标题。 */
  private String title;
  /** 列表摘要。 */
  private String summary;
  /** 消息内容。 */
  private String content;
  /** 消息类型。 */
  private String type;
  /** 消息级别：INFO/SUCCESS/WARNING/ERROR。 */
  private String level;
  /** 兼容字段；最终 scope 由 projectId 推导。 */
  private String scope;
  /** 项目标识；为空表示 SYSTEM，非空表示 PROJECT。 */
  private Long projectId;
  /** 业务来源类型。 */
  private String sourceType;
  /** 业务来源标识。 */
  private String sourceId;
  /** 前端详情跳转路径。 */
  private String actionPath;
  /** 操作日志标识。 */
  private Long oplogId;
  /** 用户标识。 */
  private Long userId;

  public MessageDTO(Long userId, Long oplogId) {
    this.userId = userId;
    this.oplogId = oplogId;
  }
}
