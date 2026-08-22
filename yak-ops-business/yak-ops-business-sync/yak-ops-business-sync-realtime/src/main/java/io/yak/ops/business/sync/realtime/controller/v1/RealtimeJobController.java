package io.yak.ops.business.sync.realtime.controller.v1;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.sync.realtime.controller.RealtimePermissionCode;
import io.yak.ops.business.sync.realtime.controller.v1.dto.RealtimeJobRequests;
import io.yak.ops.business.sync.realtime.controller.v1.mapper.RealtimeRequestMapper;
import io.yak.ops.business.sync.realtime.controller.v1.mapper.RealtimeViewMapper;
import io.yak.ops.business.sync.realtime.controller.v1.vo.RealtimeViews;
import io.yak.ops.business.sync.realtime.service.RealtimeEventStreamService;
import io.yak.ops.business.sync.realtime.service.RealtimeJobLifecycleCoordinator;
import io.yak.ops.business.sync.realtime.service.RealtimeJobQueryService;
import io.yak.ops.business.sync.realtime.service.RealtimeJobService;
import io.yak.ops.business.sync.realtime.service.RealtimeObservabilityService;
import io.yak.ops.business.sync.realtime.service.RealtimeValidationService;
import io.yak.ops.business.sync.realtime.service.RealtimeYamlCodec;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "实时同步")
@RestController
@RequestMapping("/api/v1/realtime-sync")
@RequiresPermission(RealtimePermissionCode.READ)
public class RealtimeJobController {
  private final RealtimeJobService service;
  private final RealtimeValidationService validationService;
  private final RealtimeJobLifecycleCoordinator lifecycleCoordinator;
  private final RealtimeObservabilityService observabilityService;
  private final RealtimeJobQueryService queryService;
  private final RealtimeEventStreamService eventStream;
  private final RealtimeRequestMapper requestMapper;
  private final RealtimeViewMapper viewMapper;
  private final RealtimeYamlCodec yamlCodec;

  public RealtimeJobController(
      RealtimeJobService service,
      RealtimeValidationService validationService,
      RealtimeJobLifecycleCoordinator lifecycleCoordinator,
      RealtimeObservabilityService observabilityService,
      RealtimeJobQueryService queryService,
      RealtimeEventStreamService eventStream,
      RealtimeRequestMapper requestMapper,
      RealtimeViewMapper viewMapper,
      RealtimeYamlCodec yamlCodec) {
    this.service = service;
    this.validationService = validationService;
    this.lifecycleCoordinator = lifecycleCoordinator;
    this.observabilityService = observabilityService;
    this.queryService = queryService;
    this.eventStream = eventStream;
    this.requestMapper = requestMapper;
    this.viewMapper = viewMapper;
    this.yamlCodec = yamlCodec;
  }

  @Operation(summary = "创建实时同步基础任务") @PostMapping @RequiresPermission(RealtimePermissionCode.CREATE)
  public Result<Long> create(@Valid @RequestBody RealtimeJobRequests.CreateRequest request) { return Result.success(service.create(request.name(), request.description(), request.runtimeEnvironmentId())); }

  @Operation(summary = "新建实时同步草稿") @PostMapping("/draft") @RequiresPermission(RealtimePermissionCode.CREATE)
  public Result<Long> draft(@Valid @RequestBody RealtimeJobRequests.SaveRequest request) { return Result.success(service.save(null, request.name(), request.description(), requestMapper.toSpec(request.spec()), request.runtimeEnvironmentId())); }

  @Operation(summary = "解析 Yak Realtime YAML") @PostMapping("/yaml/parse")
  public Result<RealtimeViews.PipelineSpec> parseYaml(@Valid @RequestBody RealtimeJobRequests.YamlRequest request) {
    return Result.success(viewMapper.toView(yamlCodec.parse(request.yaml())));
  }

  @Operation(summary = "将实时同步 Spec 渲染为 Yak Realtime YAML") @PostMapping("/yaml/render")
  public Result<Map<String, String>> renderYaml(@Valid @RequestBody RealtimeJobRequests.YamlRenderRequest request) {
    return Result.success(Map.of("yaml", yamlCodec.render(requestMapper.toSpec(request.spec()))));
  }

  @Operation(summary = "保存实时同步草稿") @PutMapping("/{id}") @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Long> save(@PathVariable long id, @Valid @RequestBody RealtimeJobRequests.SaveRequest request) { return Result.success(service.save(id, request.name(), request.description(), requestMapper.toSpec(request.spec()), request.runtimeEnvironmentId())); }

