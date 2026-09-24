package io.yak.ops.security.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.security.common.constant.SecurityPermissionCode;
import io.yak.ops.security.common.dto.message.MessageDTO;
import io.yak.ops.security.common.dto.message.MessagePageQueryDTO;
import io.yak.ops.security.common.entity.Message;
import io.yak.ops.security.common.entity.user.User;
import io.yak.ops.security.common.enums.ResultCode;
import io.yak.ops.security.common.vo.message.MessagePageVO;
import io.yak.ops.security.common.vo.message.MessageVO;
import io.yak.ops.security.context.AuthorizationSnapshot;
import io.yak.ops.security.dao.MessageDao;
import io.yak.ops.security.dao.UserDao;
import io.yak.ops.security.exception.YakSecurityException;
import io.yak.ops.security.service.MessageService;
import io.yak.ops.security.util.CopyBeanUtil;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/** 消息服务实现类。 */
@Service("yakSecurityMessageServiceImpl")
public class MessageServiceImpl implements MessageService {

  private static final String STATUS_READ = "READ";
  private static final String STATUS_UNREAD = "UNREAD";
  private static final String SCOPE_SYSTEM = "SYSTEM";
  private static final String SCOPE_PROJECT = "PROJECT";
  private static final String LEVEL_INFO = "INFO";
  private static final Set<String> MESSAGE_LEVELS =
          Set.of("INFO", "SUCCESS", "WARNING", "ERROR");
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_PAGE_SIZE = 100;

  private final MessageDao messageDao;
  private final UserDao userDao;
  private final AuthorizationSnapshotService authorizationSnapshotService;

