import { NodeEditorFrame } from "../node-editor-frame";
import { ShellRunConfigPanel } from "./components/shell-run-config-panel";

import { ShellEditorWorkspace } from "./components/shell-editor-workspace";
import { useShellTaskEditor } from "./use-shell-task-editor";
import type { ShellTaskEditorProps } from "./types";

export function ShellTaskEditor({
  workflowCode,
  taskName,
  readOnly,
  canRun,
  onBackToWorkflow,
  ...editorProps
}: ShellTaskEditorProps) {
  const editor = useShellTaskEditor({
    workflowCode,
    taskName,
    readOnly,
    canRun,
    onBackToWorkflow,
    ...editorProps,
  });

  return (
    <NodeEditorFrame
      task={editor.currentTask}
      taskName={taskName}
      taskType="SHELL"
      workflowCode={workflowCode}
      activeView={editor.activeView}
      onActiveViewChange={editor.setActiveView}
      isDirty={editor.isDirty}
      saving={editor.saving}
      running={editor.running}
      stopping={editor.stopping}
      refreshing={editor.refreshing || editor.resourceQuery.isFetching}
      lastWorkflowInstanceId={editor.lastWorkflowInstanceId}
      canRun={canRun}
      readOnly={readOnly}
      onRun={() => void editor.handleRun()}
      onStop={() => void editor.handleStop()}
      onSave={() => void editor.handleSave()}
      onRefresh={() => void editor.handleRefresh()}
      onBack={onBackToWorkflow}
      runConfig={
        <ShellRunConfigPanel
          id="shell-run-config"
          settings={editor.shellRunConfig}
          onChange={editor.setShellRunConfig}
          disabled={readOnly}
          compact
        />
      }
    >
      {editor.activeView === "json" ? (
        <div className="min-h-0 flex-1 overflow-auto bg-[var(--yak-components-panel-bg)] p-4">
          <pre className="w-full overflow-auto rounded-sm border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4 font-mono text-xs leading-5 text-[var(--yak-components-panel-text)]">
            {editor.jsonText}
          </pre>
        </div>
      ) : (
        <ShellEditorWorkspace
          form={editor.form}
          readOnly={readOnly}
          activeTab={editor.activeConfigTab}
          onActiveTabChange={editor.setActiveConfigTab}
          updateField={editor.updateField}
          updateLocalParam={editor.updateLocalParam}
          resourceTree={editor.resourceTree}
          resourceLoading={editor.resourceQuery.isPending}
          resourceError={editor.resourceQuery.isError}
          onRetryResources={() => void editor.resourceQuery.refetch()}
        />
      )}
    </NodeEditorFrame>
  );
}
