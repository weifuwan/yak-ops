import type { ReactNode } from "react";
import { Alert, Button, Modal } from "@yak-ops/yak-ui";
import { useNavigate } from "react-router-dom";

import { EditorAnchorStepper, type EditorAnchorItem } from "./anchor-stepper";
import { AdvancedConfigDrawer } from "./advanced-config-drawer";
import { EditorFooter, EditorHeader } from "./editor-footer";
import { PropertiesDrawer } from "./properties-drawer";
import { YamlPreview } from "./yaml-preview";
import type { TaskEditorController } from "./use-task-editor-controller";

export function TaskEditorFrame({
  editor,
  anchorItems,
  children,
  onSave,
  onPreview,
}: {
  editor: TaskEditorController;
  anchorItems: EditorAnchorItem[];
  children: ReactNode;
  onSave: (publish: boolean) => void;
  onPreview: () => void;
}) {
  const navigate = useNavigate();

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]">
      <EditorHeader
        name={editor.authored.name}
        locked={editor.locked}
        onBack={() => navigate("/data-sync")}
        onAdvanced={editor.openAdvanced}
        onProperties={editor.openProperties}
      />
      <div className="flex min-h-0 flex-1 gap-4 px-4">
        <main className="min-h-0 min-w-0 flex-1 space-y-3 overflow-y-auto pb-6 pt-4">
          {editor.executionLocked ? (
            <Alert>
              {editor.executionLockMessage || "任务正在执行，暂不可编辑"}
            </Alert>
          ) : null}
          {editor.task?.status === "ONLINE" ? (
            <Alert>请先下线任务，再修改或重新发布配置。</Alert>
          ) : null}
          {editor.error ? (
            <Alert key={editor.error} pop>
              {editor.error}
            </Alert>
          ) : null}
          {editor.dataSourcesError ? (
            <Alert>
              {editor.dataSourcesError}
              <Button
                variant="secondary"
                size="sm"
                onClick={editor.loadDataSources}
              >
                重试
              </Button>
            </Alert>
          ) : null}
          <fieldset disabled={editor.locked} className="min-w-0 space-y-3">
            {children}
          </fieldset>
        </main>
        <aside className="hidden w-[176px] shrink-0 pt-4 lg:block">
          <EditorAnchorStepper items={anchorItems} />
        </aside>
      </div>

      <EditorFooter
        task={editor.task}
        dirty={editor.dirty}
        locked={editor.locked}
        saving={editor.saving}
        previewing={editor.previewing}
        onSave={() => onSave(false)}
        onPreview={onPreview}
        onPublish={() => onSave(true)}
      />

      {editor.previewYaml !== undefined ? (
        <Modal
          open
          centered
          width={760}
          title="Pipeline 配置预览"
          onClose={() => editor.setPreviewYaml(undefined)}
          footer={
            <div className="flex items-center justify-end gap-2">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => void editor.copyPreviewYaml()}
              >
                复制
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => editor.setPreviewYaml(undefined)}
              >
                关闭
              </Button>
            </div>
          }
        >
          <YamlPreview value={editor.previewYaml} />
        </Modal>
      ) : null}

      {editor.advancedOpen !== undefined ? (
        <AdvancedConfigDrawer
          value={editor.definition}
          open={editor.advancedOpen}
          onApply={(next) => {
            editor.edit(next);
            editor.closeAdvanced();
          }}
          onClose={editor.closeAdvanced}
          onClosed={editor.advancedClosed}
          onDirtyChange={editor.setDrawerDirty}
        />
      ) : null}

      {editor.propertiesOpen !== undefined ? (
        <PropertiesDrawer
          id={editor.task?.id}
          value={{
            name: editor.authored.name,
            remark: editor.authored.remark,
          }}
          syncPlan={editor.syncPlan}
          open={editor.propertiesOpen}
          onApply={(properties) => {
            editor.setAuthored((current) => ({ ...current, ...properties }));
            editor.closeProperties();
          }}
          onClose={editor.closeProperties}
          onClosed={editor.propertiesClosed}
          onDirtyChange={editor.setDrawerDirty}
        />
      ) : null}
    </div>
  );
}
