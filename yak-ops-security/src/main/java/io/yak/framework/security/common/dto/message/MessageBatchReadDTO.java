package io.yak.framework.security.common.dto.message;

import java.util.List;
import lombok.Data;

/** 批量消息已读请求。 */
@Data
public class MessageBatchReadDTO {
  private List<Long> ids;
}
