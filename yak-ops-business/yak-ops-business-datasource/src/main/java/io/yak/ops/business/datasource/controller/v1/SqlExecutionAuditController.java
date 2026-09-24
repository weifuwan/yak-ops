package io.yak.ops.business.datasource.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.controller.v1.converter.SqlExecutionAuditConverter;
import io.yak.ops.business.datasource.execution.audit.SqlExecutionAuditReader;
import io.yak.ops.common.bean.dto.observability.SqlExecutionAuditQueryDTO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditDetailVO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditSummaryVO;
import io.yak.ops.common.bean.vo.observability.SqlExecutionAuditVO;
import io.yak.ops.common.constant.observability.SqlExecutionAuditConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "SQL 执行观测接口")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping(SqlExecutionAuditConstants.API_PREFIX)
@RequiresPermission(SqlExecutionAuditConstants.READ_PERMISSION)
public class SqlExecutionAuditController {
  private final SqlExecutionAuditReader auditReader;
  private final SqlExecutionAuditConverter auditConverter;
  @Operation(summary = "分页查询 SQL 执行历史") @PostMapping("/page")
  public Result<PagingData<SqlExecutionAuditVO>> page(@Valid @RequestBody(required = false) SqlExecutionAuditQueryDTO query) { return Result.success(auditConverter.page(auditReader.page(auditConverter.criteria(query)))); }
  @Operation(summary = "查询 SQL 执行详情") @GetMapping("/{executionId}")
  public Result<SqlExecutionAuditDetailVO> detail(@PathVariable("executionId") String executionId) { return Result.success(auditConverter.detail(auditReader.detail(executionId))); }
  @Operation(summary = "查询 SQL 执行观测汇总") @PostMapping("/summary")
  public Result<SqlExecutionAuditSummaryVO> summary(@Valid @RequestBody(required = false) SqlExecutionAuditQueryDTO query) { return Result.success(auditConverter.summary(auditReader.summary(auditConverter.criteria(query)))); }
}
