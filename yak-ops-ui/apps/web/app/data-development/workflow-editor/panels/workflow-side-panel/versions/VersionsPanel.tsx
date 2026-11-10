import { RefreshCw } from "lucide-react";
import { Table, type TableColumns } from "@yak-ops/yak-ui";
import type { WorkflowDefinitionVersionRecord } from "@/service/workflow/management";

import {
  PanelButton,
  PanelNotice,
} from "../components/PanelPrimitives";
import { MUTED_CLASS } from "../shared/constants";
import { formatTime } from "../shared/utils";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";

export function VersionsPanel({
  controller,
}: {
  controller: WorkflowSidePanelController;
}) {
  const {
    definition,
    versions,
    workflowIsOnline,
    versionsQuery,
    confirmVersion,
    setConfirmVersion,
    switchingVersion,
    switchVersion,
  } = controller;

  const columns: TableColumns<WorkflowDefinitionVersionRecord> = [
    {
      key: "version",
      title: "版本信息",
      width: 170,
      render: (_value, version) => {
        const isCurrent = version.version === definition.version;
        return (
          <div className="min-w-0 space-y-0.5">
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-semibold text-[#303846]">
                V{version.version}
              </span>
              {isCurrent ? (
                <span className="rounded bg-[#e9efff] px-1.5 py-0.5 text-[10px] text-[#315efb]">
                  当前版本
                </span>
              ) : null}
            </div>
            <div className="truncate text-[11px] text-[#4e5969]">
              {version.name || definition.name}
            </div>
            <div
              className="truncate text-[10px] text-[#969eaa]"
              title={version.description || undefined}
            >
              {version.description || "暂无版本说明"}
            </div>
            <div className="truncate text-[10px] text-[#969eaa]">
              {version.userName || version.operator || version.modifyBy || "-"}
              {" · "}
              {formatTime(version.operateTime)}
            </div>
          </div>
        );
      },
    },
    {
      key: "releaseState",
      title: "状态",
      width: 68,
      align: "center",
      render: (_value, version) => (
        <span
          className={
            version.releaseState === "ONLINE"
              ? "inline-flex rounded bg-[#edf8f1] px-1.5 py-0.5 text-[10px] text-[#25834b]"
              : "inline-flex rounded bg-[#f1f3f5] px-1.5 py-0.5 text-[10px] text-[#687282]"
          }
        >
          {version.releaseState === "ONLINE" ? "已上线" : "未上线"}
        </span>
      ),
    },
    {
      key: "action",
      title: "操作",
      width: 92,
      align: "center",
      render: (_value, version) => {
        const isCurrent = version.version === definition.version;
        if (isCurrent) {
          return <span className="text-[10px] text-[#a0a7b2]">—</span>;
        }

        if (confirmVersion === version.version) {
          return (
            <div className="flex flex-col items-center gap-1">
              <button
                type="button"
                disabled={switchingVersion || workflowIsOnline}
                onClick={() => void switchVersion(version.version)}
                className="rounded bg-[#315efb] px-2 py-1 text-[10px] text-white disabled:opacity-50"
              >
                {switchingVersion ? "切换中..." : "确认切换"}
              </button>
              <button
                type="button"
                disabled={switchingVersion}
                onClick={() => setConfirmVersion(null)}
                className="text-[10px] text-[#7b8492] hover:text-[#303846] disabled:opacity-50"
              >
                取消
              </button>
            </div>
          );
        }

        return (
          <button
            type="button"
            disabled={workflowIsOnline || switchingVersion}
            onClick={() => setConfirmVersion(version.version)}
            className="whitespace-nowrap text-[11px] font-medium text-[#315efb] hover:underline disabled:cursor-not-allowed disabled:text-[#aab2bf]"
          >
            切换版本
          </button>
        );
      },
    },
  ];

  return (
    <div className="space-y-3 p-4">
      <div className="flex items-center justify-between gap-2">
        <div>
          <div className="text-xs font-semibold text-[#303846]">工作流版本</div>
          <div className={MUTED_CLASS}>
            当前版本 V{definition.version} · 最近 20 个版本
          </div>
        </div>
        <button
          type="button"
          onClick={() => void versionsQuery.refetch()}
          className="inline-flex items-center gap-1 text-[11px] text-[#6d7787] hover:text-[#315efb]"
        >
          <RefreshCw size={12} />
          刷新
        </button>
      </div>

      {workflowIsOnline ? (
        <PanelNotice tone="warning">
          工作流上线期间不能切换版本，请先下线工作流。
        </PanelNotice>
      ) : null}

      <Table<WorkflowDefinitionVersionRecord>
        className="w-full"
        columns={columns}
        dataSource={versions}
        rowKey="version"
        loading={versionsQuery.isFetching}
        size="small"
        rowHeight={88}
        scroll={{ x: 330 }}
        pagination={false}
        emptyText={
          versionsQuery.isError
            ? "版本列表加载失败"
            : "暂无历史版本"
        }
      />

      {versionsQuery.isError ? (
        <div className="space-y-2">
          <PanelNotice tone="warning">版本列表加载失败。</PanelNotice>
          <div className="flex justify-end">
            <PanelButton
              variant="secondary"
              onClick={() => void versionsQuery.refetch()}
            >
              <RefreshCw size={13} />
              重试
            </PanelButton>
          </div>
        </div>
      ) : null}
    </div>
  );
}