  @Operation(summary = "实时同步任务详情") @GetMapping("/{id}")
  public Result<RealtimeViews.Job> detail(@PathVariable long id) { return Result.success(viewMapper.toView(service.get(id))); }

  @Operation(summary = "实时同步任务分页") @GetMapping
  public Result<RealtimeViews.Page> page(@RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String keyword, @RequestParam(required = false) Long id, @RequestParam(required = false) String releaseState, @RequestParam(required = false) String stateGroup) { return Result.success(viewMapper.toView(queryService.page(pageNo, pageSize, keyword, id, releaseState, stateGroup))); }

  @Operation(summary = "订阅实时同步任务状态") @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream() { return eventStream.subscribe(); }

  @Operation(summary = "发布当前定义版本") @PostMapping("/{id}/publish") @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Boolean> publish(@PathVariable long id) { service.publish(id); return Result.success(true); }

  @Operation(summary = "使用任务绑定的 Flink CDC 运行环境校验当前定义") @PostMapping("/{id}/validate") @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<RealtimeViews.Validation> validate(@PathVariable long id) { return Result.success(viewMapper.toView(validationService.validate(id))); }

  @Operation(summary = "启动实时同步任务") @PostMapping("/{id}/start") @RequiresPermission(RealtimePermissionCode.EXECUTE)
  public Result<RealtimeViews.Deployment> start(@PathVariable long id, @RequestHeader(value = "Idempotency-Key", required = false) String key) { return Result.success(viewMapper.toView(service.start(id, key))); }

  @Operation(summary = "停止实时同步任务") @PostMapping("/{id}/stop") @RequiresPermission(RealtimePermissionCode.EXECUTE)
  public Result<Boolean> stop(@PathVariable long id) { service.stop(id); return Result.success(true); }

  @Operation(summary = "重启实时同步任务") @PostMapping("/{id}/restart") @RequiresPermission(RealtimePermissionCode.EXECUTE)
  public Result<RealtimeViews.Deployment> restart(@PathVariable long id, @RequestHeader(value = "Idempotency-Key", required = false) String key) { return Result.success(viewMapper.toView(service.restart(id, key))); }

  @Operation(summary = "立即对账实时同步任务") @PostMapping("/{id}/reconcile") @RequiresPermission(RealtimePermissionCode.EXECUTE)
  public Result<RealtimeViews.Job> reconcile(@PathVariable long id) { return Result.success(viewMapper.toView(lifecycleCoordinator.reconcile(id))); }

  @Operation(summary = "删除已停止的实时同步任务") @DeleteMapping("/{id}") @RequiresPermission(RealtimePermissionCode.DELETE)
  public Result<Boolean> delete(@PathVariable long id) { lifecycleCoordinator.assertSafeToDelete(id); service.delete(id); return Result.success(true); }

  @Operation(summary = "查询任务状态事件") @GetMapping("/{id}/events")
  public Result<List<RealtimeViews.Event>> events(@PathVariable long id) { return Result.success(service.events(id).stream().map(viewMapper::toView).toList()); }

  @Operation(summary = "查询指定 Flink CDC 运行环境能力") @GetMapping("/runtime/capabilities")
  public Result<JsonNode> capabilities(@RequestParam long environmentId) { return Result.success(service.capabilities(environmentId)); }

  @Operation(summary = "查询归一化运行概览、Checkpoint 和 Metrics") @GetMapping("/{id}/observability")
  public Result<RealtimeViews.Observability> observability(@PathVariable long id) { return Result.success(viewMapper.toView(observabilityService.snapshot(id))); }

  @Operation(summary = "查询 Flink CDC 提交日志") @GetMapping("/{id}/logs/submission")
  public Result<Map<String, String>> submissionLog(@PathVariable long id, @RequestParam(defaultValue = "500") int tail) { return Result.success(Map.of("logs", observabilityService.submissionLog(id, tail))); }

  @Operation(summary = "查询 Flink 运行异常历史") @GetMapping("/{id}/logs/runtime")
  public Result<RealtimeViews.RuntimeLog> runtimeLog(@PathVariable long id, @RequestParam(defaultValue = "50") int maxExceptions) { return Result.success(viewMapper.toView(observabilityService.runtimeLog(id, maxExceptions))); }
}
