package io.yak.ops.security.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.security.common.entity.Message;
import java.util.Date;
import java.util.List;

/** 消息数据访问接口。 */
public interface MessageDao {
  void insert(Message message);

  void update(Message message);

  void insertBatch(List<Message> messages);

  List<Message> selectListByUserIdAndReadTag(
          Long userId,
          Boolean readTag,
          List<Long> visibleProjectIds,
          boolean restrictProjects);

  List<Message> selectListByMessageIdList(List<Long> messageIds);

  List<Message> selectListByMessageIdListAndUserId(
          List<Long> messageIds,
          Long userId);

  Message selectByMessageIdAndUserId(Long messageId, Long userId);

  IPage<Message> selectPageByUserId(
          Long userId,
          Boolean readTag,
          String type,
          List<Long> visibleProjectIds,
          boolean restrictProjects,
          Date startTime,
          Date endTime,
          int pageNum,
          int pageSize);

  long countUnreadByUserId(
          Long userId,
          List<Long> visibleProjectIds,
          boolean restrictProjects);
}
