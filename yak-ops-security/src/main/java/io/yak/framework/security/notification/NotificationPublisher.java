package io.yak.framework.security.notification;

import io.yak.framework.security.common.dto.message.MessageDTO;
import java.util.List;

/**
 * 统一用户通知发布入口。
 *
 * <p>业务模块只依赖本接口发布值得用户关注的事件，不直接操作消息 DAO。</p>
 */
public interface NotificationPublisher {

  void publish(MessageDTO notification);

  void publishAll(List<MessageDTO> notifications);
}
