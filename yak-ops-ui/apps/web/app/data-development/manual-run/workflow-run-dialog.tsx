import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Field,
  FieldLabel,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
} from "@yak-ops/yak-ui";

import type { WorkflowDefinitionRecord } from "@/service/workflow/definition";
import {
  startWorkflowInstance,
  type WorkflowFailureStrategy,
  type WorkflowPriority,
} from "@/service/workflow/execution";
import { listWorkerGroupNames } from "@/service/worker-group";
import { listEnvironments } from "@/service/environment";
import { useDataDevelopmentWorkbench } from "../workbench-context";

type Props = {
  workflow: WorkflowDefinitionRecord | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

const FAILURE_STRATEGIES: Record<WorkflowFailureStrategy, string> = {
  END: "失败即结束",
  CONTINUE: "失败后继续",
};

const PRIORITIES: Record<WorkflowPriority, string> = {
  HIGHEST: "最高",
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低",
  LOWEST: "最低",
};

function parseParams(text: string): Record<string, string> {
  return Object.fromEntries(
    text
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const index = line.indexOf("=");
        return index < 0
          ? [line, ""]
          : [line.slice(0, index).trim(), line.slice(index + 1).trim()];
      })
      .filter(([key]) => Boolean(key)),
  );
}

export function WorkflowRunDialog({ workflow, open, onOpenChange }: Props) {
  const { openWorkflowInstance } = useDataDevelopmentWorkbench();
  const [workerGroup, setWorkerGroup] = useState("default");
  const [environmentCode, setEnvironmentCode] = useState("-1");
  const [failureStrategy, setFailureStrategy] =
    useState<WorkflowFailureStrategy>("END");
  const [priority, setPriority] = useState<WorkflowPriority>("MEDIUM");
  const [paramsText, setParamsText] = useState("");
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const groupsQuery = useQuery({
    queryKey: ["data-development", "manual-run", "worker-groups"],
    queryFn: ({ signal }) => listWorkerGroupNames(signal),
    enabled: open,
  });

  const environmentsQuery = useQuery({
    queryKey: ["data-development", "manual-run", "environments"],
    queryFn: ({ signal }) =>
      listEnvironments({ pageNo: 1, pageSize: 100 }, signal),
    enabled: open,
  });

  const groups = useMemo(() => {
    const values = groupsQuery.data ?? [];
    return values.includes("default") ? values : ["default", ...values];
  }, [groupsQuery.data]);

  const environments = environmentsQuery.data?.totalList ?? [];

  const run = async () => {
    if (!workflow) return;
    setRunning(true);
    setError(null);
    try {
      const result = await startWorkflowInstance(String(workflow.code), {
        workerGroup,
        environmentCode: Number(environmentCode),
        failureStrategy,
        workflowInstancePriority: priority,
        startParams: parseParams(paramsText),
      });
      onOpenChange(false);
      openWorkflowInstance(
        result.workflowInstanceId,
        workflow.name + " · 运行 #" + result.workflowInstanceId,
      );
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "工作流启动失败");
    } finally {
      setRunning(false);
    }
  };

  return (
    <Modal
      open={open && Boolean(workflow)}
      centered
      width={520}
      title={
        <div className="flex min-w-0 flex-col gap-0.5">
          {workflow ? (
            <span className="truncate text-xs font-normal leading-4 text-[var(--yak-components-field-description)]">
              {workflow.name}
            </span>
          ) : null}
          <span>运行工作流</span>
        </div>
      }
      onClose={() => {
        if (!running) onOpenChange(false);
      }}
      bodyClassName="space-y-4"
      footer={
        <>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => onOpenChange(false)}
            isDisabled={running}
          >
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            onClick={() => void run()}
            isPending={running}
            isDisabled={running}
          >
            {running ? "启动中..." : "开始运行"}
          </Button>
        </>
      }
    >
      {workflow ? (
        <>
          <div className="space-y-4">
            <Field>
              <FieldLabel>Worker Group</FieldLabel>
              <Select<string>
                size="small"
                items={Object.fromEntries(groups.map((group) => [group, group]))}
                value={workerGroup}
                onValueChange={(next) => {
                  if (next) setWorkerGroup(next);
                }}
              >
                <SelectTrigger variant="outlined">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {groups.map((group) => (
                    <SelectItem key={group} value={group}>
                      <SelectItemText>{group}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <Field>
              <FieldLabel>Environment</FieldLabel>
              <Select<string>
                size="small"
                items={Object.fromEntries([
                  ["-1", "不使用环境"],
                  ...environments.map((environment) => [
                    String(environment.code),
                    environment.name,
                  ]),
                ])}
                value={environmentCode}
                onValueChange={(next) => {
                  if (next) setEnvironmentCode(next);
                }}
              >
                <SelectTrigger variant="outlined">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="-1">
                    <SelectItemText>不使用环境</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                  {environments.map((environment) => (
                    <SelectItem
                      key={String(environment.code)}
                      value={String(environment.code)}
                    >
                      <SelectItemText>{environment.name}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            <div className="grid grid-cols-2 gap-3">
              <Field>
                <FieldLabel>失败策略</FieldLabel>
                <Select<WorkflowFailureStrategy>
                  size="small"
                  items={FAILURE_STRATEGIES}
                  value={failureStrategy}
                  onValueChange={(next) => {
                    if (next) setFailureStrategy(next);
                  }}
                >
                  <SelectTrigger variant="outlined">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.entries(FAILURE_STRATEGIES).map(([value, label]) => (
                      <SelectItem
                        key={value}
                        value={value as WorkflowFailureStrategy}
                      >
                        <SelectItemText>{label}</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>

              <Field>
                <FieldLabel>优先级</FieldLabel>
                <Select<WorkflowPriority>
                  size="small"
                  items={PRIORITIES}
                  value={priority}
                  onValueChange={(next) => {
                    if (next) setPriority(next);
                  }}
                >
                  <SelectTrigger variant="outlined">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.entries(PRIORITIES).map(([value, label]) => (
                      <SelectItem key={value} value={value as WorkflowPriority}>
                        <SelectItemText>{label}</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
            </div>

            <Field>
              <FieldLabel>运行参数</FieldLabel>
              <Textarea
                size="small"
                variant="outlined"
                rows={4}
                className="min-h-24 resize-none leading-5"
                value={paramsText}
                onValueChange={setParamsText}
                placeholder={"每行一个参数，例如：\nbiz_date=2026-11-18"}
              />
            </Field>
          </div>

          {error ? (
            <div
              role="alert"
              className="rounded-[4px] bg-[#fff2f0] px-3 py-2 text-[12px] leading-5 text-[#d4380d]"
            >
              {error}
            </div>
          ) : null}
        </>
      ) : null}
    </Modal>
  );
}
