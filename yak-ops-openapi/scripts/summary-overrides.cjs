/**
 * Human-written summary/description overrides, keyed by operationId.
 *
 * These replace the derived (camelCase-split) summaries at generation time.
 * Keeping them here — instead of editing @Operation on upstream-born
 * controllers — keeps the fork's merge surface with upstream at zero for
 * contract-quality improvements.
 *
 * Priority in to-openapi-31.cjs: override > controller annotation > derived fallback.
 */
module.exports = {
  // ---- data-sync (一期闭环域) ----
  overview: { summary: "查询数据同步概览", description: "返回项目的同步任务数、执行统计与健康度概览" },
  executionStats: { summary: "查询数据同步执行统计", description: "按日期区间统计同步任务执行情况（开始/成功/失败数量）" },
  listTasks: { summary: "查询数据同步任务列表", description: "分页查询任务定义，支持名称关键字、发布状态与范围过滤" },
  getTask: { summary: "查询数据同步任务详情", description: "返回任务定义、parameters 参数模板、revision（乐观锁）与 definitionVersion" },
  preview: { summary: "预览数据同步任务 YAML", description: "校验 pipeline 参数并渲染生成的 YAML，不产生任何写入" },
  createTask: { summary: "创建数据同步任务", description: "创建新任务，默认离线（OFFLINE），发布后才可执行" },
  updateTask: { summary: "修改数据同步任务", description: "更新任务定义，需携带 expectedRevision 乐观锁" },
  publish: { summary: "发布数据同步任务", description: "将指定版本上线，使其可被调度执行" },
  unpublish: { summary: "下线数据同步任务", description: "将任务下线，停止新的调度执行" },
  deleteTask: { summary: "删除数据同步任务", description: "永久删除任务，不可恢复；仅停用请使用下线" },
  version: { summary: "查询任务历史版本", description: "返回指定版本的任务定义快照" },
  instances: { summary: "查询数据同步执行实例列表", description: "分页查询执行实例，支持状态与任务编码过滤，最新在前" },
  latest: { summary: "查询任务最新执行实例", description: "批量查询指定任务的最新执行实例，可选仅活跃状态" },
  getInstance: { summary: "查询执行实例详情", description: "返回实例状态、起止时间、耗时与关联工作流实例" },
  getInstanceDefinition: { summary: "查询实例对应的任务定义", description: "返回该实例执行时使用的任务定义快照" },

  // ---- task-instance / log (白名单涉及的实例与日志操作) ----
  stopTask: { summary: "停止任务实例", description: "停止正在运行的实例" },
  forceTaskSuccess: { summary: "强制标记任务实例成功", description: "将失败的实例标记为成功，仅用于确认数据无误的场景" },
  queryLog: { summary: "查询任务实例执行日志", description: "按行读取任务实例日志，支持跳过行数与条数控制" },
};
