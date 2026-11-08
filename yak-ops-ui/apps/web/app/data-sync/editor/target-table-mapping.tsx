import { Button, Input } from "@yak-ops/yak-ui";
import type { PipelineTable } from "@/service/data-sync/native-pipeline";
import { ContentSection } from "./content-layout";

export function TargetTableMapping({
  sources,
  targetDatabase,
  targetTableName,
  onTargetTableChange,
  onRemove,
}: {
  sources: PipelineTable[];
  targetDatabase: string;
  targetTableName: (source: PipelineTable) => string;
  onTargetTableChange: (source: PipelineTable, table: string) => void;
  onRemove: (source: PipelineTable) => void;
}) {
  return (
    <ContentSection id="target" title="目标表映射">
      {!sources.length ? (
        <div className="rounded-sm border border-dashed border-[var(--yak-components-panel-border)] px-4 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
          请先添加来源表
        </div>
      ) : (
        <div className="overflow-hidden rounded-sm border border-[var(--yak-components-panel-border)]">
          <div className="grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,1.2fr)_72px] bg-[var(--yak-color-hover)] text-xs font-medium">
            <div className="px-3 py-2">来源表</div>
            <div className="px-3 py-2">目标数据库</div>
            <div className="px-3 py-2">目标表</div>
            <div className="px-3 py-2 text-center">操作</div>
          </div>

          {sources.map((source) => (
            <div
              key={source.database + "." + source.table}
              className="grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,1.2fr)_72px] items-center border-t border-[var(--yak-components-panel-border)] text-xs"
            >
              <div className="min-w-0 truncate px-3 py-2" title={source.database + "." + source.table}>
                {source.database + "." + source.table}
              </div>
              <div className="min-w-0 truncate px-3 py-2" title={targetDatabase}>
                {targetDatabase || "-"}
              </div>
              <div className="px-3 py-2">
                <Input
                  size="small"
                  variant="outlined"
                  aria-label={source.table + " 目标表"}
                  value={targetTableName(source)}
                  placeholder={source.table}
                  disabled={!targetDatabase}
                  onChange={(event) => onTargetTableChange(source, event.target.value)}
                />
              </div>
              <div className="px-2 py-2 text-center">
                <Button
                  size="small"
                  variant="ghost"
                  className="text-xs font-normal text-[var(--yak-color-danger)]"
                  onClick={() => onRemove(source)}
                >
                  删除
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </ContentSection>
  );
}
