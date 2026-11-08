import { Alert, Button, PageHeader } from "@yak-ops/yak-ui";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { TaskDefinitionRecord } from "@/service/data-sync";

export function ReadonlyTaskEditor({
  definition,
  error,
}: {
  definition: TaskDefinitionRecord;
  error: string;
}) {
  const navigate = useNavigate();
  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader
        title={
          <span className="flex items-center gap-2">
            <Button size="sm" variant="ghost" onClick={() => navigate("/data-sync")}>
              <ArrowLeft aria-hidden="true" size={14} />
              任务列表
            </Button>
            <span>/ {definition.name}</span>
          </span>
        }
        bordered
        className="shrink-0 bg-[var(--yak-components-panel-bg)] px-6"
      />
      <main className="min-h-0 flex-1 space-y-4 overflow-auto p-6">
        <Alert>{error}，当前任务仅支持查看。</Alert>
        <pre className="overflow-auto rounded border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4 text-xs">
          {JSON.stringify(definition.parameters, null, 2)}
        </pre>
      </main>
      <footer className="flex shrink-0 gap-2 border-t border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-6 py-3">
        <Button variant="secondary" size="sm" isDisabled>
          保存
        </Button>
        <Button size="sm" variant="primary" isDisabled>
          发布
        </Button>
      </footer>
    </div>
  );
}
