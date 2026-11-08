import { Badge, Button, PageHeader } from "@yak-ops/yak-ui";
import type { TaskDefinitionRecord } from "@/service/data-sync";

export function EditorHeader({
  name, locked, onBack, onAdvanced, onProperties,
}: {
  name: string;
  locked: boolean;
  onBack: () => void;
  onAdvanced: () => void;
  onProperties: () => void;
}) {
  return (
    <PageHeader
      title={
        <span className="flex min-w-0 items-center gap-2 text-base">
          <Button size="sm" variant="ghost" onClick={onBack}>任务列表</Button>
          <span className="text-[var(--yak-components-muted-text)]">/</span>
          <span className="truncate">{name || "新建同步任务"}</span>
        </span>
      }
      bordered
      className="shrink-0 bg-[var(--yak-components-panel-bg)] px-6 max-md:px-4"
      extra={
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" isDisabled={locked} onClick={onAdvanced}>高级配置</Button>
          <Button variant="secondary" size="sm" isDisabled={locked} onClick={onProperties}>属性</Button>
        </div>
      }
    />
  );
}

export function EditorFooter({
  task, dirty, locked, saving, previewing, onSave, onPreview, onPublish,
}: {
  task?: Pick<TaskDefinitionRecord, "status">;
  dirty: boolean;
  locked: boolean;
  saving: boolean;
  previewing: boolean;
  onSave: () => void;
  onPreview: () => void;
  onPublish: () => void;
}) {
  return (
    <footer className="flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-6 py-3 max-md:px-4">
      <div className="flex items-center gap-2">
        <Button variant="secondary" size="sm" isPending={saving} isDisabled={locked || !dirty} onClick={onSave}>保存</Button>
        <Button variant="secondary" size="sm" isPending={previewing} isDisabled={saving || previewing}
          onClick={onPreview}>预览</Button>
        <Button size="sm" variant="primary" isPending={saving}
          isDisabled={locked || (!dirty && task?.status === "ONLINE")}
          onClick={onPublish}>发布</Button>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
        {dirty ? <Badge tone="warning">未保存</Badge> : <span>已保存</span>}
      </div>
    </footer>
  );
}
