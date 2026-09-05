import { useAccess, useIntl } from '@umijs/max';
import { Tooltip, message } from 'antd';
import {
  GitBranch,
  LoaderCircle,
  Play,
  Redo2,
  Rocket,
  Save,
  Search,
  Sparkles,
  Undo2,
  Wand2,
} from 'lucide-react';
import type { ReactNode } from 'react';

import type { DevelopmentEditorToolbarContext } from '../types';
import {
  executeSqlEditorCommand,
  type SqlEditorCommand,
} from './commands/sqlEditorCommandBus';
import SqlMetadataContextToolbar from './metadata/SqlMetadataContextToolbar';

const iconButtonClassName =
  'flex h-7 w-7 shrink-0 items-center justify-center rounded-[3px] text-[#475467] outline-none transition-colors hover:bg-[#f5f5f6] hover:text-[#1f2937] focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)] disabled:cursor-not-allowed disabled:opacity-45 disabled:hover:bg-transparent';

interface ToolbarButtonProps {
  title: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}

const ToolbarButton = ({ title, onClick, disabled, children }: ToolbarButtonProps) => (
  <Tooltip title={title} mouseEnterDelay={0.35}>
    <button
      type="button"
      aria-label={title}
      disabled={disabled}
      onClick={onClick}
      className={iconButtonClassName}
    >
      {children}
    </button>
  </Tooltip>
);

const ToolbarDivider = () => <span className="mx-1 h-4 w-px shrink-0 bg-[#e5e7eb]" />;

const SqlToolbar = ({
  node,
  onRun,
  onSave,
  onPublish,
  onLineage,
  running,
  saving,
  publishing,
  lineageLoading,
}: DevelopmentEditorToolbarContext) => {
  const access = useAccess();
  const intl = useIntl();
  const canExecute = access.hasPermission('data-development:execute');
  const canEdit = access.hasPermission('data-development:edit');
  const canPublish = access.hasPermission('data-development:publish');
  const text = (id: string) => intl.formatMessage({ id });

  const execute = (command: SqlEditorCommand) => {
    if (!executeSqlEditorCommand(node.id, command)) {
      message.info(text('pages.dataDevelopment.toolbar.sqlNotReady'));
    }
  };

  return (
    <div className="flex h-full w-full min-w-0 items-center justify-between gap-3">
      <div className="flex shrink-0 items-center gap-0.5">
        <ToolbarButton
          title={
            !canExecute
              ? text('pages.dataDevelopment.toolbar.noExecutePermission')
              : running
                ? text('pages.dataDevelopment.toolbar.sqlRunning')
                : text('pages.dataDevelopment.toolbar.runSql')
          }
          disabled={running || !canExecute}
          onClick={onRun}
        >
          {running ? <LoaderCircle size={15} className="animate-spin" /> : <Play size={15} strokeWidth={1.8} />}
        </ToolbarButton>
        <ToolbarDivider />
        <ToolbarButton
          title={!canEdit ? text('pages.dataDevelopment.toolbar.noEditPermission') : text('pages.dataDevelopment.toolbar.saveDraft')}
          disabled={saving || publishing || running || !canEdit}
          onClick={onSave}
        >
          {saving ? <LoaderCircle size={15} className="animate-spin" /> : <Save size={15} strokeWidth={1.8} />}
        </ToolbarButton>
        <ToolbarButton
          title={!canPublish ? text('pages.dataDevelopment.toolbar.noPublishPermission') : text('pages.dataDevelopment.toolbar.publish')}
          disabled={saving || publishing || running || !canPublish}
          onClick={onPublish}
        >
          {publishing ? <LoaderCircle size={15} className="animate-spin" /> : <Rocket size={15} strokeWidth={1.8} />}
        </ToolbarButton>
        {onLineage ? (
          <ToolbarButton
            title={lineageLoading ? text('pages.dataDevelopment.toolbar.lineageLoading') : text('pages.dataDevelopment.toolbar.lineage')}
            disabled={Boolean(lineageLoading || saving || publishing || running)}
            onClick={onLineage}
          >
            {lineageLoading ? <LoaderCircle size={15} className="animate-spin" /> : <GitBranch size={15} strokeWidth={1.8} />}
          </ToolbarButton>
        ) : null}
        <ToolbarDivider />
        <ToolbarButton
          title={text('pages.dataDevelopment.toolbar.undo')}
          disabled={running || !canEdit}
          onClick={() => execute('undo')}
        >
          <Undo2 size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton
          title={text('pages.dataDevelopment.toolbar.redo')}
          disabled={running || !canEdit}
          onClick={() => execute('redo')}
        >
          <Redo2 size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton title={text('pages.dataDevelopment.toolbar.find')} onClick={() => execute('find')}>
          <Search size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarDivider />
        <ToolbarButton
          title={text('pages.dataDevelopment.toolbar.formatSql')}
          disabled={running || !canEdit}
          onClick={() => execute('format')}
        >
          <Wand2 size={15} strokeWidth={1.8} />
        </ToolbarButton>
        <ToolbarButton title={text('pages.dataDevelopment.toolbar.suggest')} onClick={() => execute('suggest')}>
          <Sparkles size={15} strokeWidth={1.8} />
        </ToolbarButton>
      </div>
      <SqlMetadataContextToolbar nodeId={node.id} />
    </div>
  );
};

export default SqlToolbar;
