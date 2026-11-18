import { useCallback, useEffect, useMemo, useRef } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  getWorkflowDefinition,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";
import { listProjectParameters } from "@/service/project/parameter";

import { useDataDevelopmentWorkbench } from "../../../workbench-context";
import { getWorkflowBranchOutputs } from "../../../workflow-editor/adapter/workflow-branch-adapter";

import { LogicTaskEditor } from "../shared/logic-task-editor";
import { useSwitchRunConfig } from "./components/switch-run-config-panel";
import {
  getSwitchVariableOptions,
  remapSwitchCaseHandles,
  validateSwitchTaskParams,
} from "./adapter";
import { SwitchForm } from "./switch-form";

export function SwitchEditor({
  workflowCode,
  task,
  nodeId,
  taskName,
  readOnly,
  onApplyTask,
  onDirtyChange,
  onBackToWorkflow,
}: {
  workflowCode: number;
  task: WorkflowTaskRecord;
  nodeId: string;
  taskName: string;
  readOnly: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
}) {
  const runConfig = useSwitchRunConfig(task, readOnly);
  const { activeTabId, getWorkflowStore } = useDataDevelopmentWorkbench();
  const taskTabId = "task:" + workflowCode + ":" + task.code;
  const latestPreviewParamsRef = useRef<unknown>(null);

  const updateCanvasPreview = useCallback((params: unknown) => {
    const store = getWorkflowStore(workflowCode);
    if (!store) {
      latestPreviewParamsRef.current = params;
      return;
    }
    const state = store.getState();
    const node = state.nodes.find((item) => item.id === nodeId);
    if (!node) {
      latestPreviewParamsRef.current = params;
      return;
    }

    const previousParams = latestPreviewParamsRef.current ?? task.taskParams;
    const currentEdges =
      state.previewNodeId === nodeId && state.previewEdges
        ? state.previewEdges
        : state.edges;
    const includeUnassigned = (node.data.branchOutputs ?? []).some(
      (output) => output.id === "unassigned",
    );
    state.setSwitchPreview(
      nodeId,
      getWorkflowBranchOutputs("SWITCH", params, includeUnassigned),
      remapSwitchCaseHandles(nodeId, previousParams, params, currentEdges),
    );
    latestPreviewParamsRef.current = params;
  }, [getWorkflowStore, nodeId, task.taskParams, workflowCode]);

  useEffect(() => {
    // A refreshed or externally updated task is the new preview baseline.
    latestPreviewParamsRef.current = task.taskParams;
  }, [task.taskParams]);

  useEffect(() => {
    // Keep the draft preview while the workflow tab is active so the user can
    // inspect the updated ports and connections without applying the task yet.
    if (activeTabId === taskTabId && latestPreviewParamsRef.current !== null) {
      updateCanvasPreview(latestPreviewParamsRef.current);
    }
  }, [activeTabId, taskTabId, updateCanvasPreview]);

  useEffect(() => () => {
    getWorkflowStore(workflowCode)?.getState().clearSwitchPreview(nodeId);
  }, [getWorkflowStore, nodeId, workflowCode]);

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
      runConfig={runConfig}
      onApplyTask={onApplyTask}
      onDirtyChange={onDirtyChange}
      onBackToWorkflow={onBackToWorkflow}
      validateParams={validateSwitchTaskParams}
      onParamsChange={updateCanvasPreview}
    >
      {(formProps) => <SwitchForm {...formProps} variables={variables} />}
    </LogicTaskEditor>
  );
}
