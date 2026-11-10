import { RefreshCw } from "lucide-react";
import { Table, type TableColumns } from "@yak-ops/yak-ui";
import type { WorkflowDefinitionVersionRecord } from "@/service/workflow/management";

import {
  PanelButton,
  PanelNotice,
} from "../components/PanelPrimitives";
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
    versionsPageNo,
    versionsPageSize,
    versionsTotal,
    onVersionsPageChange,
    switchVersion,
  } = controller;

  const columns: TableColumns<WorkflowDefinitionVersionRecord> = [
    {
      key: "version",
      title: "版本",
      width: 88,
      render: (_value, version) => {
        const isCurrent = version.version === definition.version;

        return (
          <div className="flex items-center gap-1.5 whitespace-nowrap">
            <span className="text-xs font-medium text-[#303846]">
              V{version.version}
            </span>
            {isCurrent ? (
              <span className="rounded bg-[#e9efff] px-1 py-0.5 text-[10px] leading-4 text-[#315efb]">
                当前
              </span>
            ) : null}
          </div>
        );
      },
    },
    {
      key: "operator",
      title: "操作人",
      width: 112,
      render: (_value, version) => {
        const operator =
          version.userName || version.operator || version.modifyBy || "-";

        return (
          <span
            className="block truncate text-xs text-[#4e5969]"
            title={String(operator)}
          >
            {operator}
          </span>
        );
      },
    },
    {
      key: "operateTime",
      title: "时间",
      width: 152,
      render: (_value, version) => (
        <span className="whitespace-nowrap text-xs tabular-nums text-[#687282]">
          {formatTime(version.operateTime)}
        </span>
      ),
    },
    {
      key: "releaseState",
      title: "状态",
      width: 76,
      align: "center",
      render: (_value, version) => (
        <span
          className={
            version.releaseState === "ONLINE"
              ? "inline-flex whitespace-nowrap rounded bg-[#edf8f1] px-1.5 py-0.5 text-[10px] leading-4 text-[#25834b]"
              : "inline-flex whitespace-nowrap rounded bg-[#f1f3f5] px-1.5 py-0.5 text-[10px] leading-4 text-[#687282]"
          }
        >
          {version.releaseState === "ONLINE" ? "已上线" : "未上线"}
        </span>
      ),
    },
    {
      key: "action",
      title: "操作",
      width: 104,
      align: "center",
      render: (_value, version) => {
        const isCurrent = version.version === definition.version;

        if (isCurrent) {
          return <span className="text-xs text-[#a0a7b2]">—</span>;
        }

        if (confirmVersion === version.version) {
          return (
            <div className="flex items-center justify-center gap-2 whitespace-nowrap">
              <button
                type="button"
                disabled={switchingVersion || workflowIsOnline}
                onClick={() => void switchVersion(version.version)}
                className="text-xs font-medium text-[#315efb] hover:text-[#244bd0] disabled:cursor-not-allowed disabled:text-[#aab2bf]"
              >
                {switchingVersion ? "切换中..." : "确认"}
              </button>
              <span className="text-[10px] text-[#d5d9e0]">|</span>
              <button
                type="button"
                disabled={switchingVersion}
                onClick={() => setConfirmVersion(null)}
                className="text-xs text-[#7b8492] hover:text-[#303846] disabled:cursor-not-allowed disabled:opacity-50"
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
            className="whitespace-nowrap text-xs font-medium text-[#315efb] hover:text-[#244bd0] disabled:cursor-not-allowed disabled:text-[#aab2bf]"
          >
            切换
          </button>
        );
      },
    },
  ];

  return (
    <div className="space-y-3 p-4">
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
        rowHeight={48}
        scroll={{ x: 532 }}
        pagination={
          versionsTotal > 0
            ? {
                current: versionsPageNo,
                pageSize: versionsPageSize,
                total: versionsTotal,
                pageSizeOptions: [10, 20, 50, 100],
                pageSizeLabel: "每页显示：",
                showSizeChanger: true,
                disabled: versionsQuery.isFetching || switchingVersion,
                onChange: onVersionsPageChange,
              }
            : false
        }
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
