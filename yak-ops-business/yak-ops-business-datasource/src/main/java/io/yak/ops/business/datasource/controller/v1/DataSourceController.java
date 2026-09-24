package io.yak.ops.business.datasource.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.connection.DataSourceConnectionTester;
import io.yak.ops.business.datasource.controller.v1.converter.DataSourceRequestConverter;
import io.yak.ops.business.datasource.controller.v1.converter.DataSourceViewConverter;
import io.yak.ops.business.datasource.management.DataSourceManager;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceOptionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceSummaryVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.constant.datasource.DataSourceConstants;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据源管理接口")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping(DataSourceConstants.API_PREFIX)
@RequiresPermission(DataSourcePermissionCode.READ)
public class DataSourceController {
  private final DataSourceManager manager;
  private final DataSourceReader reader;
  private final DataSourceConnectionTester connectionTester;
  private final DataSourceRequestConverter requestConverter;
  private final DataSourceViewConverter viewConverter;

  @Operation(summary = "新增数据源") @PostMapping @RequiresPermission(DataSourcePermissionCode.CREATE)
  public Result<Boolean> create(@Valid @RequestBody DataSourceDTO dto) { return Result.success(manager.create(requestConverter.configuration(dto))); }
  @Operation(summary = "编辑数据源") @PutMapping("/{id}") @RequiresPermission(DataSourcePermissionCode.UPDATE)
  public Result<Boolean> update(@PathVariable("id") Long id, @Valid @RequestBody DataSourceDTO dto) { return Result.success(manager.update(id, requestConverter.configuration(dto))); }
  @Operation(summary = "查询数据源详情") @GetMapping("/{id}")
  public Result<DataSourceVO> detail(@PathVariable("id") Long id) { return Result.success(viewConverter.definition(reader.require(id), true)); }
  @Operation(summary = "删除数据源") @DeleteMapping("/{id}") @RequiresPermission(DataSourcePermissionCode.DELETE)
  public Result<Boolean> delete(@PathVariable("id") Long id) { return Result.success(manager.delete(id)); }
  @Operation(summary = "分页查询数据源") @PostMapping("/page")
  public Result<PagingData<DataSourceVO>> page(@Valid @RequestBody DataSourceQueryDTO dto) { return Result.success(viewConverter.page(reader.page(requestConverter.query(dto)))); }
  @Operation(summary = "查询数据源总览统计") @GetMapping("/summary")
  public Result<DataSourceSummaryVO> summary() { return Result.success(viewConverter.summary(reader.summary())); }
  @Operation(summary = "查询全部数据源") @RequestMapping(value = "/all", method = {RequestMethod.GET, RequestMethod.POST})
  public Result<PagingData<DataSourceVO>> all() { return Result.success(viewConverter.all(reader.findAll(null))); }
  @Operation(summary = "查询数据源下拉选项") @GetMapping("/option")
  public Result<List<DataSourceOptionVO>> option(@RequestParam(value = "dbType", required = false) String dbType) { return Result.success(viewConverter.options(reader.findAll(requestConverter.optionalDbType(dbType)))); }
  @Operation(summary = "测试已保存数据源连接") @RequestMapping(value = "/{id}/connect-test", method = {RequestMethod.GET, RequestMethod.POST}) @RequiresPermission(DataSourcePermissionCode.TEST)
  public Result<Boolean> testConnection(@PathVariable("id") Long id) { return Result.success(connectionTester.testSaved(id)); }
  @Operation(summary = "使用连接参数测试数据源连接") @PostMapping("/connect-test-with-param") @RequiresPermission(DataSourcePermissionCode.TEST)
  public Result<Boolean> testConnection(@Valid @RequestBody DataSourceConnectTestDTO dto) { return Result.success(connectionTester.test(requestConverter.connectionTest(dto))); }
}
