package io.yak.ops.security.common.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 用户消息持久化对象。
 *
 * @author weifuwan
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("yak_security_message")
public class MessagePO extends BasePO {

  /** 消息标题。 */
  private String title;

  /** 列表摘要。 */
  private String summary;

  /** 消息内容。 */
  @ToString.Exclude
  private String content;

  /** 消息类型。 */
  @TableField("message_type")
  private String type;

  /** 消息级别。 */
  @TableField("message_level")
  private String level;

  /** 消息范围。 */
  @TableField("message_scope")
  private String scope;

  /** 项目标识。 */
  private Long projectId;

  /** 业务来源类型。 */
  private String sourceType;

  /** 业务来源标识。 */
  private String sourceId;

  /** 前端详情跳转路径。 */
  private String actionPath;

  /** 消息是否已读。 */
  private Boolean readTag;

  /** 已读时间。 */
  private Date readTime;

  /** 关联操作日志标识。 */
  private Long oplogId;

  /** 接收用户标识。 */
  private Long userId;
}
