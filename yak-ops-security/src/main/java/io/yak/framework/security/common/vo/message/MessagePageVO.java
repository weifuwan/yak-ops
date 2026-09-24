package io.yak.framework.security.common.vo.message;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 消息分页结果，兼容 Yak Ops 当前 records + total 契约。 */
@Data
@AllArgsConstructor
public class MessagePageVO {
  private List<MessageVO> records;
  private long total;
}
