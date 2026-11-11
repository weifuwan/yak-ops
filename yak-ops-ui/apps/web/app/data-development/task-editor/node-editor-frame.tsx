import { useState, type ReactNode } from "react";
import {
  ArrowLeft,
  CalendarClock,
  Code2,
  GitBranch,
  Info,
  PlayCircle,
  RefreshCw,
  Save,
  Settings2,
  Square,
  X,
  Activity,
} from "lucide-react";

import type { WorkflowTaskRecord } from "@/service/workflow/definition";
import { PanelResizer } from "../layout/panel-resizer";

type NodeEditorView = "form" | "json";
type NodePanelKey = "properties" | "runtime" | "schedule" | "versions" | "ops";

const PANEL_ITEMS: Array<{ key: NodePanelKey; label: string }> = [
  { key: "properties", label: "属性" },
  { key: "runtime", label: "运行配置" },
  { key: "schedule", label: "调度配置" },
  { key: "versions", label: "版本" },
  { key: "ops", label: "运维" },
];

const PANEL_WIDTH = 340;
const MIN_PANEL_WIDTH = 280;
const MAX_PANEL_WIDTH = 520;

function ToolbarButton({
  label,
  icon,
  onClick,
  disabled = false,
  active = false,
  title,
}: {
  label: string;
  icon: ReactNode;
  onClick: () => void;
  disabled?: boolean;
  active?: boolean;
  title?: string;
}) {
  return (
    <button
      type="button"
      title={title ?? label}
      disabled={disabled}
      onClick={onClick}
      aria-pressed={active}
      className={[
        "flex h-7 cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-md px-2 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-40",
        active
          ? "bg-[#eef3ff] text-[#315efb]"
          : "text-[#475467] hover:bg-[var(--yak-color-hover)]",
      ].join(" ")}
    >
      {icon}
      {label}
    </button>
  );
}

function InfoRow({
  label,
  value,
}: {
  label: string;
  value: string | number | null | undefined;
}) {
  const displayValue =
    value === null || value === undefined || value === ""
      ? "未设置"
      : String(value);

  return (
    <div className="grid grid-cols-[96px_minmax(0,1fr)] gap-3 border-b border-[#f0f1f3] py-2 last:border-b-0">
      <span className="text-xs text-[#8b93a1]">{label}</span>
      <span className="break-all text-xs text-[#303846]">{displayValue}</span>
    </div>
  );
}

function PanelSection({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <section className="rounded-md border border-[#e8ebef] bg-white px-3 py-2">
      <h3 className="mb-1 text-xs font-semibold text-[#303846]">{title}</h3>
      {children}
    </section>
  );
}

