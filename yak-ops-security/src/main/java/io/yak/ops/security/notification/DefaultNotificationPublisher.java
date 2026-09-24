package io.yak.ops.security.notification;

import io.yak.ops.security.common.dto.message.MessageDTO;
import io.yak.ops.security.service.MessageService;
import java.util.List;

/** 默认通知发布器，委托 yak-security MessageService 完成持久化。 */
public class DefaultNotificationPublisher implements NotificationPublisher {

  private final MessageService messageService;

  public DefaultNotificationPublisher(MessageService messageService) {
    this.messageService = messageService;
  }

  @Override
  public void publish(MessageDTO notification) {
    messageService.saveMessage(notification);
  }

  @Override
  public void publishAll(List<MessageDTO> notifications) {
    messageService.saveMessages(notifications);
  }
}
