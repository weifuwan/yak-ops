import { Button } from "@yak-ops/yak-ui";
import { ArrowRight, Copy } from "lucide-react";
import type { ReactNode } from "react";
import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import type { DataSourceRecord } from "@/service/datasource";
import type { DataSyncTaskRecord } from "@/service/data-sync";
import { syncSummary } from "../solution/presentation";

function BasicInfoField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid min-w-0 grid-cols-[88px_minmax(0,1fr)] items-start gap-2 text-sm">
      <span className="text-xs leading-5 text-[var(--yak-components-muted-text)]">{label}</span>
      <div className="min-w-0 break-words text-[var(--yak-components-field-label)]">{children}</div>
    </div>
  );
}

interface Props {
  task: DataSyncTaskRecord;
  updatedBy: string;
  dataSources: { source?: DataSourceRecord; target?: DataSourceRecord };
  onCopyTaskId: () => void;
}
export function TaskSummary({ task, updatedBy, dataSources, onCopyTaskId }: Props) {
  const copyTaskId = onCopyTaskId;
  return (
    <section aria-labelledby="task-basic-info">
      <h2
        id="task-basic-info"
        className="mb-5 text-sm font-semibold text-[var(--yak-components-field-label)]"
      >
        基本信息
      </h2>
      <div className="grid grid-cols-2 gap-x-10 gap-y-5 max-lg:grid-cols-1">
        <BasicInfoField label="任务 ID">
          <div className="flex min-w-0 items-center gap-1.5">
            <span className="truncate" title={task.id}>
              {task.id}
            </span>
            <Button
              size="small"
              variant="ghost"
              className="sync-execution-icon-button h-6 w-6 shrink-0"
              aria-label="复制任务 ID"
              onClick={() => void copyTaskId()}
            >
              <Copy size={13} />
            </Button>
          </div>
        </BasicInfoField>
        <BasicInfoField label="修改人">{updatedBy}</BasicInfoField>
        <BasicInfoField label="数据源">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <DatabaseIcons dbType={dataSources.source?.dbType} width="16px" height="16px" />
            <span>{dataSources.source?.name || "-"}</span>
            <ArrowRight size={14} className="shrink-0 text-[var(--yak-components-muted-text)]" />
            <DatabaseIcons dbType={dataSources.target?.dbType} width="16px" height="16px" />
            <span>{dataSources.target?.name || "-"}</span>
          </div>
        </BasicInfoField>
        <BasicInfoField label="同步方案">
          {syncSummary(task.productType, task.steps)}
        </BasicInfoField>
        <BasicInfoField label="同步表数">{task.tableMappings.length} 张表</BasicInfoField>
        <BasicInfoField label="创建时间">{task.createTime || "-"}</BasicInfoField>
        <BasicInfoField label="更新时间">{task.updateTime || "-"}</BasicInfoField>
        <BasicInfoField label="当前版本">v{task.definitionVersion}</BasicInfoField>
        {task.remark ? <BasicInfoField label="备注">{task.remark}</BasicInfoField> : null}
      </div>
    </section>
  );
}
