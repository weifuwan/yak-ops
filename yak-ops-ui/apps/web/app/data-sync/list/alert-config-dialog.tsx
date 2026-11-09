import { Button, Modal } from "@yak-ops/yak-ui";
import { useState } from "react";

import type { DataSyncTaskRecord } from "@/service/data-sync";
import type { WorkflowAlertOptions } from "@/service/workflow/execution";
import { WorkflowAlertSettings } from "@/app/workflow/alert-settings";

export function DataSyncAlertConfigDialog({
  task,
  value,
  onClose,
  onSave,
}: {
  task: DataSyncTaskRecord;
  value: WorkflowAlertOptions;
  onClose: () => void;
  onSave: (value: WorkflowAlertOptions) => void;
}) {
  const [draft, setDraft] = useState<WorkflowAlertOptions>(value);
  const [valid, setValid] = useState(true);

  return (
    <Modal
      open
      centered
      width={600}
      title="告警配置"
      onClose={onClose}
      footer={
        <>
          <Button size="sm" variant="secondary" onClick={onClose}>
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            isDisabled={!valid}
            onClick={() => onSave(draft)}
          >
            应用
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] px-3 py-2.5">
          <div className="text-sm font-medium text-[var(--yak-components-panel-text)]">
            {task.name}
          </div>
          <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
            该配置会作为当前页面会话中此任务后续运行的默认告警设置。
          </div>
        </div>

        <WorkflowAlertSettings
          value={draft}
          onChange={setDraft}
          onValidChange={setValid}
        />

        <div className="text-xs leading-5 text-[var(--yak-components-muted-text)]">
          现有后端仅在工作流启动时接收告警参数，因此这里不会修改任务定义，也不会影响已经运行中的实例。
        </div>
      </div>
    </Modal>
  );
}
