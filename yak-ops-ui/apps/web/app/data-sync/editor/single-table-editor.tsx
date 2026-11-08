import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Input } from "@yak-ops/yak-ui";

import { listDataSourceTableMetadata } from "@/service/datasource";
import {
  chunkKeyColumn,
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
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ContentField, ContentSection } from "./content-layout";
import { DataSourceSection } from "./data-source-section";
import type { TaskEditorProps } from "./editor-types";
import { FieldMappingSection } from "./field-mapping-section";
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
  const [sourceTableSearch, setSourceTableSearch] = useState("");
  const [sourceTableKeyword, setSourceTableKeyword] = useState("");

  const sources = sourceTables(editor.definition);
  const selectedTableNames = useMemo(
    () => sources.map((source) => source.table),
    [editor.definition.source.tables],
  );

  const sourceCatalog = useCatalogOptions(
    editor.sourceId,
    editor.sourceDatasource?.database || "",
    sourceTableKeyword,
    1,
    100,
    Boolean(editor.sourceId),
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

  const selectedSourceBase = sources[0];
  const selectedSource = selectedSourceBase
    ? {
        ...selectedSourceBase,
        primaryKeys: sourceMetadata.get(selectedSourceBase.table)?.primaryKeys,
      }
    : undefined;

  const sourceTableOptions = useMemo(() => {
    const tables = new Map(
      [...sourceCatalog.tables, ...(selectedMetadataQuery.data ?? [])].map(
        (table) => [table.name, table],
      ),
    );
    return Array.from(tables.values()).map((table) => ({
      value: table.name,
      label: table.name,
      searchText: [table.name, table.remarks].filter(Boolean).join(" "),
    }));
  }, [sourceCatalog.tables, selectedMetadataQuery.data]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSourceTableKeyword(sourceTableSearch.trim());
    }, 300);
    return () => window.clearTimeout(timer);
  }, [sourceTableSearch]);

  const edit = (next: Parameters<typeof editor.edit>[0]) => {
    editor.edit(next);
  };

  const changeSource = (value: string) => {
    setSourceTableSearch("");
    setSourceTableKeyword("");
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

  const changeSourceTable = (tableName: string) => {
    const database = editor.sourceDatasource?.database || "";
    if (!database) return;

    let next = setPipelineSourceTable(editor.definition, {
      database,
      table: tableName,
    });
    next = setPipelineSinkDatabase(
      next,
      editor.sinkDatasource?.database || "",
    );
    edit(next);
  };

  const validateSingleTable = () => {
    if (!selectedSource) {
      throw new Error("请选择来源表");
    }
    if (
      selectedSource.primaryKeys !== undefined &&
      selectedSource.primaryKeys.length === 0 &&
      !chunkKeyColumn(editor.definition, selectedSource)
    ) {
      throw new Error(
        "来源表 " + selectedSource.table + " 无主键，请配置快照切分列",
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
        <ContentField label="表" required>
          <DataSyncSearchableSelect
            value={selectedSource?.table || null}
            options={sourceTableOptions}
            placeholder={editor.sourceId ? "请选择来源表" : "请先选择来源数据源"}
            searchPlaceholder="搜索表名"
            emptyText={sourceCatalog.loading ? "正在加载表..." : "没有匹配的表"}
            disabled={!editor.sourceId || !editor.sourceDatasource?.database}
            refreshing={sourceCatalog.loading}
            onRefresh={sourceCatalog.refresh}
            onSearchChange={setSourceTableSearch}
            onValueChange={changeSourceTable}
          />
        </ContentField>

        {sourceCatalog.error || selectedMetadataQuery.isError ? (
          <Alert>
            {sourceCatalog.error ||
              (selectedMetadataQuery.isError ? "来源表元数据加载失败" : "")}
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                sourceCatalog.refresh();
                void selectedMetadataQuery.refetch();
              }}
            >
              刷新
            </Button>
          </Alert>
        ) : null}

        {selectedSource ? (
          <>
            <ContentField label="快照切分列">
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

            <ContentField label="数据过滤" wide>
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
          <ContentField label="目标表" required>
            <Input
              size="small"
              variant="outlined"
              value={targetTableName(editor.definition, selectedSource)}
              placeholder={selectedSource.table}
              disabled={!editor.sinkDatasource?.database}
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
