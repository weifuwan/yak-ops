import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Input } from "@yak-ops/yak-ui";

import { listDataSourceTableMetadata } from "@/service/datasource";
import {
  chunkKeyColumn,
  removePipelineSourceTable,
  setPipelineChunkKeyColumn,
  setPipelineDatasource,
  setPipelineSinkDatabase,
  setPipelineSourceTable,
  setPipelineTargetTableName,
  setPipelineTransformValue,
  sourceTables,
  targetTableName,
  transformValue,
} from "@/service/data-sync/native-pipeline";
import { catalogPath, tableKey } from "./table-catalog";
import { ContentField, ContentSection } from "./content-layout";
import { DataSourceSection } from "./data-source-section";
import type { TaskEditorProps } from "./editor-types";
import { FieldMappingSection } from "./field-mapping-section";
import { TableTransferPanel } from "./table-transfer-panel";
import { TaskEditorFrame } from "./task-editor-frame";
import { useCatalogOptions } from "./use-catalog-options";
import { useTaskEditorController } from "./use-task-editor-controller";

const ANCHORS = [
  { id: "datasource", label: "数据源" },
  { id: "source", label: "来源表" },
  { id: "target", label: "数据去向" },
  { id: "field-mapping", label: "去向字段映射" },
];

export function SingleTableTaskEditor(props: TaskEditorProps) {
  const editor = useTaskEditorController(props, "SINGLE_TABLE");
  const [catalogError, setCatalogError] = useState("");
  const [sourceTableFilter, setSourceTableFilter] = useState("");
  const [sourceTableKeyword, setSourceTableKeyword] = useState("");
  const [sourcePageNo, setSourcePageNo] = useState(1);
  const [sourcePageSize, setSourcePageSize] = useState(100);
  const [sourceChecked, setSourceChecked] = useState<string[]>([]);

  const sources = sourceTables(editor.definition);
  const sourceCatalog = useCatalogOptions(
    editor.sourceId,
    editor.sourceDatasource?.database || "",
    sourceTableKeyword,
    sourcePageNo,
    sourcePageSize,
    Boolean(editor.sourceId),
  );
  const selectedTableNames = useMemo(
    () => sources.map((source) => source.table).sort(),
    [editor.definition.source.tables],
  );
  const selectedMetadataQuery = useQuery({
    queryKey: [
      "data-sync-selected-table-metadata",
      editor.sourceId,
      editor.sourceDatasource?.database,
      selectedTableNames,
    ],
    queryFn: ({ signal }) =>
      listDataSourceTableMetadata(
        editor.sourceId,
        editor.sourceDatasource?.database || "",
        selectedTableNames,
        signal,
      ),
    enabled: Boolean(
      editor.sourceId &&
        editor.sourceDatasource?.database &&
        selectedTableNames.length,
    ),
  });

  const sourceMetadata = new Map(
    [...sourceCatalog.tables, ...(selectedMetadataQuery.data ?? [])].map(
      (table) => [table.name, table],
    ),
  );
  const catalogTables = sourceCatalog.tables.map(catalogPath);
  const selectedTables = sources.map((source) => ({
    ...source,
    primaryKeys: sourceMetadata.get(source.table)?.primaryKeys,
  }));
  const selectedTableKeys = new Set(selectedTables.map(tableKey));
  const selectedSource = selectedTables[0];

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSourceTableKeyword(sourceTableFilter.trim());
      setSourcePageNo(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [sourceTableFilter]);

  const edit = (next: Parameters<typeof editor.edit>[0]) => {
    editor.edit(next);
    setCatalogError("");
  };

  const changeSource = (value: string) => {
    setSourceTableFilter("");
    setSourceTableKeyword("");
    setSourcePageNo(1);
    setSourceChecked([]);
    edit(setPipelineDatasource(editor.definition, "source", value));
  };

  const changeSink = (value: string) => {
    let next = setPipelineDatasource(editor.definition, "sink", value);
    const target = editor.dataSources.find((item) => item.id === value);
    if (target?.database) {
      next = setPipelineSinkDatabase(next, target.database);
    }
    edit(next);
  };

  const addSelectedSource = () => {
    try {
      const selected = sourceCatalog.tables
        .filter((item) =>
          sourceChecked.includes(tableKey(catalogPath(item))),
        )
        .map((item) => {
          const location = catalogPath(item);
          return {
            database:
              location.database ||
              location.schema ||
              editor.sourceDatasource?.database ||
              "",
            table: location.table,
          };
        });

      if (!selected.length) return;
      if (selected.length !== 1) {
        throw new Error("单表同步只能选择一张来源表");
      }

      let next = setPipelineSourceTable(editor.definition, selected[0]);
      next = setPipelineSinkDatabase(
        next,
        editor.sinkDatasource?.database || "",
      );
      edit(next);
      setSourceChecked([]);
    } catch (failure) {
      setCatalogError(
        failure instanceof Error ? failure.message : "来源表无效",
      );
    }
  };

  const removeSelectedSource = () => {
    if (!selectedSource) return;
    edit(removePipelineSourceTable(editor.definition, selectedSource));
  };

  const validateSingleTable = () => {
    if (selectedTables.length !== 1) {
      throw new Error("单表同步必须且只能选择一张来源表");
    }
    const table = selectedTables[0];
    if (
      table.primaryKeys !== undefined &&
      table.primaryKeys.length === 0 &&
      !chunkKeyColumn(editor.definition, table)
    ) {
      throw new Error(
        "来源表 " + table.table + " 无主键，请配置快照切分列",
      );
    }
  };

  return (
    <TaskEditorFrame
      editor={editor}
      anchorItems={ANCHORS}
      onSave={(publish) => void editor.save(publish, validateSingleTable)}
      onPreview={() => void editor.preview(validateSingleTable)}
    >
      <DataSourceSection
        id={editor.task?.id}
        newTaskPath="/data-sync/new/single-table"
        sourceType={editor.sourceType}
        targetType={editor.sinkType}
        sourceDataSources={editor.dataSources.filter(
          (item) => item.dbType === "MYSQL",
        )}
        targetDataSources={editor.dataSources.filter(
          (item) => item.dbType === "MYSQL",
        )}
        sourceDataSourceId={editor.sourceId}
        targetDataSourceId={editor.sinkId}
        loading={editor.dataSourcesLoading}
        onRefresh={editor.loadDataSources}
        onSourceChange={changeSource}
        onTargetChange={changeSink}
      />

      <ContentSection id="source" title="来源表">
        <div className="grid grid-cols-1 items-center gap-3 lg:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)]">
          <TableTransferPanel
            title="来源库表"
            tables={catalogTables}
            checked={sourceChecked}
            onCheckedChange={(keys) =>
              setSourceChecked(keys.length ? [keys[keys.length - 1]] : [])
            }
            filter={sourceTableFilter}
            onFilterChange={setSourceTableFilter}
            loading={sourceCatalog.loading}
            pageNo={sourcePageNo}
            pageSize={sourcePageSize}
            total={sourceCatalog.total}
            onPageChange={(pageNo, pageSize) => {
              setSourcePageNo(pageNo);
              setSourcePageSize(pageSize);
              setSourceChecked([]);
            }}
            onRefresh={sourceCatalog.refresh}
            disabled={(table) => selectedTableKeys.has(tableKey(table))}
          />
          <Button
            variant="secondary"
            size="sm"
            isDisabled={!sourceChecked.length || !editor.sourceId}
            onClick={addSelectedSource}
          >
            添加 →
          </Button>
          <TableTransferPanel
            title="已选来源表"
            tables={selectedTables}
            checked={selectedTables.map(tableKey)}
            onCheckedChange={() => undefined}
            disabled={() => true}
            hideFilters
            actions={
              <Button
                variant="ghost"
                size="sm"
                className="text-[var(--yak-color-danger)]"
                isDisabled={!selectedSource}
                onClick={removeSelectedSource}
              >
                移除
              </Button>
            }
          />
        </div>

        {sourceCatalog.error ||
        selectedMetadataQuery.isError ||
        catalogError ? (
          <Alert>
            {sourceCatalog.error ||
              (selectedMetadataQuery.isError ? "主键元数据加载失败" : "") ||
              catalogError}
            <Button
              variant="secondary"
              size="sm"
              onClick={sourceCatalog.refresh}
            >
              刷新
            </Button>
          </Alert>
        ) : null}

        {selectedSource ? (
          <>
            <ContentField
              label="快照切分列"
              description={
                selectedSource.primaryKeys?.length
                  ? "有主键时默认使用主键；无主键表必须显式配置切分列"
                  : "来源表无主键时必填"
              }
            >
              <Input
                size="small"
                variant="outlined"
                value={chunkKeyColumn(editor.definition, selectedSource)}
                placeholder={
                  selectedSource.primaryKeys?.length
                    ? "自动使用主键"
                    : "请输入切分列"
                }
                onChange={(event) =>
                  edit(
                    setPipelineChunkKeyColumn(
                      editor.definition,
                      selectedSource,
                      event.target.value,
                    ),
                  )
                }
              />
            </ContentField>

            <ContentField
              label="数据过滤"
              description="填写 Flink CDC Transform filter 表达式"
              wide
            >
              <Input
                size="small"
                variant="outlined"
                value={transformValue(
                  editor.definition,
                  selectedSource,
                  "filter",
                )}
                placeholder="如 id > 0"
                onChange={(event) =>
                  edit(
                    setPipelineTransformValue(
                      editor.definition,
                      selectedSource,
                      "filter",
                      event.target.value,
                    ),
                  )
                }
              />
            </ContentField>
          </>
        ) : null}
      </ContentSection>

      <ContentSection id="target" title="数据去向">
        {selectedSource ? (
          <>
            <ContentField label="目标数据库" required>
              <Input
                size="small"
                variant="outlined"
                value={editor.sinkDatasource?.database || ""}
                placeholder="请选择去向数据源"
                readOnly
              />
            </ContentField>
            <ContentField
              label="目标表"
              required
              description="默认与来源表同名；可以填写已存在表，也可以填写结构迁移将创建的新表"
            >
              <Input
                size="small"
                variant="outlined"
                value={targetTableName(editor.definition, selectedSource)}
                placeholder={selectedSource.table}
                onChange={(event) =>
                  edit(
                    setPipelineTargetTableName(
                      editor.definition,
                      selectedSource,
                      editor.sinkDatasource?.database || "",
                      event.target.value,
                    ),
                  )
                }
              />
            </ContentField>
          </>
        ) : (
          <Alert>请先选择来源表</Alert>
        )}
      </ContentSection>

      {selectedSource ? (
        <FieldMappingSection
          definition={editor.definition}
          source={selectedSource}
          targetDatabase={editor.sinkDatasource?.database || ""}
          onChange={edit}
        />
      ) : (
        <ContentSection id="field-mapping" title="去向字段映射">
          <Alert>请先选择来源表并配置去向表</Alert>
        </ContentSection>
      )}
    </TaskEditorFrame>
  );
}
