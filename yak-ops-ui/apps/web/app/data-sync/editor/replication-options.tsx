import { Alert, Button, Field, FieldLabel, Input } from "@yak-ops/yak-ui";
import type { DataSourceCatalogColumn } from "@/service/datasource";
import type { DataSyncTableMapping } from "@/service/data-sync";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { tableKey } from "./table-mapping-editor";

export interface ReplicationMetadata {
  columns: Map<string, DataSourceCatalogColumn[] | undefined>;
  loading: boolean;
  error: string;
  refresh: () => void;
}

/** Physical column checks guide configuration; publication validates actual sink constraints. */
export function replicationConfigurationError(
  mappings: DataSyncTableMapping[],
  metadata: ReplicationMetadata,
  initialLoad: boolean,
  continuous: boolean,
  evolution: boolean,
  targetType: string,
): string {
  if (metadata.error) return metadata.error;
  if (metadata.loading) return "正在读取来源字段";
  for (const mapping of mappings) {
    const columns = metadata.columns.get(tableKey(mapping.source));
    if (!columns?.length) return "请加载所选来源表的字段";
    const hasPrimaryKey = columns.some((column) => column.primaryKey === true);
    if (
      mapping.writeMode === "RETRACT" &&
      (mappings.length !== 1 ||
        targetType !== "MYSQL" ||
        !continuous ||
        evolution ||
        mapping.target.primaryKeys?.length)
    )
      return "逐行更新仅支持单张 MySQL 目标表的持续同步，需关闭结构变更且不设置目标主键";
    if (hasPrimaryKey) continue;
    if (!initialLoad)
      return `${mapping.source.table} 没有物理主键，不支持仅增量；请选择包含全量的策略`;
    if (mappings.length > 1)
      return `${mapping.source.table} 没有物理主键，请单独选择该表配置行标识`;
    if (evolution) return "无主键来源表需关闭同步结构变更";
    const chunk = columns.find((column) => column.name === mapping.source.chunkKeyColumn);
    if (
      !chunk ||
      chunk.nullable !== false ||
      !/^(tinyint|smallint|int(?:eger)?|bigint|decimal|numeric)(\b|\()/i.test(chunk.typeName || "")
    )
      return "无主键来源表需选择非空数值字段作为快照分片字段";
    const keys = mapping.target.primaryKeys;
    if (
      mapping.writeMode === "UPSERT" &&
      (!keys?.length || keys.some((key) => !key.trim()) || new Set(keys).size !== keys.length)
    )
      return "按主键更新需明确目标主键；目标唯一性和非空约束将在上线时验证";
  }
  return "";
}

export function ReplicationOptions({
  mappings,
  metadata,
  initialLoad,
  onChange,
}: {
  mappings: DataSyncTableMapping[];
  metadata: ReplicationMetadata;
  initialLoad: boolean;
  onChange: (mappings: DataSyncTableMapping[]) => void;
}) {
  const mapping = mappings.length === 1 ? mappings[0] : undefined;
  const columns = mapping ? metadata.columns.get(tableKey(mapping.source)) : undefined;
  const keyless = columns?.length && !columns.some((column) => column.primaryKey === true);
  if (metadata.error)
    return (
      <Alert>
        {metadata.error}
        <Button size="small" onClick={metadata.refresh}>
          重试
        </Button>
      </Alert>
    );
  if (!mapping || !columns || !keyless) return null;
  const patch = (value: DataSyncTableMapping) => onChange([value]);
  return (
    <div className="mt-3 space-y-3 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
      <Alert>
        来源表没有物理主键。请明确快照分片字段和写入方式；逐行更新仅支持单表，使用检查点事务提交。
      </Alert>
      {initialLoad ? (
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>快照分片字段</FieldLabel>
          <DataSyncSearchableSelect
            ariaLabel="快照分片字段"
            value={mapping.source.chunkKeyColumn || null}
            placeholder="选择非空数值字段"
            options={columns
              .filter(
                (column) =>
                  column.nullable === false &&
                  /^(tinyint|smallint|int(?:eger)?|bigint|decimal|numeric)(\b|\()/i.test(
                    column.typeName || "",
                  ),
              )
              .map((column) => ({ value: column.name, label: column.name }))}
            onValueChange={(value) =>
              patch({ ...mapping, source: { ...mapping.source, chunkKeyColumn: value } })
            }
          />
        </Field>
      ) : null}
      {mapping.writeMode === "UPSERT" ? (
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>目标主键</FieldLabel>
          <Input
            aria-label="目标主键"
            size="small"
            variant="outlined"
            placeholder="字段名，多个字段用逗号分隔"
            value={(mapping.target.primaryKeys ?? []).join(", ")}
            onChange={(event) =>
              patch({
                ...mapping,
                target: {
                  ...mapping.target,
                  primaryKeys: event.target.value
                    ? event.target.value.split(",").map((key) => key.trim())
                    : undefined,
                },
              })
            }
          />
        </Field>
      ) : null}
    </div>
  );
}
