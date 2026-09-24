package io.yak.framework.security.common.dto.message;

import lombok.Data;

/** 当前用户消息分页查询条件。 */
@Data
public class MessagePageQueryDTO {
  /** 页码，从 1 开始。 */
  private Integer pageNum = 1;
  /** 每页数量。 */
  private Integer pageSize = 10;
  /** READ 或 UNREAD。 */
  private String status;
  /** 消息类型。 */
  private String type;
  /** 当前项目；传入后同时返回 SYSTEM 消息与该项目消息。 */
  private Long projectId;
  /** 创建时间起点，Unix epoch milliseconds。 */
  private Long startTime;
  /** 创建时间终点，Unix epoch milliseconds。 */
  private Long endTime;
}
