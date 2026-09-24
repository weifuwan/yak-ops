package io.yak.framework.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.common.constant.Constants;
import io.yak.framework.security.common.dto.oplog.OplogQueryDTO;
import io.yak.framework.security.common.vo.oplog.OplogOptionsVO;
import io.yak.framework.security.common.vo.oplog.OplogVO;
import io.yak.framework.security.service.OplogService;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志管理接口。
 *
 * @author weifuwan
 */
@Tag(name = Constants.SWAGGER_API_TAG_PREFIX + "操作日志管理接口")
@RestController
@RequestMapping("/yak-security/api/v1/oplog")
public class OplogController {

  private final OplogService oplogService;

  public OplogController(OplogService oplogService) {
    this.oplogService = oplogService;
  }

  /** 分页查询操作日志。 */
  @Operation(summary = "分页查询操作日志")
  @PostMapping("/page")
  public Result<PagingData<OplogVO>> page(
          @RequestBody(required = false)
                  OplogQueryDTO queryDTO) {

    PagingData<OplogVO> pagingData =
            oplogService.getOplogPage(queryDTO);
    return Result.success(pagingData);
  }

  /** 查询操作日志筛选选项。 */
  @Operation(summary = "查询操作日志筛选选项")
  @GetMapping("/options")
  public Result<OplogOptionsVO> options() {
    return Result.success(oplogService.getOptions());
  }

  /** 根据操作日志 ID 查询日志详情。 */
  @Operation(summary = "根据操作日志 ID 查询日志详情")
  @GetMapping("/{id}")
  public Result<OplogVO> detail(
          @PathVariable("id") Long oplogId) {

    return Result.success(
            oplogService.getOplogDetailByOplogId(oplogId));
  }

  /** 查询全部操作目标类型。 */
  @Operation(summary = "查询全部操作目标类型")
  @GetMapping("/type/list")
  public Result<List<String>> targetTypeList() {
    return Result.success(oplogService.listTargetType());
  }
}
