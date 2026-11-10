import { RefreshCw } from "lucide-react";
import type { WorkflowDefinitionVersionRecord } from "@/service/workflow/management";

import {
  EmptyState,
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

  return (
<div className="space-y-3 p-4">
      <div className="flex items-center justify-between gap-2">
        <div>
          <div className="text-xs font-semibold text-[#303846]">工作流版本</div>
          <div className={MUTED_CLASS}>当前版本 V{definition.version} · 最近 20 个版本</div>
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
      {versionsQuery.isPending ? (
        <div className={MUTED_CLASS}>正在加载版本...</div>
      ) : versionsQuery.isError ? (
        <div className="space-y-2">
          <PanelNotice tone="warning">版本列表加载失败。</PanelNotice>
          <PanelButton variant="secondary" onClick={() => void versionsQuery.refetch()}>
            <RefreshCw size={13} />
            重试
          </PanelButton>
        </div>
      ) : versions.length === 0 ? (
        <EmptyState title="暂无历史版本" />
      ) : (
        <div className="space-y-2">
          {versions.map((version: WorkflowDefinitionVersionRecord) => {
            const isCurrent = version.version === definition.version;
            return (
              <div
                key={version.version}
                className={[
                  "rounded-lg border p-3",
                  isCurrent ? "border-[#b8c8ff] bg-[#f7f9ff]" : "border-[#e5e9ef] bg-white",
                ].join(" ")}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-[#303846]">
                      V{version.version}
                    </span>
                    {isCurrent ? (
                      <span className="rounded bg-[#e9efff] px-1.5 py-0.5 text-[10px] text-[#315efb]">
                        当前版本
                      </span>
                    ) : null}
                  </div>
                  <span className="text-[10px] text-[#8b93a1]">
                    {version.releaseState === "ONLINE" ? "已上线" : "未上线"}
                  </span>
                </div>
                <div className="mt-2 truncate text-xs text-[#4e5969]">
                  {version.name || definition.name}
                </div>
                {version.description ? (
                  <div className="mt-1 line-clamp-2 text-[11px] leading-5 text-[#8b93a1]">
                    {version.description}
                  </div>
                ) : null}
                <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-[10px] text-[#969eaa]">
                  <span>操作人：{version.userName || version.operator || version.modifyBy || "-"}</span>
                  <span>时间：{formatTime(version.operateTime)}</span>
                </div>
                {!isCurrent ? (
                  <div className="mt-3">
                    {confirmVersion === version.version ? (
                      <div className="flex items-center justify-between gap-2 rounded-md bg-[#fff9ed] px-2.5 py-2">
                        <span className="text-[11px] text-[#8a6424]">
                          确认切换到 V{version.version}？
                        </span>
                        <div className="flex gap-1.5">
                          <button
                            type="button"
                            onClick={() => setConfirmVersion(null)}
                            className="rounded px-2 py-1 text-[11px] text-[#6c7480] hover:bg-white"
                          >
                            取消
                          </button>
                          <button
                            type="button"
                            disabled={switchingVersion || workflowIsOnline}
                            onClick={() => void switchVersion(version.version)}
                            className="rounded bg-[#315efb] px-2 py-1 text-[11px] text-white disabled:opacity-50"
                          >
                            {switchingVersion ? "切换中..." : "确认切换"}
                          </button>
                        </div>
                      </div>
                    ) : (
                      <button
                        type="button"
                        disabled={workflowIsOnline || switchingVersion}
                        onClick={() => setConfirmVersion(version.version)}
                        className="text-xs font-medium text-[#315efb] hover:underline disabled:cursor-not-allowed disabled:text-[#aab2bf]"
                      >
                        切换到此版本
                      </button>
                    )}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
