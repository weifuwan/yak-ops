import { NodeEditorFrame } from "../node-editor-frame";
import { NodeResourceParamsPanel } from "../common/node-resource-params";
import { TaskCommonSettingsSection } from "../task-common-settings-section";

import { ScriptEditorWorkspace } from "./script-editor-workspace";
import { useScriptTaskEditor } from "./use-script-task-editor";
import type { ScriptLanguage, ScriptTaskEditorProps, ScriptTaskType } from "./types";

export function ScriptTaskEditor({
  taskType,
  language,
  scriptLabel,
  workflowCode,
  taskName,
  readOnly,
  canRun,
  onBackToWorkflow,
  ...editorProps
}: ScriptTaskEditorProps & {
  taskType: ScriptTaskType;
  language: ScriptLanguage;
  scriptLabel: string;
}) {
  const editor = useScriptTaskEditor({
    taskType,
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
      taskType={taskType}
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
      resourceParams={
        <NodeResourceParamsPanel
          idPrefix={taskType.toLowerCase() + "-" + editor.currentTask.code}
          resourceList={editor.form.resourceList}
          onResourceListChange={(resourceList) => editor.updateField("resourceList", resourceList)}
          localParams={editor.form.localParams}
          onLocalParamsChange={(localParams) => editor.updateField("localParams", localParams)}
          resourceTree={editor.resourceTree}
          resourceLoading={editor.resourceQuery.isPending}
          resourceError={editor.resourceQuery.isError}
          onRetryResources={() => void editor.resourceQuery.refetch()}
          disabled={readOnly}
        />
      }
      runConfig={
        <TaskCommonSettingsSection
          id={taskType.toLowerCase() + "-run-config"}
          settings={editor.commonSettings}
          onChange={editor.setCommonSettings}
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
        <ScriptEditorWorkspace
          form={editor.form}
          readOnly={readOnly}
          updateField={editor.updateField}
          language={language}
          scriptLabel={scriptLabel}
        />
      )}
    </NodeEditorFrame>
  );
}
