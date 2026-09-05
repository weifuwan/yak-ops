import { useIntl } from '@umijs/max';
import { useMemo, useState } from 'react';

import SqlResultWorkspace from '../../components/sql-result/SqlResultWorkspace';
import {
  updateEditorSessionContent,
  updateEditorSessionViewState,
  useEditorSession,
} from '../session/editorSessionStore';
import type {
  DevelopmentEditorContext,
  DevelopmentEditorRunResultContext,
} from '../types';
import SqlMonacoEditor, { type SqlEditorPosition } from './components/SqlMonacoEditor';
import { useSqlMetadataContext } from './metadata/sqlMetadataContextStore';

export const SqlEditor = ({
  node,
  onRunContent,
  running,
}: DevelopmentEditorContext) => {
  const intl = useIntl();
  const session = useEditorSession(node.id, node.type);
  const metadataContext = useSqlMetadataContext(node.id);
  const [position, setPosition] = useState<SqlEditorPosition>(() => ({
    lineNumber: session.viewState?.lineNumber || 1,
    column: session.viewState?.column || 1,
    selectionLength: 0,
  }));

  const metadataPath = useMemo(
    () =>
      [
        metadataContext.dataSourceName ||
          (metadataContext.dataSourceId ? `DS ${metadataContext.dataSourceId}` : undefined),
        metadataContext.database,
        metadataContext.schema,
      ]
        .filter(Boolean)
        .join(' / '),
    [
      metadataContext.dataSourceId,
      metadataContext.dataSourceName,
      metadataContext.database,
      metadataContext.schema,
    ],
  );
  const noDataSource = intl.formatMessage({ id: 'pages.dataDevelopment.editor.noDataSource' });

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-white">
      <div className="min-h-0 flex-1">
        <SqlMonacoEditor
          id={String(node.id)}
          value={session.content}
          initialViewState={session.viewState}
          onChange={(value) => updateEditorSessionContent(node.id, value)}
          onRunStatement={onRunContent}
          running={running}
          onPositionChange={setPosition}
          onViewStateChange={(viewState) => updateEditorSessionViewState(node.id, viewState)}
        />
      </div>

      <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[10px] text-[#7b808a]">
        <div className="flex min-w-0 items-center gap-3">
          <span className="font-medium text-[#667085]">SQL</span>
          <span className="truncate">{node.name}</span>
          {session.dirty ? (
            <span className="inline-flex shrink-0 items-center gap-1 text-[#667085]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#667085]" />
              {intl.formatMessage({ id: 'pages.dataDevelopment.editor.unsaved' })}
            </span>
          ) : null}
          <span
            className={[
              'max-w-[260px] truncate',
              metadataContext.dataSourceId ? 'text-[#667085]' : 'text-[#b0b7c3]',
            ].join(' ')}
            title={metadataPath || noDataSource}
          >
            {metadataPath || noDataSource}
          </span>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          {position.selectionLength > 0 ? (
            <span>
              {intl.formatMessage(
                { id: 'pages.dataDevelopment.editor.selectedChars' },
                { count: position.selectionLength },
              )}
            </span>
          ) : null}
          <span>Ln {position.lineNumber}, Col {position.column}</span>
        </div>
      </div>
    </div>
  );
};

export const SqlRunConfig = ({ node }: DevelopmentEditorContext) => {
  const intl = useIntl();
  return (
    <div className="text-[12px] leading-6 text-[#667085]">
      <div className="font-medium text-[#344054]">
        {intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlRunConfig' })}
      </div>
      <div className="mt-2 text-[11px] leading-5 text-[#98a2b3]">
        {intl.formatMessage(
          { id: 'pages.dataDevelopment.editor.sqlRunConfigHint' },
          { name: node.name },
        )}
      </div>
      <div className="mt-3 border-t border-[#eef0f2] pt-3 text-[11px] leading-5 text-[#98a2b3]">
        {intl.formatMessage({ id: 'pages.dataDevelopment.editor.sqlRunLimitHint' })}
      </div>
    </div>
  );
};

export const SqlRunResult = ({ result }: DevelopmentEditorRunResultContext) => (
  <SqlResultWorkspace result={result} />
);
