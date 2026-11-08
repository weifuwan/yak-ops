import { Button, Input } from "@yak-ops/yak-ui";

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

export function MultiTableMapping({
  definition,
  sources,
  targetDatabase,
  onChange,
}: {
  definition: PipelineDefinition;
  sources: Array<PipelineTable & Pick<DataSyncTablePath, "primaryKeys">>;
  targetDatabase: string;
  onChange: (definition: PipelineDefinition) => void;
}) {
  return (
    <ContentSection id="target" title="目标映射">
      {!sources.length ? (
        <div className="rounded-sm border border-dashed border-[var(--yak-components-panel-border)] px-4 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
          请先添加来源表
        </div>
      ) : (
        <div className="overflow-x-auto rounded-sm border border-[var(--yak-components-panel-border)]">
          <div className="min-w-[1160px]">
            <div className="grid grid-cols-[220px_180px_180px_180px_220px_220px_72px] bg-[var(--yak-color-hover)] text-xs font-medium">
              <div className="px-3 py-2">来源表</div>
              <div className="px-3 py-2">目标表</div>
              <div className="px-3 py-2">目标同步主键</div>
              <div className="px-3 py-2">快照切分列</div>
              <div className="px-3 py-2">字段映射</div>
              <div className="px-3 py-2">数据过滤</div>
              <div className="px-3 py-2 text-center">操作</div>
            </div>

            {sources.map((source) => {
              const sourcePath = source.database + "." + source.table;
              const primaryKeysLoaded = source.primaryKeys !== undefined;
              const inheritedPrimaryKeys = source.primaryKeys?.join(",") || "";
              const primaryKeyOverride = transformValue(
                definition,
                source,
                "primary-keys",
              );

              return (
                <div
                  key={sourcePath}
                  className="grid grid-cols-[220px_180px_180px_180px_220px_220px_72px] items-center border-t border-[var(--yak-components-panel-border)] text-xs"
                >
                  <div className="min-w-0 truncate px-3 py-2" title={sourcePath}>
                    {sourcePath}
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={source.table + " 目标表"}
                      value={targetTableName(definition, source)}
                      placeholder={source.table}
                      disabled={!targetDatabase}
                      onChange={(event) =>
                        onChange(
                          setPipelineTargetTableName(
                            definition,
                            source,
                            targetDatabase,
                            event.target.value,
                          ),
                        )
                      }
                    />
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={source.table + " 目标同步主键"}
                      value={primaryKeyOverride || inheritedPrimaryKeys}
                      placeholder={
                        !primaryKeysLoaded
                          ? "加载中"
                          : inheritedPrimaryKeys
                            ? undefined
                            : "可选"
                      }
                      onChange={(event) => {
                        const next = event.target.value;
                        onChange(
                          setPipelineTransformValue(
                            definition,
                            source,
                            "primary-keys",
                            next.trim() === inheritedPrimaryKeys ? "" : next,
                          ),
                        );
                      }}
                    />
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={source.table + " 快照切分列"}
                      value={chunkKeyColumn(definition, source)}
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
                            event.target.value,
                          ),
                        )
                      }
                    />
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={source.table + " 字段映射"}
                      value={transformValue(definition, source, "projection")}
                      placeholder="如 *,name AS user_name"
                      onChange={(event) =>
                        onChange(
                          setPipelineTransformValue(
                            definition,
                            source,
                            "projection",
                            event.target.value,
                          ),
                        )
                      }
                    />
                  </div>

                  <div className="px-3 py-2">
                    <Input
                      size="small"
                      variant="outlined"
                      aria-label={source.table + " 数据过滤"}
                      value={transformValue(definition, source, "filter")}
                      placeholder="如 id > 0"
                      onChange={(event) =>
                        onChange(
                          setPipelineTransformValue(
                            definition,
                            source,
                            "filter",
                            event.target.value,
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
                          removePipelineSourceTable(definition, source),
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
