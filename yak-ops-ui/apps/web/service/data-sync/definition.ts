import type { PageInfo } from "@/service/http/page-info";
import {
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
  const { source, target } = mappings[0];
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
    productType: "DATABASE_REPLICATION",
    steps: ["SCHEMA", "FULL", "INCREMENTAL"],
    syncType: "REALTIME",
    writeMode: "UPSERT",
    tableMappings: mappings.map((mapping) => ({ ...mapping, writeMode: "UPSERT" })),
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
