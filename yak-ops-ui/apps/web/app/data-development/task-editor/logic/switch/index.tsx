import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  getWorkflowDefinition,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";
import { listProjectParameters } from "@/service/project/parameter";

import { LogicTaskEditor } from "../shared/logic-task-editor";
import {
  getSwitchVariableOptions,
  validateSwitchTaskParams,
} from "./adapter";
import { SwitchForm } from "./switch-form";

export function SwitchEditor({
  workflowCode,
  task,
  taskName,
  readOnly,
  onApplyTask,
  onDirtyChange,
  onBackToWorkflow,
}: {
  workflowCode: number;
  task: WorkflowTaskRecord;
  taskName: string;
  readOnly: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
}) {
  const workflowQuery = useQuery({
    queryKey: ["workflow-switch-variables", workflowCode],
    queryFn: () => getWorkflowDefinition(workflowCode),
    staleTime: 60_000,
  });
  const projectParametersQuery = useQuery({
    queryKey: ["workflow-switch-project-parameters", task.projectCode],
    queryFn: ({ signal }) => listProjectParameters(task.projectCode, signal),
    enabled: Number.isSafeInteger(task.projectCode) && task.projectCode > 0,
    staleTime: 60_000,
  });
  const variables = useMemo(
    () => getSwitchVariableOptions(
      workflowQuery.data?.workflowDefinition.globalParams,
      task.taskParams,
      projectParametersQuery.data?.totalList,
      workflowQuery.data?.taskDefinitionList,
      workflowQuery.data?.workflowTaskRelationList,
      task.code,
    ),
    [
      projectParametersQuery.data?.totalList,
      task.code,
      task.taskParams,
      workflowQuery.data?.taskDefinitionList,
      workflowQuery.data?.workflowTaskRelationList,
      workflowQuery.data?.workflowDefinition.globalParams,
    ],
  );

  return (
    <LogicTaskEditor
      workflowCode={workflowCode}
      task={task}
      taskName={taskName}
      taskType="SWITCH"
      readOnly={readOnly}
      onApplyTask={onApplyTask}
      onDirtyChange={onDirtyChange}
      onBackToWorkflow={onBackToWorkflow}
      validateParams={validateSwitchTaskParams}
    >
      {(formProps) => <SwitchForm {...formProps} variables={variables} />}
    </LogicTaskEditor>
  );
}
