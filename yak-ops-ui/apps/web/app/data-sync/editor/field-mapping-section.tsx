import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Input } from "@yak-ops/yak-ui";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  listDataSourceColumns,
  pageDataSourceTables,
} from "@/service/datasource";
import {
  fieldProjection,
  mappedSourcePrimaryKeys,
  readDirectProjection,
  sameNameFieldMappings,
} from "@/service/data-sync/field-mapping";
import {
  setPipelineTransformValue,
  targetTableName,
  transformValue,
  type PipelineDefinition,
  type PipelineTable,
} from "@/service/data-sync/native-pipeline";
import { ContentField, ContentSection } from "./content-layout";
import { FieldMappingTable } from "./field-mapping-table";

interface Props {
  definition: PipelineDefinition;
  source: PipelineTable & { primaryKeys?: string[] };
  targetDatabase: string;
  onChange: (definition: PipelineDefinition) => void;
}

export function FieldMappingSection({
  definition,
  source,
  targetDatabase,
  onChange,
}: Props) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const [showExpression, setShowExpression] = useState(false);
  const [mappingError, setMappingError] = useState("");

  const sourceDataSourceId = definition.source["datasource-id"];
  const targetDataSourceId = definition.sink["datasource-id"];
  const targetTable = targetTableName(definition, source);
  const ready = Boolean(
    sourceDataSourceId &&
      targetDataSourceId &&
      source.database &&
      source.table &&
      targetDatabase &&
      targetTable,
  );

  const sourceColumnsQuery = useQuery({
    queryKey: [
      "data-sync-field-mapping-source",
      currentUser?.id,
      currentWorkspace?.id,
      sourceDataSourceId,
      source.database,
      source.table,
    ],
    queryFn: ({ signal }) =>
      listDataSourceColumns(
        sourceDataSourceId,
        { database: source.database, table: source.table },
        signal,
      ),
    enabled: ready,
    retry: false,
  });

  const targetTableQuery = useQuery({
    queryKey: [
      "data-sync-field-mapping-target-table",
      currentUser?.id,
      currentWorkspace?.id,
      targetDataSourceId,
      targetDatabase,
      targetTable,
    ],
    queryFn: ({ signal }) =>
      pageDataSourceTables(
        targetDataSourceId,
        {
          database: targetDatabase,
          keyword: targetTable,
          offset: 0,
          limit: 100,
        },
        signal,
      ),
    enabled: ready,
    retry: false,
  });

  const targetExists = Boolean(
    targetTableQuery.data?.items.some((table) => table.name === targetTable),
  );

  const targetColumnsQuery = useQuery({
    queryKey: [
      "data-sync-field-mapping-target-columns",
      currentUser?.id,
      currentWorkspace?.id,
      targetDataSourceId,
      targetDatabase,
      targetTable,
    ],
    queryFn: ({ signal }) =>
      listDataSourceColumns(
        targetDataSourceId,
        { database: targetDatabase, table: targetTable },
        signal,
      ),
    enabled: ready && targetTableQuery.isSuccess && targetExists,
    retry: false,
  });

  const sourceColumns = sourceColumnsQuery.data ?? [];
  const targetColumns = targetExists ? targetColumnsQuery.data : undefined;
  const projection = transformValue(definition, source, "projection");
  const direct = useMemo(
    () => readDirectProjection(projection),
    [projection],
  );

  const fields = direct
    ? direct.all
      ? sameNameFieldMappings(sourceColumns)
      : direct.fields
    : undefined;

  const inheritedPrimaryKeys = source.primaryKeys ?? [];
  const mappedPrimaryKeys = fields
    ? mappedSourcePrimaryKeys(fields, sourceColumns)
    : undefined;
  const primaryKeyOverride = transformValue(
    definition,
    source,
    "primary-keys",
  );

  const loading =
    ready &&
    (sourceColumnsQuery.isPending ||
      targetTableQuery.isPending ||
      (targetExists && targetColumnsQuery.isPending));
  const metadataError =
    sourceColumnsQuery.isError ||
    targetTableQuery.isError ||
    (targetExists && targetColumnsQuery.isError);
  const disabled = !ready || loading || metadataError;

  const setFields = (nextFields: Parameters<typeof fieldProjection>[0]) => {
    try {
      const nextProjection = fieldProjection(nextFields);
      setMappingError("");
      onChange(
        setPipelineTransformValue(
          definition,
          source,
          "projection",
          nextProjection,
        ),
      );
    } catch (error) {
      setMappingError(error instanceof Error ? error.message : "字段映射无效");
    }
  };

  const applySameName = () => {
    const next = sameNameFieldMappings(
      sourceColumns,
      targetExists ? targetColumns : undefined,
    );
    if (!next.length) {
      setMappingError("来源表与去向表没有可同名映射的字段");
      return;
    }
    setFields(next);
  };

  const useAllFields = () => {
    setMappingError("");
    onChange(setPipelineTransformValue(definition, source, "projection", ""));
  };

  const retry = () => {
    void sourceColumnsQuery.refetch();
    void targetTableQuery.refetch();
    if (targetExists) void targetColumnsQuery.refetch();
  };

  return (
    <ContentSection id="field-mapping" title="去向字段映射">
      {!ready ? (
        <Alert>请先选择来源表并配置去向表</Alert>
      ) : metadataError ? (
        <Alert>
          字段元数据加载失败
          <Button variant="secondary" size="sm" onClick={retry}>
            重试
          </Button>
        </Alert>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <Button
              variant="primary"
              size="sm"
              isDisabled={disabled || !sourceColumns.length}
              onClick={applySameName}
            >
              同名映射
            </Button>
            <Button
              variant="secondary"
              size="sm"
              isDisabled={disabled || !sourceColumns.length}
              onClick={useAllFields}
            >
              全部字段
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setShowExpression((current) => !current)}
            >
              {showExpression ? "隐藏表达式" : "手动编辑映射关系"}
            </Button>
            <span className="ml-auto text-xs text-[var(--yak-components-muted-text)]">
              {loading
                ? "正在加载字段..."
                : targetExists
                  ? "去向表已存在"
                  : "去向表不存在，将按结构迁移创建"}
            </span>
          </div>

          {showExpression ? (
            <ContentField
              label="Projection"
              description="直接编辑 Flink CDC transform.projection；表格无法解析的复杂表达式会原样保留。"
              wide
            >
              <Input
                size="small"
                variant="outlined"
                value={projection || "*"}
                onChange={(event) =>
                  onChange(
                    setPipelineTransformValue(
                      definition,
                      source,
                      "projection",
                      event.target.value === "*" ? "" : event.target.value,
                    ),
                  )
                }
              />
            </ContentField>
          ) : null}

          <ContentField
            label="目标同步主键"
            description={
              mappedPrimaryKeys?.length
                ? "当前字段映射后的来源主键：" + mappedPrimaryKeys.join(", ")
                : inheritedPrimaryKeys.length
                  ? "默认使用来源主键：" + inheritedPrimaryKeys.join(", ")
                  : "来源表没有主键，可按目标表需要手动配置"
            }
          >
            <Input
              size="small"
              variant="outlined"
              value={primaryKeyOverride}
              placeholder={
                mappedPrimaryKeys?.join(",") ||
                inheritedPrimaryKeys.join(",") ||
                "可选"
              }
              onChange={(event) =>
                onChange(
                  setPipelineTransformValue(
                    definition,
                    source,
                    "primary-keys",
                    event.target.value,
                  ),
                )
              }
            />
          </ContentField>

          {direct && fields ? (
            <FieldMappingTable
              fields={fields}
              sourceColumns={sourceColumns}
              targetColumns={targetColumns}
              disabled={disabled}
              onChange={setFields}
            />
          ) : (
            <Alert>
              当前 Projection 是复杂表达式，表格模式不会改写它；请使用“手动编辑映射关系”继续编辑，
              或点击“同名映射”替换为可视化字段映射。
            </Alert>
          )}

          {mappingError ? <Alert>{mappingError}</Alert> : null}
        </>
      )}
    </ContentSection>
  );
}
