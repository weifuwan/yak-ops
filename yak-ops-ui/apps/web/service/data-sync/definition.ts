import type { PageInfo } from "@/service/http/page-info";
import {
  pipelineDatabaseRoute,
  pipelineScope,
  pipelineSteps,
  pipelineSyncType,
  pipelineTableMappings,
  readPipelineDefinition,
} from "./native-pipeline";
import type {
  DataSyncTaskListRecord,
  DataSyncTaskRecord,
  TaskDefinitionRecord,
} from "./types";

/** List and detail projections are read-only. They do not produce task parameters. */
export function toSyncTaskRecord(task: TaskDefinitionRecord): DataSyncTaskRecord {
  if (task.taskType !== "DATA_SYNC") throw new Error("不是 DATA_SYNC 任务");
  const pipeline = readPipelineDefinition(task.parameters);
  const mappings = pipelineTableMappings(pipeline);
  const databaseRoute = pipelineDatabaseRoute(pipeline);
  const firstMapping = mappings[0];
  if (!databaseRoute && !firstMapping) throw new Error("缺少有效的同步路由");
  const source = databaseRoute
    ? { database: databaseRoute.sourceDatabase, table: "*" }
    : firstMapping!.source;
  const target = databaseRoute
    ? { database: databaseRoute.targetDatabase, table: "*" }
    : firstMapping!.target;
  const displayMappings = databaseRoute ? [{ source, target }] : mappings;
  return {
    id: task.id,
    workflowCode: task.workflowCode,
    name: task.name,
    remark: task.remark,
    status: task.status,
    archived: task.archived,
    definitionVersion: task.definitionVersion,
    revision: task.revision,
    publishedVersion: task.publishedVersion,
    updateBy: task.updateBy,
    createTime: task.createTime,
    updateTime: task.updateTime,
    sourceConnector: pipeline.source.type,
    sinkConnector: pipeline.sink.type,
    sourceProducesChangelog: true,
    sourceDataSourceId: pipeline.source["datasource-id"],
    targetDataSourceId: pipeline.sink["datasource-id"],
    sourceDatabase: source.database,
    sourceTable: source.table,
    targetDatabase: target.database,
    targetTable: target.table,
    scope: pipelineScope(pipeline),
    steps: pipelineSteps(pipeline),
    syncType: pipelineSyncType(pipeline),
    writeMode: "UPSERT",
    tableMappings: displayMappings.map((mapping) => ({ ...mapping, writeMode: "UPSERT" })),
  };
}

/** A bad stored definition stays visible as an unreadable row, never a default task. */
export function projectSyncTaskPage(
  page: PageInfo<TaskDefinitionRecord>,
): PageInfo<DataSyncTaskListRecord> {
  return {
    ...page,
    totalList: page.totalList.map((definition) => {
      try {
        return toSyncTaskRecord(definition);
      } catch (failure) {
        return {
          id: definition.id,
          name: definition.name,
          status: definition.status,
          archived: definition.archived,
          definitionVersion: definition.definitionVersion,
          revision: definition.revision,
          publishedVersion: definition.publishedVersion,
          updateBy: definition.updateBy,
          updateTime: definition.updateTime,
          configurationError: failure instanceof Error ? failure.message : "Pipeline 配置无法读取",
        };
      }
    }),
  };
}
