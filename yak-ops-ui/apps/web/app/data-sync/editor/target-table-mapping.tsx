import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Input,
  Popover,
  PopoverContent,
  PopoverTitle,
  PopoverTrigger,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Check, Copy } from "lucide-react";

import {
  listDataSourceColumns,
  type DataSourceCatalogColumn,
} from "@/service/datasource";
import {
  chunkKeyColumn,
  removePipelineSourceTable,
  setPipelineChunkKeyColumn,
  setPipelineTargetTableName,
  setPipelineTransformValue,
  targetTableName,
  transformValue,
  type PipelineDefinition,
  type PipelineTable,
} from "@/service/data-sync/native-pipeline";

import type { DataSyncTablePath } from "./table-catalog";
import { ContentSection } from "./content-layout";

export function TargetTableMapping({
  definition,
  sources,
  targetDatabase,
  onChange,
}: {
  definition: PipelineDefinition;
  sources: Array<
    PipelineTable &
      Pick<DataSyncTablePath, "primaryKeys">
  >;
  targetDatabase: string;
  onChange: (
    definition: PipelineDefinition,
  ) => void;
}) {
  const sourceDatasourceId =
    definition.source["datasource-id"];

  return (
    <ContentSection
      id="target"
      title="目标表映射"
    >
      {!sources.length ? (
        <div className="rounded-sm border border-dashed border-[var(--yak-components-panel-border)] px-4 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
          请先添加来源表
        </div>
      ) : (
        <div className="overflow-x-auto rounded-sm border border-[var(--yak-components-panel-border)]">
          <div className="min-w-[1380px]">
            <div className="grid grid-cols-[220px_150px_180px_180px_180px_250px_250px_72px] bg-[var(--yak-color-hover)] text-xs font-medium">
              <div className="px-3 py-2">
                来源表
              </div>

              <div className="px-3 py-2">
                目标数据库
              </div>

              <div className="px-3 py-2">
                目标表
              </div>

              <div className="px-3 py-2">
                目标表同步主键
              </div>

              <div className="px-3 py-2">
                快照切分列
              </div>

              <div className="px-3 py-2">
                字段映射
              </div>

              <div className="px-3 py-2">
                数据过滤
              </div>

              <div className="px-3 py-2 text-center">
                操作
              </div>
            </div>

            {sources.map((source) => {
              const sourcePath =
                source.database +
                "." +
                source.table;

              const primaryKeysLoaded =
                source.primaryKeys !== undefined;

              const inheritedPrimaryKeys =
                source.primaryKeys?.join(",") ||
                "";

              const primaryKeyOverride =
                transformValue(
                  definition,
                  source,
                  "primary-keys",
                );

              const currentChunkKey =
                chunkKeyColumn(
                  definition,
                  source,
                );

              return (
                <div
                  key={sourcePath}
                  className="grid grid-cols-[220px_150px_180px_180px_180px_250px_250px_72px] items-center border-t border-[var(--yak-components-panel-border)] text-xs"
                >
                  <div className="min-w-0 px-3 py-2">
                    <SourceTableColumnsPopover
                      datasourceId={
                        sourceDatasourceId
                      }
                      source={source}
                    />
                  </div>

                  <div
                    className="min-w-0 truncate px-3 py-2"
                    title={targetDatabase}
                  >
                    {targetDatabase || "-"}
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={
                        source.table +
                        " 目标表"
                      }
                      value={targetTableName(
                        definition,
                        source,
                      )}
                      placeholder={
                        source.table
                      }
                      disabled={
                        !targetDatabase
                      }
                      onChange={(event) =>
                        onChange(
                          setPipelineTargetTableName(
                            definition,
                            source,
                            targetDatabase,
                            event.target
                              .value,
                          ),
                        )
                      }
                    />
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={
                        source.table +
                        " 目标表同步主键"
                      }
                      value={
                        primaryKeyOverride ||
                        inheritedPrimaryKeys
                      }
                      placeholder={
                        !primaryKeysLoaded
                          ? "加载中"
                          : inheritedPrimaryKeys
                            ? undefined
                            : "可选"
                      }
                      onChange={(event) => {
                        const next =
                          event.target.value;

                        onChange(
                          setPipelineTransformValue(
                            definition,
                            source,
                            "primary-keys",
                            next.trim() ===
                              inheritedPrimaryKeys
                              ? ""
                              : next,
                          ),
                        );
                      }}
                    />
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={
                        source.table +
                        " 快照切分列"
                      }
                      value={
                        currentChunkKey
                      }
                      placeholder={
                        !primaryKeysLoaded
                          ? "加载中"
                          : inheritedPrimaryKeys
                            ? "自动使用主键"
                            : "必填"
                      }
                      onChange={(event) =>
                        onChange(
                          setPipelineChunkKeyColumn(
                            definition,
                            source,
                            event.target
                              .value,
                          ),
                        )
                      }
                    />
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={
                        source.table +
                        " 字段映射"
                      }
                      value={transformValue(
                        definition,
                        source,
                        "projection",
                      )}
                      placeholder="如 *,name AS user_name"
                      onChange={(event) =>
                        onChange(
                          setPipelineTransformValue(
                            definition,
                            source,
                            "projection",
                            event.target
                              .value,
                          ),
                        )
                      }
                    />
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={
                        source.table +
                        " 数据过滤"
                      }
                      value={transformValue(
                        definition,
                        source,
                        "filter",
                      )}
                      placeholder="如 id > 0"
                      onChange={(event) =>
                        onChange(
                          setPipelineTransformValue(
                            definition,
                            source,
                            "filter",
                            event.target
                              .value,
                          ),
                        )
                      }
                    />
                  </div>

                  <div className="px-2 py-2 text-center">
                    <Button
                      size="sm"
                      variant="ghost"
                      className="text-xs font-normal text-[var(--yak-color-danger)]"
                      onClick={() =>
                        onChange(
                          removePipelineSourceTable(
                            definition,
                            source,
                          ),
                        )
                      }
                    >
                      删除
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </ContentSection>
  );
}

function SourceTableColumnsPopover({
  datasourceId,
  source,
}: {
  datasourceId: string;
  source: PipelineTable;
}) {
  const [open, setOpen] =
    useState(false);

  const [copiedColumn, setCopiedColumn] =
    useState<string>();

  const copyTimerRef =
    useRef<ReturnType<
      typeof setTimeout
    > | null>(null);

  const columnsQuery = useQuery({
    queryKey: [
      "data-sync-source-columns",
      datasourceId,
      source.database,
      source.table,
    ],
    queryFn: ({ signal }) =>
      listDataSourceColumns(
        datasourceId,
        {
          database: source.database,
          table: source.table,
        },
        signal,
      ),
    enabled:
      open &&
      Boolean(
        datasourceId &&
          source.database &&
          source.table,
      ),
    staleTime: 60_000,
  });

  useEffect(() => {
    return () => {
      if (copyTimerRef.current) {
        clearTimeout(
          copyTimerRef.current,
        );
      }
    };
  }, []);

  const copyColumn = async (
    column: string,
  ) => {
    try {
      await navigator.clipboard.writeText(
        column,
      );

      setCopiedColumn(column);

      if (copyTimerRef.current) {
        clearTimeout(
          copyTimerRef.current,
        );
      }

      copyTimerRef.current =
        setTimeout(() => {
          setCopiedColumn(undefined);
        }, 1500);
    } catch {
      setCopiedColumn(undefined);
    }
  };

  const columns: TableColumns<DataSourceCatalogColumn> =
    [
      {
        key: "name",
        title: "字段名称",
        minWidth: 180,
        render: (_, column) => (
          <div className="flex min-w-0 items-center gap-1">
            <span
              className="min-w-0 flex-1 truncate"
              title={column.name}
            >
              {column.name}
            </span>

            <button
              type="button"
              aria-label={`复制字段 ${column.name}`}
              title="复制字段名"
              className="flex h-5 w-5 shrink-0 items-center justify-center rounded-sm border-0 bg-transparent text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]"
              onClick={(event) => {
                event.stopPropagation();
                void copyColumn(
                  column.name,
                );
              }}
            >
              {copiedColumn ===
              column.name ? (
                <Check
                  size={13}
                  className="text-[var(--yak-color-success)]"
                />
              ) : (
                <Copy size={13} />
              )}
            </button>
          </div>
        ),
      },
      {
        key: "typeName",
        title: "字段类型",
        width: 160,
        render: (_, column) => (
          <span
            className="block truncate"
            title={column.typeName || "-"}
          >
            {column.typeName || "-"}
          </span>
        ),
      },
      {
        key: "nullable",
        title: "非空",
        width: 80,
        align: "center",
        render: (_, column) => {
          if (
            column.nullable === undefined
          ) {
            return "-";
          }

          return column.nullable
            ? "否"
            : "是";
        },
      },
    ];

  const sourcePath =
    source.database +
    "." +
    source.table;

  return (
    <Popover
      open={open}
      onOpenChange={setOpen}
    >
      <PopoverTrigger
        type="button"
        className="block max-w-full cursor-pointer truncate border-0 bg-transparent p-0 text-left text-xs text-[var(--yak-color-primary)] hover:underline"
        title={sourcePath}
      >
        {sourcePath}
      </PopoverTrigger>

      <PopoverContent
        side="top"
        align="start"
        sideOffset={6}
        className="w-[520px] max-w-[calc(100vw-32px)] p-0"
      >
        <PopoverTitle className="sr-only">
          {sourcePath} 字段
        </PopoverTitle>

        <div className="flex h-9 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3">
          <span className="truncate text-xs font-medium">
            {sourcePath}
          </span>

          <span className="text-[10px] text-[var(--yak-components-muted-text)]">
            {columnsQuery.data?.length ??
              0}{" "}
            个字段
          </span>
        </div>

        <Table<DataSourceCatalogColumn>
          columns={columns}
          dataSource={
            columnsQuery.data ?? []
          }
          rowKey="name"
          size="small"
          loading={
            columnsQuery.isLoading
          }
          pagination={false}
          scroll={{ y: 260 }}
          emptyText={
            columnsQuery.isError
              ? "字段加载失败"
              : "暂无字段"
          }
        />

        {columnsQuery.isError ? (
          <div className="flex justify-end border-t border-[var(--yak-components-panel-border)] px-3 py-2">
            <Button variant="secondary"
              size="sm"
              onClick={() =>
                void columnsQuery.refetch()
              }
            >
              重新加载
            </Button>
          </div>
        ) : null}
      </PopoverContent>
    </Popover>
  );
}