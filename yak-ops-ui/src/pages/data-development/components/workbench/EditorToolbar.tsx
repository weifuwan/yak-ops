import { useAccess, useIntl } from '@umijs/max';
import { Tooltip } from 'antd';
import { LoaderCircle, Play, Rocket, Save } from 'lucide-react';

import type { DevelopmentEditorDefinition } from '../../editors/types';
import type { DevelopmentDirectory, DevelopmentNode } from '../../types';

interface EditorToolbarProps {
  node: DevelopmentNode;
  directory?: DevelopmentDirectory;
  definition: DevelopmentEditorDefinition;
  onRun: () => void;
  onSave: () => void;
  onPublish: () => void;
  onLineage?: () => void;
  running: boolean;
  saving: boolean;
  publishing: boolean;
  lineageLoading?: boolean;
}

const iconButtonClassName =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#475467] outline-none transition-colors hover:bg-[#f5f5f6] hover:text-[#1f2937] focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:bg-transparent';

const EditorToolbar = ({
  node,
  directory,
  definition,
  onRun,
  onSave,
  onPublish,
  onLineage,
  running,
  saving,
  publishing,
  lineageLoading,
}: EditorToolbarProps) => {
  const access = useAccess();
  const intl = useIntl();
  const canExecute = access.hasPermission('data-development:execute');
  const canEdit = access.hasPermission('data-development:edit');
  const canPublish = access.hasPermission('data-development:publish');
  const Toolbar = definition.Toolbar;
  const capabilities = definition.capabilities;

  const text = (id: string) => intl.formatMessage({ id });

  return (
    <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e8e9ec] bg-white px-2">
      {Toolbar ? (
        <div className="flex h-full min-w-0 flex-1 items-center">
          <Toolbar
            node={node}
            directory={directory}
            onRun={onRun}
            onSave={onSave}
            onPublish={onPublish}
            onLineage={onLineage}
            running={running}
            saving={saving}
            publishing={publishing}
            lineageLoading={lineageLoading}
          />
        </div>
      ) : (
        <>
          <div className="flex h-full min-w-0 items-center">
            <div className="flex h-full items-center gap-0.5">
              {capabilities.run ? (
                <Tooltip
                  title={
                    !canExecute
                      ? text('pages.dataDevelopment.toolbar.noExecutePermission')
                      : running
                        ? text('pages.dataDevelopment.toolbar.running')
                        : text('pages.dataDevelopment.toolbar.run')
                  }
                  mouseEnterDelay={0.35}
                >
                  <button
                    type="button"
                    aria-label={text('pages.dataDevelopment.toolbar.run')}
                    disabled={running || !canExecute}
                    onClick={onRun}
                    className={iconButtonClassName}
                  >
                    {running ? (
                      <LoaderCircle size={15} className="animate-spin" />
                    ) : (
                      <Play size={15} strokeWidth={1.8} />
                    )}
                  </button>
                </Tooltip>
              ) : null}
              {capabilities.save ? (
                <Tooltip
                  title={
                    !canEdit
                      ? text('pages.dataDevelopment.toolbar.noEditPermission')
                      : text('pages.dataDevelopment.toolbar.saveDraft')
                  }
                  mouseEnterDelay={0.35}
                >
                  <button
                    type="button"
                    aria-label={text('pages.dataDevelopment.toolbar.saveDraft')}
                    disabled={saving || publishing || running || !canEdit}
                    onClick={onSave}
                    className={iconButtonClassName}
                  >
                    {saving ? (
                      <LoaderCircle size={15} className="animate-spin" />
                    ) : (
                      <Save size={15} strokeWidth={1.8} />
                    )}
                  </button>
                </Tooltip>
              ) : null}
              {capabilities.publish ? (
                <Tooltip
                  title={
                    !canPublish
                      ? text('pages.dataDevelopment.toolbar.noPublishPermission')
                      : text('pages.dataDevelopment.toolbar.publish')
                  }
                  mouseEnterDelay={0.35}
                >
                  <button
                    type="button"
                    aria-label={text('pages.dataDevelopment.toolbar.publish')}
                    disabled={saving || publishing || running || !canPublish}
                    onClick={onPublish}
                    className={iconButtonClassName}
                  >
                    {publishing ? (
                      <LoaderCircle size={15} className="animate-spin" />
                    ) : (
                      <Rocket size={15} strokeWidth={1.8} />
                    )}
                  </button>
                </Tooltip>
              ) : null}
            </div>
          </div>

          <div className="min-w-0 truncate pl-4 text-[11px] text-[#98a2b3]">
            {directory?.path || '/'} / {node.name}
          </div>
        </>
      )}
    </div>
  );
};

export default EditorToolbar;
