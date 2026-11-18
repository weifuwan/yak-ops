import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
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
    <Dialog open={open} onOpenChange={onOpenChange}>
      {workflow ? (
        <DialogContent className="w-[520px]">
          <DialogTitle className="text-base font-semibold">
            运行工作流
          </DialogTitle>
          <DialogDescription className="mt-1 text-[12px] text-[#7c828c]">
            {workflow.name}
          </DialogDescription>

          <div className="mt-5 space-y-4">
            <label className="block">
              <span className="mb-1.5 block text-[12px] text-[#657080]">Worker Group</span>
              <select
                value={workerGroup}
                onChange={(event) => setWorkerGroup(event.target.value)}
                className="h-9 w-full rounded-[4px] border border-[#d8dce3] bg-white px-2 text-[13px] outline-none focus:border-[#8aa8ff]"
              >
                {groups.map((group) => (
                  <option key={group} value={group}>{group}</option>
                ))}
              </select>
            </label>

            <label className="block">
              <span className="mb-1.5 block text-[12px] text-[#657080]">Environment</span>
              <select
                value={environmentCode}
                onChange={(event) => setEnvironmentCode(event.target.value)}
                className="h-9 w-full rounded-[4px] border border-[#d8dce3] bg-white px-2 text-[13px] outline-none focus:border-[#8aa8ff]"
              >
                <option value="-1">不使用环境</option>
                {environments.map((environment) => (
                  <option key={String(environment.code)} value={String(environment.code)}>
                    {environment.name}
                  </option>
                ))}
              </select>
            </label>

            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="mb-1.5 block text-[12px] text-[#657080]">失败策略</span>
                <select
                  value={failureStrategy}
                  onChange={(event) =>
                    setFailureStrategy(event.target.value as WorkflowFailureStrategy)
                  }
                  className="h-9 w-full rounded-[4px] border border-[#d8dce3] bg-white px-2 text-[13px] outline-none"
                >
                  <option value="END">失败即结束</option>
                  <option value="CONTINUE">失败后继续</option>
                </select>
              </label>

              <label className="block">
                <span className="mb-1.5 block text-[12px] text-[#657080]">优先级</span>
                <select
                  value={priority}
                  onChange={(event) =>
                    setPriority(event.target.value as WorkflowPriority)
                  }
                  className="h-9 w-full rounded-[4px] border border-[#d8dce3] bg-white px-2 text-[13px] outline-none"
                >
                  <option value="HIGHEST">最高</option>
                  <option value="HIGH">高</option>
                  <option value="MEDIUM">中</option>
                  <option value="LOW">低</option>
                  <option value="LOWEST">最低</option>
                </select>
              </label>
            </div>

            <label className="block">
              <span className="mb-1.5 block text-[12px] text-[#657080]">运行参数</span>
              <textarea
                value={paramsText}
                onChange={(event) => setParamsText(event.target.value)}
                placeholder={"每行一个参数，例如：\nbiz_date=2026-11-18"}
                className="h-24 w-full resize-none rounded-[4px] border border-[#d8dce3] bg-white p-2 text-[12px] leading-5 outline-none focus:border-[#8aa8ff]"
              />
            </label>

            {error ? (
              <div className="rounded-[4px] bg-[#fff2f0] px-3 py-2 text-[12px] leading-5 text-[#d4380d]">
                {error}
              </div>
            ) : null}
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button
              size="sm"
              variant="secondary"
              onClick={() => onOpenChange(false)}
              isDisabled={running}
            >
              取消
            </Button>
            <Button size="sm" onClick={run} isDisabled={running}>
              {running ? "启动中..." : "开始运行"}
            </Button>
          </div>
        </DialogContent>
      ) : null}
    </Dialog>
  );
}
