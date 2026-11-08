import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Alert, Button } from "@yak-ops/yak-ui";

import {
  listDataSourceTableMetadata,
} from "@/service/datasource";
import {
  chunkKeyColumn,
  setPipelineDatasource,
  setPipelineSinkDatabase,
  setPipelineSourceTable,
  sourceTables,
} from "@/service/data-sync/native-pipeline";
import { catalogPath, tableKey } from "./table-catalog";
import { ContentSection } from "./content-layout";
import { DataSourceSection } from "./data-source-section";
import type { TaskEditorProps } from "./editor-types";
import { TableTransferPanel } from "./table-transfer-panel";
import { TargetTableMapping } from "./target-table-mapping";
import { TaskEditorFrame } from "./task-editor-frame";
import { useCatalogOptions } from "./use-catalog-options";
import { useTaskEditorController } from "./use-task-editor-controller";

const ANCHORS = [
  { id: "datasource", label: "数据源" },
  { id: "source", label: "来源表" },
  { id: "target", label: "目标表映射" },
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
        sourceDataSources={editor.dataSources.filter((item) => item.dbType === "MYSQL")}
        targetDataSources={editor.dataSources.filter((item) => item.dbType === "MYSQL")}
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
      </ContentSection>

      <TargetTableMapping
        definition={editor.definition}
        sources={selectedTables}
        targetDatabase={editor.sinkDatasource?.database || ""}
        onChange={edit}
      />
    </TaskEditorFrame>
  );
}
