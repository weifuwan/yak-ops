package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.dto.message.MessageBatchReadDTO;
import io.yak.framework.security.common.dto.message.MessagePageQueryDTO;
import io.yak.framework.security.common.dto.message.MessageReadDTO;
import io.yak.framework.security.common.vo.message.MessagePageVO;
import io.yak.framework.security.common.vo.message.MessageVO;
import io.yak.framework.security.service.MessageService;
import io.yak.framework.security.util.HttpRequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 消息中心接口。 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "消息管理接口")
@RestController
@RequestMapping("/yak-security/api/v1/message")
public class MessageController {

  private final MessageService messageService;

  public MessageController(MessageService messageService) {
    this.messageService = messageService;
  }

  /**
   * 旧版列表接口，保留兼容。
   */
  @Operation(summary = "查询当前用户消息")
  @GetMapping({"/list", "/list/{readTag}"})
  public Result<List<MessageVO>> list(
          @PathVariable(required = false) Boolean readTag,
          HttpServletRequest request) {

    return Result.success(
            messageService.getMessageListByUsernameAndReadTag(
                    HttpRequestUtil.getOperator(request),
                    readTag));
  }

  /**
   * 新版消息中心分页查询。
   *
   * <p>projectId 为空时查询当前用户全部消息；传入 projectId 时只返回
   * SYSTEM 消息与该项目消息。</p>
   */
  @Operation(summary = "分页查询当前用户消息")
  @GetMapping("/page")
  public Result<MessagePageVO> page(
          MessagePageQueryDTO queryDTO,
          HttpServletRequest request) {

    return Result.success(
            messageService.getMessagePage(
                    HttpRequestUtil.getOperator(request),
                    queryDTO));
  }

  /** 查询当前用户的单条消息详情。 */
  @Operation(summary = "查询当前用户消息详情")
  @GetMapping("/detail")
  public Result<MessageVO> detail(
          @RequestParam("id") Long messageId,
          HttpServletRequest request) {

    return Result.success(
            messageService.getMessageDetail(
                    HttpRequestUtil.getOperator(request),
                    messageId));
  }

  /**
   * 旧版切换接口，保留兼容；同时增加当前用户所有权校验。
   */
  @Operation(summary = "批量切换消息已读状态")
  @PutMapping("/switch")
  public Result<Void> switchStatus(
          @RequestBody List<Long> messageIdList,
          HttpServletRequest request) {

    messageService.changeMessageStatus(
            HttpRequestUtil.getOperator(request),
            messageIdList);
    return Result.success(null);
  }

  /** 幂等地标记单条消息为已读。 */
  @Operation(summary = "标记单条消息为已读")
  @PostMapping("/mark-read")
  public Result<Void> markRead(
          @RequestBody MessageReadDTO readDTO,
          HttpServletRequest request) {

    messageService.markMessageRead(
            HttpRequestUtil.getOperator(request),
            readDTO == null ? null : readDTO.getId());
    return Result.success(null);
  }

  /** 幂等地批量标记消息为已读。 */
  @Operation(summary = "批量标记消息为已读")
  @PostMapping("/batch-read")
  public Result<Void> batchRead(
          @RequestBody MessageBatchReadDTO readDTO,
          HttpServletRequest request) {

    messageService.markMessagesRead(
            HttpRequestUtil.getOperator(request),
            readDTO == null ? null : readDTO.getIds());
    return Result.success(null);
  }

  @Operation(summary = "查询当前用户未读消息数量")
  @GetMapping("/unread-count")
  public Result<Integer> unreadCount(HttpServletRequest request) {
    return Result.success(
            messageService.getUnreadMessageCount(
                    HttpRequestUtil.getOperator(request)));
  }
}