function NodePanelContent({
  panel,
  task,
  taskName,
  taskType,
  workflowCode,
  lastWorkflowInstanceId,
  onBack,
}: {
  panel: NodePanelKey;
  task?: WorkflowTaskRecord;
  taskName: string;
  taskType: string;
  workflowCode: number;
  lastWorkflowInstanceId: number | null;
  onBack: () => void;
}) {
  if (panel === "properties") {
    return (
      <div className="space-y-3 p-3">
        <PanelSection title="节点信息">
          <InfoRow label="节点名称" value={taskName} />
          <InfoRow label="节点类型" value={task?.taskType ?? taskType} />
          <InfoRow label="节点编码" value={task?.code} />
          <InfoRow label="节点版本" value={task?.version} />
          <InfoRow label="所属工作流" value={workflowCode} />
          <InfoRow label="修改人" value={task?.modifyBy} />
          <InfoRow label="更新时间" value={task?.updateTime} />
        </PanelSection>
        {task?.description ? (
          <PanelSection title="节点描述">
            <p className="whitespace-pre-wrap break-words py-1 text-xs leading-5 text-[#687385]">
              {task.description}
            </p>
          </PanelSection>
        ) : null}
        {!task ? (
          <p className="px-1 text-xs leading-5 text-[#8b93a1]">
            当前为草稿节点，保存到工作流后可查看完整节点属性。
          </p>
        ) : null}
      </div>
    );
  }

  if (panel === "runtime") {
    return task ? (
      <div className="space-y-3 p-3">
        <PanelSection title="执行参数">
          <InfoRow label="Worker 分组" value={task.workerGroup} />
          <InfoRow label="任务优先级" value={task.taskPriority} />
          <InfoRow label="执行方式" value={task.taskExecuteType} />
          <InfoRow label="环境 ID" value={task.environmentCode} />
          <InfoRow label="超时时间" value={task.timeout} />
          <InfoRow label="超时策略" value={task.timeoutFlag} />
          <InfoRow label="失败重试次数" value={task.failRetryTimes} />
          <InfoRow label="重试间隔" value={task.failRetryInterval} />
          <InfoRow label="CPU 配额" value={task.cpuQuota} />
          <InfoRow label="内存上限" value={task.memoryMax} />
        </PanelSection>
        <p className="px-1 text-xs leading-5 text-[#8b93a1]">
          当前展示工作流定义中已有的节点运行参数；本阶段暂不提供独立编辑入口。
        </p>
      </div>
    ) : (
      <div className="p-4 text-xs leading-5 text-[#8b93a1]">
        节点保存后，这里会展示运行参数。
      </div>
    );
  }

  if (panel === "schedule") {
    return (
      <div className="space-y-3 p-3">
        <PanelSection title="调度归属">
          <div className="flex items-start gap-2 py-1">
            <CalendarClock size={15} className="mt-0.5 shrink-0 text-[#8b93a1]" />
            <p className="text-xs leading-5 text-[#687385]">
              节点不单独配置调度周期，调度时间由所属工作流统一管理。
            </p>
          </div>
          <InfoRow label="所属工作流" value={workflowCode} />
        </PanelSection>
        <button
          type="button"
          onClick={onBack}
          className="flex h-8 w-full cursor-pointer items-center justify-center rounded-md border border-[#dfe3e8] text-xs text-[#475467] transition-colors hover:border-[#315efb] hover:text-[#315efb]"
        >
          <ArrowLeft size={13} className="mr-1.5" />
          返回工作流配置调度
        </button>
      </div>
    );
  }

  if (panel === "versions") {
    return (
      <div className="space-y-3 p-3">
        <PanelSection title="当前版本">
          <InfoRow label="节点版本" value={task?.version} />
          <InfoRow label="节点编码" value={task?.code} />
          <InfoRow label="工作流编码" value={workflowCode} />
        </PanelSection>
        <p className="px-1 text-xs leading-5 text-[#8b93a1]">
          节点版本信息来自当前工作流定义。版本发布与切换统一在工作流中管理。
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3 p-3">
      <PanelSection title="最近一次单节点运行">
        <InfoRow
          label="工作流实例 ID"
          value={lastWorkflowInstanceId ?? "尚未发起运行"}
        />
        <InfoRow label="节点名称" value={taskName} />
        <InfoRow label="节点编码" value={task?.code} />
      </PanelSection>
      <div className="flex items-start gap-2 px-1">
        <Activity size={14} className="mt-0.5 shrink-0 text-[#8b93a1]" />
        <p className="text-xs leading-5 text-[#8b93a1]">
          完整运行历史、节点日志和实例状态后续在运维面板中接入。
        </p>
      </div>
    </div>
  );
}

export function NodeEditorFrame({
  task,
  taskName,
  taskType,
  workflowCode,
  activeView,
  onActiveViewChange,
  isDirty,
  saving,
  running,
  stopping,
  refreshing,
  lastWorkflowInstanceId,
  onRun,
  onStop,
  onSave,
  onRefresh,
  onBack,
  children,
}: {
  task?: WorkflowTaskRecord;
  taskName: string;
  taskType: string;
  workflowCode: number;
  activeView: NodeEditorView;
  onActiveViewChange: (view: NodeEditorView) => void;
  isDirty: boolean;
  saving: boolean;
  running: boolean;
  stopping: boolean;
  refreshing: boolean;
  lastWorkflowInstanceId: number | null;
  onRun: () => void;
  onStop: () => void;
  onSave: () => void;
  onRefresh: () => void;
  onBack: () => void;
  children: ReactNode;
}) {
  const [activePanel, setActivePanel] = useState<NodePanelKey | null>(null);
  const [panelWidth, setPanelWidth] = useState(PANEL_WIDTH);
  const activeTitle = PANEL_ITEMS.find((item) => item.key === activePanel)?.label;

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-white">
      <header className="flex h-9 shrink-0 items-center gap-1 border-b border-[#e5e7eb] bg-white px-2">
        <ToolbarButton
          label="运行"
          icon={<PlayCircle size={14} />}
          disabled={!task || running}
          title={task ? "仅运行当前 Java 节点" : "草稿节点暂不可运行"}
          onClick={onRun}
        />
        <ToolbarButton
          label="停止"
          icon={<Square size={13} />}
          disabled={!lastWorkflowInstanceId || stopping}
          title={
            lastWorkflowInstanceId
              ? "停止本编辑器发起的最近一次单节点运行"
              : "仅能停止本编辑器发起且已记录实例 ID 的运行"
          }
          onClick={onStop}
        />
        <span aria-hidden="true" className="mx-1 h-4 w-px bg-[#e5e7eb]" />
        <ToolbarButton
          label="保存"
          icon={<Save size={14} />}
          disabled={!task || !isDirty || saving}
          title={!task ? "草稿节点暂不支持独立保存" : isDirty ? "保存节点配置" : "配置没有变更"}
          onClick={onSave}
        />
        <ToolbarButton
          label="刷新"
          icon={<RefreshCw size={13} className={refreshing ? "animate-spin" : ""} />}
          disabled={refreshing}
          onClick={onRefresh}
        />
        <ToolbarButton
          label={activeView === "json" ? "配置模式" : "JSON"}
          icon={<Code2 size={14} />}
          active={activeView === "json"}
          onClick={() => onActiveViewChange(activeView === "json" ? "form" : "json")}
        />

        <div className="ml-auto flex min-w-0 items-center gap-1 pl-2">
          <ToolbarButton
            label="回到工作流"
            icon={<ArrowLeft size={14} />}
            onClick={onBack}
          />
        </div>
      </header>

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <main className="min-h-0 min-w-0 flex-1 overflow-hidden bg-white">
          {children}
        </main>

        <aside className="flex h-full min-h-0 shrink-0 bg-white">
          {activePanel ? (
            <>
              <PanelResizer
                width={panelWidth}
                minWidth={MIN_PANEL_WIDTH}
                maxWidth={MAX_PANEL_WIDTH}
                resizeDirection="left"
                ariaLabel="调整节点高级配置面板宽度"
                onResize={setPanelWidth}
              />
              <section
                className="flex h-full min-h-0 max-w-[calc(100vw-64px)] flex-col border-l border-[#e5e7eb] bg-white"
                style={{ width: panelWidth }}
              >
                <header className="flex h-11 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-3">
                  <span className="text-sm font-semibold text-[#303846]">{activeTitle}</span>
                  <button
                    type="button"
                    title="收起面板"
                    aria-label="收起面板"
                    onClick={() => setActivePanel(null)}
                    className="flex size-6 cursor-pointer items-center justify-center rounded text-[#8b93a1] transition-colors hover:bg-[#f0f2f5] hover:text-[#303846]"
                  >
                    <X size={14} />
                  </button>
                </header>
                <div className="min-h-0 flex-1 overflow-y-auto bg-white">
                  <NodePanelContent
                    panel={activePanel}
                    task={task}
                    taskName={taskName}
                    taskType={taskType}
                    workflowCode={workflowCode}
                    lastWorkflowInstanceId={lastWorkflowInstanceId}
                    onBack={onBack}
                  />
                </div>
              </section>
            </>
          ) : null}

          <nav
            aria-label="节点高级配置"
            className="flex h-full w-9 shrink-0 flex-col border-l border-[#e5e7eb] bg-white"
          >
            {PANEL_ITEMS.map((item) => {
              const selected = activePanel === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  title={item.label}
                  aria-label={item.label}
                  aria-pressed={selected}
                  onClick={() =>
                    setActivePanel((current) => (current === item.key ? null : item.key))
                  }
                  className={[
                    "relative flex w-full shrink-0 cursor-pointer items-center justify-center border-b border-[#e5e7eb] px-0 py-2 text-xs leading-[18px] transition-colors first:border-t first:border-[#e5e7eb] hover:bg-[#f7f9fc] hover:text-[#315efb]",
                    selected ? "bg-white text-[#315efb]" : "bg-white text-[#7c8796]",
                  ].join(" ")}
                >
                  {selected ? (
                    <span
                      aria-hidden="true"
                      className="absolute inset-y-0 left-0 z-[1] w-0.5 bg-[#315efb]"
                    />
                  ) : null}
                  <span className="[writing-mode:vertical-rl] [text-orientation:upright] [word-break:keep-all] whitespace-nowrap tracking-[2px]">
                    {item.label}
                  </span>
                </button>
              );
            })}
          </nav>
        </aside>
      </div>
    </div>
  );
}
