import { Badge, Button, PageHeader } from "@yak-ops/yak-ui";
import type { DataSyncTaskRecord } from "@/service/data-sync";

export function EditorHeader({
  name,
  locked,
  saving,
  onBack,
  onProperties,
  onAdvanced,
  onVersions,
}: {
  name: string;
  locked: boolean;
  saving: boolean;
  onBack: () => void;
  onProperties: () => void;
  onAdvanced: () => void;
  onVersions: () => void;
}) {
  return (
    <PageHeader
      title={
        <span className="flex min-w-0 items-center gap-2 text-base">
          <Button size="small" variant="ghost" onClick={onBack}>
            任务列表
          </Button>
          <span className="text-[var(--yak-components-muted-text)]">/</span>
          <span className="truncate">{name || "新建同步任务"}</span>
        </span>
      }
      bordered
      className="shrink-0 bg-[var(--yak-components-panel-bg)] px-6 max-md:px-4"
      extra={
        <>
          <Button size="small" disabled={locked} onClick={onProperties}>
            属性
          </Button>
          <Button size="small" disabled={locked} onClick={onAdvanced}>
            高级配置
          </Button>
          <Button size="small" disabled={saving} onClick={onVersions}>
            版本
          </Button>
        </>
      }
    />
  );
}

export function EditorFooter({
  task,
  dirty,
  locked,
  saving,
  onSave,
  onPublish,
}: {
  task?: DataSyncTaskRecord;
  dirty: boolean;
  locked: boolean;
  saving: boolean;
  onSave: () => void;
  onPublish: () => void;
}) {
  return (
    <footer className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-6 py-3 max-md:px-4">
      <div className="flex items-center gap-2">
        <Button size="small" loading={saving} disabled={locked || !dirty} onClick={onSave}>
          保存
        </Button>
        <Button
          size="small"
          variant="primary"
          loading={saving}
          disabled={
            locked || (!dirty && task?.status === "PUBLISHED" && !task.hasUnpublishedChanges)
          }
          onClick={onPublish}
        >
          发布
        </Button>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
        {dirty ? <Badge tone="warning">未保存</Badge> : <span>已保存</span>}
        {task ? (
          <>
            <span>最新 V{task.definitionVersion}</span>
            <span>{task.publishedVersion ? `已发布 V${task.publishedVersion}` : "未发布"}</span>
            {task.hasUnpublishedChanges ? <Badge tone="warning">待发布</Badge> : null}
          </>
        ) : null}
      </div>
    </footer>
  );
}
