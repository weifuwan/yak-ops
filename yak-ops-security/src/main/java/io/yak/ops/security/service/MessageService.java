package io.yak.ops.security.service;

import io.yak.ops.security.common.dto.message.MessageDTO;
import io.yak.ops.security.common.dto.message.MessagePageQueryDTO;
import io.yak.ops.security.common.vo.message.MessagePageVO;
import io.yak.ops.security.common.vo.message.MessageVO;
import java.util.List;

/** 消息服务接口。 */
public interface MessageService {

  void saveMessage(MessageDTO messageDTO);

  List<MessageVO> getMessageListByUsernameAndReadTag(
          String username,
          Boolean readTag);

  /** 旧内部契约：按 ID 切换已读状态。 */
  void changeMessageStatus(List<Long> messageIdList);

  /** HTTP 兼容接口使用的用户隔离版本。 */
  void changeMessageStatus(
          String username,
          List<Long> messageIdList);

  void saveMessages(List<MessageDTO> messageDTOList);

  MessagePageVO getMessagePage(
          String username,
          MessagePageQueryDTO queryDTO);

  MessageVO getMessageDetail(
          String username,
          Long messageId);

  void markMessageRead(
          String username,
          Long messageId);

  void markMessagesRead(
          String username,
          List<Long> messageIds);

  int getUnreadMessageCount(String username);
}
