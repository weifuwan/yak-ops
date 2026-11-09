import { Button, Field, FieldLabel, Input, Modal } from "@yak-ops/yak-ui";
import { useState } from "react";

import { WorkflowAlertSettings } from "@/app/workflow/alert-settings";
import type { DataSyncTaskRecord } from "@/service/data-sync";
import type {
  StartWorkflowOptions,
  WorkflowAlertOptions,
} from "@/service/workflow/execution";

export function RunDataSyncTaskDialog({
  task,
  initialAlert,
  pending,
  onClose,
  onRun,
}: {
  task: DataSyncTaskRecord;
  initialAlert: WorkflowAlertOptions;
  pending: boolean;
  onClose: () => void;
  onRun: (options: StartWorkflowOptions) => void;
}) {
  const [alert, setAlert] = useState<WorkflowAlertOptions>(initialAlert);
  const [valid, setValid] = useState(true);

  return (
    <Modal
      open
      centered
      width={600}
      title="运行同步任务"
      onClose={() => {
        if (!pending) onClose();
      }}
      footer={
        <>
          <Button size="sm" variant="secondary" isDisabled={pending} onClick={onClose}>
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            isPending={pending}
            isDisabled={!valid}
            onClick={() =>
              onRun({
                warningType: alert.warningType,
                warningGroupId:
                  alert.warningType === "NONE" ? undefined : alert.warningGroupId,
              })
            }
          >
            运行
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>任务</FieldLabel>
          <Input size="small" variant="outlined" value={task.name} disabled />
        </Field>

        <WorkflowAlertSettings
          value={alert}
          onChange={setAlert}
          onValidChange={setValid}
          disabled={pending}
        />
      </div>
    </Modal>
  );
}
