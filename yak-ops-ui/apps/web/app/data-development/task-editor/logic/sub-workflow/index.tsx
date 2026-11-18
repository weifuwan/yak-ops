import { useQuery } from "@tanstack/react-query";
import { AlertCircle, Workflow } from "lucide-react";

import {
  listWorkflowDefinitions,
  type WorkflowDefinitionRecord,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";

import { LogicTaskEditor, type LogicTaskFormProps } from "../shared/logic-task-editor";
import { useGenericLogicTaskRunConfig } from "../shared/use-generic-logic-run-config";
import { LogicField, LogicSection, LogicSelect } from "../shared/logic-form-controls";
import { normalizeSubWorkflowTaskParams } from "./adapter";
import { hasSubWorkflowCycle } from "./service";

function SubWorkflowForm({
  params,
  onChange,
  disabled,
  workflows,
  isLoading,
  isError,
}: LogicTaskFormProps & {
  workflows: WorkflowDefinitionRecord[];
  isLoading: boolean;
  isError: boolean;
}) {
  const normalized = normalizeSubWorkflowTaskParams(params);
  const selectedWorkflow = workflows.find(
    (workflow) => workflow.code === normalized.workflowDefinitionCode,
  );

  return (
    <div className="space-y-3">
      <LogicSection
        title="子工作流"
        description="选择一个已有工作流作为当前节点的执行内容。子工作流运行完成后，当前节点才会结束。"
      >
        <div className="space-y-3">
          <LogicField label="目标工作流" required>
            <LogicSelect
              id="sub-workflow-definition"
              value={normalized.workflowDefinitionCode > 0 ? String(normalized.workflowDefinitionCode) : ""}
              options={workflows.map((workflow) => ({
                value: String(workflow.code),
                label: workflow.name + "（" + workflow.code + "）",
              }))}
              placeholder={
                isLoading
                  ? "正在加载工作流..."
                  : isError
                    ? "工作流列表加载失败"
                    : "请选择子工作流"
              }
              disabled={disabled || isLoading || isError}
              onChange={(value) => onChange({
                ...params,
                workflowDefinitionCode: Number(value),
              })}
            />
          </LogicField>

          {selectedWorkflow ? (
            <div className="rounded-md border border-[#e4e7ec] bg-[#f9fafb] p-3">
              <div className="flex items-center gap-2">
                <Workflow size={15} className="text-[#475467]" />
                <span className="text-xs font-semibold text-[#344054]">{selectedWorkflow.name}</span>
                <span className="ml-auto rounded-full bg-white px-2 py-0.5 text-[10px] text-[#667085]">
                  {selectedWorkflow.releaseState === "ONLINE" ? "已发布" : "未发布"}
                </span>
              </div>
              <div className="mt-2 grid grid-cols-2 gap-2 text-[11px] text-[#667085]">
                <span>工作流编码：{selectedWorkflow.code}</span>
                <span>版本：{selectedWorkflow.version}</span>
              </div>
            </div>
          ) : (
            <div className="rounded-md border border-dashed border-[#d0d5dd] bg-[#f9fafb] px-3 py-5 text-center text-xs text-[#667085]">
              选择工作流后，这里会展示子工作流的版本与发布状态
            </div>
          )}

          {selectedWorkflow?.releaseState !== "ONLINE" && selectedWorkflow ? (
            <div className="flex items-start gap-2 rounded-md border border-[#fedf89] bg-[#fffaeb] px-3 py-2 text-[11px] leading-4 text-[#b54708]">
              <AlertCircle size={14} className="mt-0.5 shrink-0" />
              当前子工作流尚未发布。请确认该工作流已具备可执行版本后再发布父工作流。
            </div>
          ) : null}
        </div>
      </LogicSection>

      <LogicSection title="执行说明">
        <ul className="list-disc space-y-1 pl-4 text-[11px] leading-5 text-[#667085]">
          <li>子工作流使用所选工作流的当前版本执行。</li>
          <li>父工作流的全局参数、启动参数和变量池由后端按现有机制传递。</li>
          <li>子工作流完成后，当前节点沿普通输出端口继续执行。</li>
          <li>不允许选择当前工作流，也不允许形成多层循环引用。</li>
        </ul>
      </LogicSection>
    </div>
  );
}

export function SubWorkflowEditor({
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
  const runConfig = useGenericLogicTaskRunConfig(task, readOnly);
  const workflowsQuery = useQuery({
    queryKey: ["workflow-logic-sub-workflows", task.projectCode],
    queryFn: () => listWorkflowDefinitions({ pageNo: 1, pageSize: 200 }),
    staleTime: 60_000,
  });
  const workflows = (workflowsQuery.data?.totalList ?? []).filter(
    (workflow) => workflow.code !== workflowCode,
  );

  return (
    <LogicTaskEditor
      workflowCode={workflowCode}
      task={task}
      taskName={taskName}
      taskType="SUB_WORKFLOW"
      readOnly={readOnly}
      runConfig={runConfig}
      onApplyTask={onApplyTask}
      onDirtyChange={onDirtyChange}
      onBackToWorkflow={onBackToWorkflow}
      validateParams={async (value) => {
        const selectedCode = normalizeSubWorkflowTaskParams(value).workflowDefinitionCode;
        if (selectedCode <= 0) return "请选择一个子工作流";
        if (selectedCode === workflowCode) return "不能将当前工作流设置为自己的子工作流";
        const cycle = await hasSubWorkflowCycle(workflowCode, selectedCode);
        return cycle ? "检测到子工作流存在循环引用，请调整工作流依赖关系" : null;
      }}
    >
      {(formProps) => (
        <SubWorkflowForm
          {...formProps}
          workflows={workflows}
          isLoading={workflowsQuery.isPending}
          isError={workflowsQuery.isError}
        />
      )}
    </LogicTaskEditor>
  );
}