  public MessageServiceImpl(
          MessageDao messageDao,
          UserDao userDao,
          AuthorizationSnapshotService authorizationSnapshotService) {
    this.messageDao = messageDao;
    this.userDao = userDao;
    this.authorizationSnapshotService = authorizationSnapshotService;
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void saveMessage(MessageDTO messageDTO) {
    if (messageDTO == null) {
      return;
    }
    Message message = convertToEntity(messageDTO);
    normalizeMessage(message);
    messageDao.insert(message);
  }

  @Override
  public List<MessageVO> getMessageListByUsernameAndReadTag(
          String username,
          Boolean readTag) {

    User user = requireUser(username);
    AuthorizationSnapshot authorization = authorization(user);
    ProjectVisibility visibility = resolveVisibility(authorization, null);
    List<Message> messages = messageDao.selectListByUserIdAndReadTag(
            user.getId(),
            readTag,
            visibility.projectIds(),
            visibility.restrictProjects());
    return convertToVOList(messages);
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void changeMessageStatus(List<Long> messageIdList) {
    toggleMessages(messageDao.selectListByMessageIdList(
            normalizeIds(messageIdList)));
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void changeMessageStatus(
          String username,
          List<Long> messageIdList) {

    User user = requireUser(username);
    AuthorizationSnapshot authorization = authorization(user);
    List<Message> messages = messageDao.selectListByMessageIdListAndUserId(
            normalizeIds(messageIdList), user.getId());
    toggleMessages(filterVisibleMessages(messages, authorization));
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void saveMessages(List<MessageDTO> messageDTOList) {
    if (CollectionUtils.isEmpty(messageDTOList)) {
      return;
    }

    List<Message> messages = messageDTOList.stream()
            .filter(Objects::nonNull)
            .map(this::convertToEntity)
            .peek(this::normalizeMessage)
            .collect(Collectors.toList());

    if (!messages.isEmpty()) {
      messageDao.insertBatch(messages);
    }
  }

  @Override
  public MessagePageVO getMessagePage(
          String username,
          MessagePageQueryDTO queryDTO) {

    User user = requireUser(username);
    AuthorizationSnapshot authorization = authorization(user);
    MessagePageQueryDTO query = queryDTO == null
            ? new MessagePageQueryDTO()
            : queryDTO;

    validateTimeRange(query.getStartTime(), query.getEndTime());

    int pageNum = query.getPageNum() == null
            ? 1
            : Math.max(1, query.getPageNum());
    int pageSize = query.getPageSize() == null
            ? DEFAULT_PAGE_SIZE
            : Math.max(1, Math.min(MAX_PAGE_SIZE, query.getPageSize()));
    ProjectVisibility visibility = resolveVisibility(
            authorization,
            query.getProjectId());

    IPage<Message> page = messageDao.selectPageByUserId(
            user.getId(),
            parseReadTag(query.getStatus()),
            normalizeText(query.getType()),
            visibility.projectIds(),
            visibility.restrictProjects(),
            toDate(query.getStartTime()),
            toDate(query.getEndTime()),
            pageNum,
            pageSize);

    return new MessagePageVO(
            convertToVOList(page.getRecords()),
            page.getTotal());
  }

  @Override
  public MessageVO getMessageDetail(
          String username,
          Long messageId) {

    User user = requireUser(username);
    Message message = messageDao.selectByMessageIdAndUserId(
            messageId, user.getId());
    if (!canAccessMessage(authorization(user), message)) {
      return null;
    }
    return convertToVO(message);
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void markMessageRead(
          String username,
          Long messageId) {

    if (messageId == null) {
      return;
    }
    User user = requireUser(username);
    Message message = messageDao.selectByMessageIdAndUserId(
            messageId, user.getId());
    if (canAccessMessage(authorization(user), message)) {
      markRead(message);
    }
  }

  @Override
  @Transactional(
          transactionManager = "yakSecurityTransactionManager",
          rollbackFor = Exception.class)
  public void markMessagesRead(
          String username,
          List<Long> messageIds) {

    List<Long> ids = normalizeIds(messageIds);
    if (ids.isEmpty()) {
      return;
    }
    User user = requireUser(username);
    AuthorizationSnapshot authorization = authorization(user);
    filterVisibleMessages(
            messageDao.selectListByMessageIdListAndUserId(ids, user.getId()),
            authorization)
            .forEach(this::markRead);
  }

  @Override
  public int getUnreadMessageCount(String username) {
    User user = requireUser(username);
    ProjectVisibility visibility = resolveVisibility(authorization(user), null);
    return Math.toIntExact(messageDao.countUnreadByUserId(
            user.getId(),
            visibility.projectIds(),
            visibility.restrictProjects()));
  }

  private void toggleMessages(List<Message> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return;
    }
    for (Message message : messages) {
      if (message == null) {
        continue;
      }
      boolean nextRead = !Boolean.TRUE.equals(message.getReadTag());
      message.setReadTag(nextRead);
      message.setReadTime(nextRead ? new Date() : null);
      messageDao.update(message);
    }
  }

  private void markRead(Message message) {
    if (message == null || Boolean.TRUE.equals(message.getReadTag())) {
      return;
    }
    message.setReadTag(true);
    message.setReadTime(new Date());
    messageDao.update(message);
  }

  private User requireUser(String username) {
    if (!StringUtils.hasText(username)) {
      throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
    }
    User user = userDao.selectByUsername(username.trim());
    if (user == null) {
      throw new YakSecurityException(ResultCode.USER_NOT_EXISTS);
    }
    return user;
  }

  private AuthorizationSnapshot authorization(User user) {
    return authorizationSnapshotService.get(user == null ? null : user.getId());
  }

  private ProjectVisibility resolveVisibility(
          AuthorizationSnapshot authorization,
          Long requestedProjectId) {

    if (requestedProjectId != null) {
      if (requestedProjectId <= 0L) {
        throw new YakSecurityException(ResultCode.PARAM_ERROR);
      }
      if (!authorization.canAccessProject(requestedProjectId)) {
        throw new YakSecurityException(ResultCode.NO_PERMISSION);
      }
      return new ProjectVisibility(List.of(requestedProjectId), true);
    }

    if (authorization.hasPermission(SecurityPermissionCode.ROOT)) {
      return new ProjectVisibility(List.of(), false);
    }
    return new ProjectVisibility(
            new ArrayList<>(authorization.getProjectIds()),
            true);
  }

  /**
   * A non-null project_id always makes a message project-owned for authorization,
   * regardless of the stored scope label.
   */
  private boolean canAccessMessage(
          AuthorizationSnapshot authorization,
          Message message) {

    return message != null
            && (message.getProjectId() == null
            || authorization.canAccessProject(message.getProjectId()));
  }

  private List<Message> filterVisibleMessages(
          List<Message> messages,
          AuthorizationSnapshot authorization) {

    if (CollectionUtils.isEmpty(messages)) {
      return new ArrayList<>();
    }
    return messages.stream()
            .filter(message -> canAccessMessage(authorization, message))
            .collect(Collectors.toList());
  }

  private Boolean parseReadTag(String status) {
    if (!StringUtils.hasText(status)) {
      return null;
    }
    if (STATUS_READ.equalsIgnoreCase(status)) {
      return true;
    }
    if (STATUS_UNREAD.equalsIgnoreCase(status)) {
      return false;
    }
    throw new YakSecurityException(ResultCode.PARAM_ERROR);
  }

  private String normalizeText(String value) {
    return StringUtils.hasText(value)
            ? value.trim().toUpperCase(Locale.ROOT)
            : null;
  }

  private Date toDate(Long epochMillis) {
    return epochMillis == null ? null : new Date(epochMillis);
  }

  private void validateTimeRange(Long startTime, Long endTime) {
    if ((startTime != null && startTime < 0L)
            || (endTime != null && endTime < 0L)
            || (startTime != null && endTime != null && startTime > endTime)) {
      throw new YakSecurityException(ResultCode.PARAM_ERROR);
    }
  }

  private Message convertToEntity(MessageDTO messageDTO) {
    Message message = CopyBeanUtil.copy(messageDTO, Message.class);
    if (message == null) {
      throw new IllegalStateException("消息对象转换失败");
    }
    return message;
  }

  /**
   * Normalizes the publishing contract before persistence.
   *
   * <p>scope is derived exclusively from projectId. Callers cannot persist a
   * SYSTEM message with a project owner, or a PROJECT message without one.</p>
   */
  private void normalizeMessage(Message message) {
    if (message.getProjectId() != null && message.getProjectId() <= 0L) {
      throw new YakSecurityException(ResultCode.PARAM_ERROR);
    }
    if (!StringUtils.hasText(message.getType())) {
      message.setType(message.getOplogId() == null ? "SYSTEM" : "SECURITY");
    } else {
      message.setType(message.getType().trim().toUpperCase(Locale.ROOT));
    }

    String level = StringUtils.hasText(message.getLevel())
            ? message.getLevel().trim().toUpperCase(Locale.ROOT)
            : LEVEL_INFO;
    if (!MESSAGE_LEVELS.contains(level)) {
      throw new YakSecurityException(ResultCode.PARAM_ERROR);
    }
    message.setLevel(level);
    message.setScope(message.getProjectId() == null
            ? SCOPE_SYSTEM
            : SCOPE_PROJECT);

    if (message.getReadTag() == null) {
      message.setReadTag(false);
    }
    if (!StringUtils.hasText(message.getSummary())
            && StringUtils.hasText(message.getContent())) {
      message.setSummary(compactSummary(message.getContent()));
    }
  }

  private String compactSummary(String content) {
    String normalized = content.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 160
            ? normalized
            : normalized.substring(0, 157) + "...";
  }

  private List<MessageVO> convertToVOList(List<Message> messages) {
    if (CollectionUtils.isEmpty(messages)) {
      return new ArrayList<>();
    }
    return messages.stream()
            .filter(Objects::nonNull)
            .map(this::convertToVO)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
  }

  private MessageVO convertToVO(Message message) {
    if (message == null) {
      return null;
    }
    MessageVO messageVO = CopyBeanUtil.copy(message, MessageVO.class);
    if (messageVO == null) {
      throw new IllegalStateException("消息视图对象转换失败");
    }
    messageVO.setStatus(Boolean.TRUE.equals(message.getReadTag())
            ? STATUS_READ
            : STATUS_UNREAD);
    messageVO.setOperationLogId(message.getOplogId());
    if (message.getCreateTime() != null) {
      messageVO.setCreateTime(message.getCreateTime().getTime());
    }
    if (message.getReadTime() != null) {
      messageVO.setReadTime(message.getReadTime().getTime());
    }
    if (!StringUtils.hasText(messageVO.getSummary())
            && StringUtils.hasText(message.getContent())) {
      messageVO.setSummary(compactSummary(message.getContent()));
    }
    return messageVO;
  }

  private List<Long> normalizeIds(List<Long> idList) {
    if (CollectionUtils.isEmpty(idList)) {
      return new ArrayList<>();
    }
    return idList.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
  }

  private record ProjectVisibility(
          List<Long> projectIds,
          boolean restrictProjects) {
  }
}
