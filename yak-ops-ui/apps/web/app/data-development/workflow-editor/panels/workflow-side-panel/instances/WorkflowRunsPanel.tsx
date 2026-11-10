import { RefreshCw, X } from "lucide-react";
import { Table, type TableColumns } from "@yak-ops/yak-ui";

import {
  EmptyState,
  PanelButton,
  PanelNotice,
} from "../components/PanelPrimitives";
import { MUTED_CLASS } from "../shared/constants";
import { formatTime, statusClass, statusLabel } from "../shared/utils";
import type { WorkflowInstanceSummary } from "@/service/workflow/management";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";

export function WorkflowRunsPanel({
  mode,
  controller,
}: {
  mode: "history" | "ops";
  controller: WorkflowSidePanelController;
}) {
  const {
    selectedInstanceId,
    setSelectedInstanceId,
    selectedInstance,
    selectedInstanceQuery,
    instanceTasksQuery,
    tasks,
    instances,
    instancesQuery,
    activeInstanceCount,
    failedInstanceCount,
    successInstanceCount,
  } = controller;

  const renderInstanceDetail = () => {
    if (selectedInstanceId === null) return null;
    const detail = selectedInstanceQuery.data;
    return (
      <section className="mt-3 rounded-lg border border-[#e5e9ef] bg-[#fbfcfe] p-3">
        <div className="mb-2 flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="text-xs font-semibold text-[#303846]">
              实例 #{selectedInstanceId}
            </div>
            <div className={MUTED_CLASS}>
              {detail?.name || selectedInstance?.name || "工作流实例"}
            </div>
          </div>
          <button
            type="button"
            title="关闭实例详情"
            onClick={() => setSelectedInstanceId(null)}
            className="flex size-6 shrink-0 items-center justify-center rounded text-[#8992a0] hover:bg-[#eef1f5] hover:text-[#303846]"
          >
            <X size={14} />
          </button>
        </div>
        {selectedInstanceQuery.isPending ? (
          <div className={MUTED_CLASS}>正在加载实例详情...</div>
        ) : (
          <div className="grid grid-cols-2 gap-x-3 gap-y-2">
            <div>
              <div className={MUTED_CLASS}>运行状态</div>
              <span className={"mt-1 inline-flex rounded px-2 py-0.5 text-[11px] " + statusClass(detail?.state)}>
                {statusLabel(detail?.state)}
              </span>
            </div>
            <div>
              <div className={MUTED_CLASS}>执行人</div>
              <div className="mt-1 truncate text-xs text-[#4c5666]">
                {detail?.executorName || "-"}
              </div>
            </div>
            <div>
              <div className={MUTED_CLASS}>开始时间</div>
              <div className="mt-1 text-xs text-[#4c5666]">
                {formatTime(detail?.startTime)}
              </div>
            </div>
            <div>
              <div className={MUTED_CLASS}>结束时间</div>
              <div className="mt-1 text-xs text-[#4c5666]">
                {formatTime(detail?.endTime)}
              </div>
            </div>
            <div>
              <div className={MUTED_CLASS}>执行方式</div>
              <div className="mt-1 text-xs text-[#4c5666]">
                {detail?.commandType || "-"}
              </div>
            </div>
            <div>
              <div className={MUTED_CLASS}>Worker Group</div>
              <div className="mt-1 truncate text-xs text-[#4c5666]">
                {detail?.workerGroup || "-"}
              </div>
            </div>
          </div>
        )}
        <div className="mt-3 border-t border-[#e8ebf0] pt-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold text-[#3b4554]">节点执行明细</span>
            <span className={MUTED_CLASS}>{tasks.length} 个节点</span>
          </div>
          {instanceTasksQuery.isPending ? (
            <div className={MUTED_CLASS}>正在加载节点...</div>
          ) : instanceTasksQuery.isError ? (
            <div className="flex items-center justify-between gap-2 text-xs text-[#b34d47]">
              <span>节点明细加载失败</span>
              <button
                type="button"
                onClick={() => void instanceTasksQuery.refetch()}
                className="text-[#315efb] hover:underline"
              >
                重试
              </button>
            </div>
          ) : tasks.length === 0 ? (
            <div className={MUTED_CLASS}>暂无节点执行记录</div>
          ) : (
            <div className="space-y-1.5">
              {tasks.map((task) => (
                <div
                  key={task.id}
                  className="flex items-center justify-between gap-2 rounded-md bg-white px-2.5 py-2"
                >
                  <div className="min-w-0">
                    <div className="truncate text-xs text-[#3d4654]">{task.name}</div>
                    <div className={MUTED_CLASS}>{task.taskType}</div>
                  </div>
                  <span className={"shrink-0 rounded px-2 py-0.5 text-[10px] " + statusClass(task.state)}>
                    {statusLabel(task.state)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    );
  };


  const renderInstanceList = (items: WorkflowInstanceSummary[]) => (
    <div className="space-y-2">
      {items.map((instance) => (
        <button
          key={instance.id}
          type="button"
          onClick={() => setSelectedInstanceId(instance.id)}
          className={[
            "w-full rounded-lg border p-3 text-left transition-colors",
            selectedInstanceId === instance.id
              ? "border-[#a9bcff] bg-[#f5f8ff]"
              : "border-[#e6eaf0] bg-white hover:border-[#cbd5e3] hover:bg-[#fbfcfd]",
          ].join(" ")}
        >
          <div className="flex items-center justify-between gap-2">
            <span className="min-w-0 truncate text-xs font-medium text-[#374151]">
              {instance.name || "工作流实例 #" + instance.id}
            </span>
            <span className={"shrink-0 rounded px-2 py-0.5 text-[10px] " + statusClass(instance.state)}>
              {statusLabel(instance.state)}
            </span>
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-[#8a93a1]">
            <span>ID: {instance.id}</span>
            <span>开始：{formatTime(instance.startTime)}</span>
            <span>耗时：{instance.duration || "-"}</span>
          </div>
        </button>
      ))}
    </div>
  );


  const historyColumns: TableColumns<WorkflowInstanceSummary> = [
    {
      key: "instance",
      title: "实例信息",
      minWidth: 140,
      render: (_value, instance) => (
        <div className="min-w-0">
          <div className="truncate text-xs font-medium text-[#374151]">
            {instance.name || "工作流实例 #" + instance.id}
          </div>
          <div className="mt-0.5 text-[10px] text-[#8a93a1]">ID: {instance.id}</div>
        </div>
      ),
    },
    {
      key: "state",
      title: "状态",
      width: 72,
      align: "center",
      render: (_value, instance) => (
        <span className={"inline-flex rounded px-1.5 py-0.5 text-[10px] " + statusClass(instance.state)}>
          {statusLabel(instance.state)}
        </span>
      ),
    },
    {
      key: "execution",
      title: "开始时间 / 耗时",
      minWidth: 150,
      render: (_value, instance) => (
        <div className="min-w-0">
          <div className="truncate text-[11px] text-[#4c5666]">
            {formatTime(instance.startTime)}
          </div>
          <div className="mt-0.5 text-[10px] text-[#8a93a1]">
            耗时：{instance.duration || "-"}
          </div>
        </div>
      ),
    },
  ];

  const renderHistory = () => (
    <div className="space-y-3 p-4">
      
      <Table<WorkflowInstanceSummary>
        className="w-full"
        columns={historyColumns}
        dataSource={instances}
        rowKey="id"
        loading={instancesQuery.isFetching}
        size="small"
        rowHeight={56}
        scroll={{ x: 362 }}
        pagination={false}
        emptyText={
          instancesQuery.isError
            ? "运行记录加载失败"
            : "工作流执行后，实例记录会显示在这里。"
        }
        onRow={(instance) => ({
          onClick: () => setSelectedInstanceId(instance.id),
          onKeyDown: (event) => {
            if (event.key === "Enter" || event.key === " ") {
              event.preventDefault();
              setSelectedInstanceId(instance.id);
            }
          },
          tabIndex: 0,
          "aria-selected": selectedInstanceId === instance.id,
          title: "点击查看实例详情",
          className: [
            "cursor-pointer",
            selectedInstanceId === instance.id ? "bg-[#f5f8ff]" : "",
            "focus-visible:outline focus-visible:outline-1 focus-visible:outline-[#8ba9ff]",
          ].join(" "),
        })}
      />
      {instancesQuery.isError ? (
        <div className="flex justify-end pt-2">
          <PanelButton variant="secondary" onClick={() => void instancesQuery.refetch()}>
            <RefreshCw size={13} />
            重试
          </PanelButton>
        </div>
      ) : null}
      {renderInstanceDetail()}
    </div>
  );


  const renderOperations = () => {
    const attentionInstances = instances.filter((item) =>
      item.state === "FAILURE" ||
      ["SUBMITTED_SUCCESS", "RUNNING_EXECUTION", "READY_PAUSE", "READY_STOP", "SERIAL_WAIT"].includes(
        item.state,
      ),
    );
    return (
      <div className="space-y-3 p-4">
        <div className="flex items-center justify-between gap-2">
          <div>
            <div className="text-xs font-semibold text-[#303846]">运行运维概览</div>
            <div className={MUTED_CLASS}>统计范围：最近 20 条工作流实例</div>
          </div>
          <button
            type="button"
            onClick={() => void instancesQuery.refetch()}
            className="inline-flex items-center gap-1 text-[11px] text-[#6d7787] hover:text-[#315efb]"
          >
            <RefreshCw size={12} />
            刷新
          </button>
        </div>
        {instancesQuery.isPending ? (
          <div className={MUTED_CLASS}>正在加载实例...</div>
        ) : instancesQuery.isError ? (
          <PanelNotice tone="warning">实例信息加载失败，请重试。</PanelNotice>
        ) : (
          <>
            <div className="grid grid-cols-3 gap-2">
              <div className="rounded-lg border border-[#e4e9f0] bg-white p-2.5">
                <div className={MUTED_CLASS}>进行中</div>
                <div className="mt-1 text-xl font-semibold tabular-nums text-[#3568c8]">
                  {activeInstanceCount}
                </div>
              </div>
              <div className="rounded-lg border border-[#e4e9f0] bg-white p-2.5">
                <div className={MUTED_CLASS}>失败</div>
                <div className="mt-1 text-xl font-semibold tabular-nums text-[#cf4a43]">
                  {failedInstanceCount}
                </div>
              </div>
              <div className="rounded-lg border border-[#e4e9f0] bg-white p-2.5">
                <div className={MUTED_CLASS}>成功</div>
                <div className="mt-1 text-xl font-semibold tabular-nums text-[#25834b]">
                  {successInstanceCount}
                </div>
              </div>
            </div>
            <div className="text-xs font-semibold text-[#455064]">需要关注的实例</div>
            {attentionInstances.length > 0 ? (
              renderInstanceList(attentionInstances)
            ) : (
              <EmptyState title="暂无异常或运行中的实例" />
            )}
          </>
        )}
        {renderInstanceDetail()}
      </div>
    );
  };



  return mode === "history" ? renderHistory() : renderOperations();
}
