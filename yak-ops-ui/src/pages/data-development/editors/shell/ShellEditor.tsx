import YakButton from '@/components/YakButton';
import { useIntl } from '@umijs/max';
import { Typography } from 'antd';
import { Snail, Trash2, Upload } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';

import { FileSuffixIcon } from '@/pages/resource-management/components/FileSuffixIcon';
import type { ResourceId } from '@/pages/resource-management/types';
import ResourcePicker, { type ResourcePickerValue } from '../../components/ResourcePicker';
import { useEditorMode } from '../session/editorModeStore';
import {
  updateEditorSessionConfig,
  updateEditorSessionContent,
  updateEditorSessionViewState,
  useEditorSession,
} from '../session/editorSessionStore';
import type {
  DevelopmentEditorContext,
  DevelopmentEditorRunResultContext,
} from '../types';
import ShellMonacoEditor, { type ShellEditorPosition } from './ShellMonacoEditor';

interface ShellTaskConfigJson {
  resourceId?: string;
  resourceName?: string;
  resourceVersion?: number;
  checksum?: string;
  shellExecutable?: string;
  scriptArgs?: string[];
  envVars?: Record<string, string>;
  timeoutSeconds?: number;
}

const parseConfigJson = (configJson: string): ShellTaskConfigJson => {
  try {
    return JSON.parse(configJson) as ShellTaskConfigJson;
  } catch {
    return {};
  }
};

const buildConfigJson = (config: ShellTaskConfigJson): string => {
  const cleaned: Record<string, unknown> = {};
  if (config.resourceId != null && config.resourceId !== '') cleaned.resourceId = config.resourceId;
  if (config.resourceName) cleaned.resourceName = config.resourceName;
  if (config.resourceVersion != null) cleaned.resourceVersion = config.resourceVersion;
  if (config.checksum) cleaned.checksum = config.checksum;
  if (config.shellExecutable) cleaned.shellExecutable = config.shellExecutable;
  if (config.scriptArgs && config.scriptArgs.length > 0) cleaned.scriptArgs = config.scriptArgs;
  if (config.envVars && Object.keys(config.envVars).length > 0) cleaned.envVars = config.envVars;
  if (config.timeoutSeconds != null) cleaned.timeoutSeconds = config.timeoutSeconds;
  return JSON.stringify(cleaned);
};

type ShellEditMode = 'inline' | 'resource';

const extractSuffix = (name?: string): string | undefined => {
  if (!name) return undefined;
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.substring(dot + 1).toLowerCase() : undefined;
};

export const ShellEditor = ({
  node,
  onRunContent,
  running,
}: DevelopmentEditorContext) => {
  const intl = useIntl();
  const session = useEditorSession(node.id, node.type);
  const config = useMemo(
    () => parseConfigJson(session.configJson || '{}'),
    [session.configJson],
  );
  const hasResource =
    config.resourceId != null && config.resourceId !== '' && config.resourceId !== '0';
  const [editMode, setEditMode] = useState<ShellEditMode>(() =>
    hasResource ? 'resource' : 'inline',
  );
  useEditorMode(node.id, editMode);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [position, setPosition] = useState<ShellEditorPosition>(() => ({
    lineNumber: session.viewState?.lineNumber || 1,
    column: session.viewState?.column || 1,
    selectionLength: 0,
  }));

  const updateConfig = useCallback(
    (partial: Partial<ShellTaskConfigJson>) => {
      const next = { ...config, ...partial };
      updateEditorSessionConfig(node.id, buildConfigJson(next));
    },
    [config, node.id],
  );

  const handleResourceSelected = (value: ResourcePickerValue) => {
    updateConfig({
      resourceId: String(value.id),
      resourceName: value.name,
      resourceVersion: undefined,
      checksum: undefined,
    });
    setPickerOpen(false);
  };

  const handleClearResource = () => {
    updateConfig({
      resourceId: '',
      resourceName: undefined,
      resourceVersion: undefined,
      checksum: undefined,
    });
  };

  const language = 'Shell';

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-white">
      <div className="flex shrink-0 items-center gap-1 border-b border-[#eef0f2] bg-[#fafafa] px-3 py-1.5">
        <YakButton
          type="text"
          htmlType="button"
          className={`!h-auto !rounded !px-3 !py-1 text-[12px] !font-medium transition-colors ${
            editMode === 'inline'
              ? '!bg-white !text-[#344054] shadow-sm'
              : '!text-[#667085] hover:!text-[#344054]'
          }`}
          onClick={() => setEditMode('inline')}
        >
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.script.inline' })}
        </YakButton>
        <YakButton
          type="text"
          htmlType="button"
          className={`!h-auto !rounded !px-3 !py-1 text-[12px] !font-medium transition-colors ${
            editMode === 'resource'
              ? '!bg-white !text-[#344054] shadow-sm'
              : '!text-[#667085] hover:!text-[#344054]'
          }`}
          onClick={() => setEditMode('resource')}
        >
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.script.resource' })}
        </YakButton>
      </div>

      {editMode === 'inline' ? (
        <div className="min-h-0 flex-1">
          <ShellMonacoEditor
            id={String(node.id)}
            value={session.content}
            initialViewState={session.viewState}
            onChange={(value) => updateEditorSessionContent(node.id, value)}
            onRunScript={onRunContent}
            running={running}
            onPositionChange={setPosition}
            onViewStateChange={(viewState) =>
              updateEditorSessionViewState(node.id, viewState)
            }
          />
        </div>
      ) : (
        <div className="flex-1 overflow-auto bg-white p-6">
          <div className="mb-6">
            <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
              <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
              {intl.formatMessage(
                { id: 'pages.dataDevelopment.editor.script.referenceFile' },
                { language },
              )}
            </Typography.Text>
            <Typography.Paragraph className="mb-3 text-[12px] text-[#98a2b3]">
              {intl.formatMessage(
                { id: 'pages.dataDevelopment.editor.script.resourceDescription' },
                { language },
              )}
            </Typography.Paragraph>

            {hasResource ? (
              <div className="flex items-center gap-3 rounded-lg border border-[#e4e7ec] bg-[#f9fafb] px-4 py-3">
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#eff8ff]">
                  <FileSuffixIcon suffix={extractSuffix(config.resourceName)} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[13px] font-medium text-[#344054]">
                    {config.resourceName || config.resourceId}
                  </div>
                  <div className="text-[11px] text-[#98a2b3]">
                    {config.checksum
                      ? `SHA-256: ${config.checksum.substring(0, 16)}...`
                      : intl.formatMessage({ id: 'pages.dataDevelopment.editor.script.versionLocked' })}
                  </div>
                </div>
                <YakButton
                  size="small"
                  type="text"
                  className="!text-[#667085]"
                  icon={<Upload size={14} />}
                  onClick={() => setPickerOpen(true)}
                />
                <YakButton
                  size="small"
                  type="text"
                  danger
                  className="!text-[#667085]"
                  icon={<Trash2 size={14} />}
                  onClick={handleClearResource}
                />
              </div>
            ) : (
              <button
                type="button"
                className="flex w-full cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-[#d0d5dd] bg-[#f9fafb] px-4 py-8 transition-colors hover:border-[#1570ef] hover:bg-[#eff8ff]"
                onClick={() => setPickerOpen(true)}
              >
                <Upload size={24} className="text-[#98a2b3]" />
                <div className="mt-2 text-[13px] text-[#475467]">
                  {intl.formatMessage(
                    { id: 'pages.dataDevelopment.editor.script.selectFile' },
                    { language },
                  )}
                </div>
                <div className="mt-1 text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.dataDevelopment.editor.script.selectUploaded' })}
                </div>
              </button>
            )}
          </div>

          <ResourcePicker
            open={pickerOpen}
            acceptSuffixes={['.sh', '.bash', '.ps1', '.psm1']}
            selectedId={config.resourceId as ResourceId | undefined}
            onCancel={() => setPickerOpen(false)}
            onConfirm={handleResourceSelected}
          />
        </div>
      )}

      <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[10px] text-[#7b808a]">
        <div className="flex min-w-0 items-center gap-3">
          <span className="font-medium text-[#667085]">Shell</span>
          <span className="truncate">{node.name}</span>
          {session.dirty ? (
            <span className="inline-flex shrink-0 items-center gap-1 text-[#667085]">
              <span className="h-1.5 w-1.5 rounded-full bg-[#667085]" />
              {intl.formatMessage({ id: 'pages.dataDevelopment.editor.unsaved' })}
            </span>
          ) : null}
        </div>
        <div className="flex shrink-0 items-center gap-3">
          {editMode === 'inline' && position.selectionLength > 0 ? (
            <span>
              {intl.formatMessage(
                { id: 'pages.dataDevelopment.editor.selectedChars' },
                { count: position.selectionLength },
              )}
            </span>
          ) : null}
          {editMode === 'inline' ? (
            <span>Ln {position.lineNumber}, Col {position.column}</span>
          ) : (
            <span>{intl.formatMessage({ id: 'pages.dataDevelopment.editor.script.referenceMode' })}</span>
          )}
        </div>
      </div>
    </div>
  );
};

export const ShellRunConfig = ({ node }: DevelopmentEditorContext) => {
  const intl = useIntl();
  return (
    <div className="text-[12px] leading-6 text-[#667085]">
      <div className="font-medium text-[#344054]">
        {intl.formatMessage(
          { id: 'pages.dataDevelopment.editor.script.runConfig' },
          { language: 'Shell' },
        )}
      </div>
      <div className="mt-2">
        {intl.formatMessage(
          { id: 'pages.dataDevelopment.editor.script.currentNode' },
          { name: node.name },
        )}
      </div>
      <div className="mt-3 border-t border-[#eef0f2] pt-3 text-[11px] leading-5 text-[#98a2b3]">
        <div>{intl.formatMessage({ id: 'pages.dataDevelopment.editor.script.shellRuntimeHint' })}</div>
        <div className="mt-2">
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.script.configHint' })}
        </div>
      </div>
    </div>
  );
};

export const ShellRunResult = ({ result }: DevelopmentEditorRunResultContext) => {
  const intl = useIntl();
  const language = 'Shell';
  if (!result) {
    return (
      <div className="flex h-full items-center justify-center text-center">
        <div>
          <div className="text-[13px] font-medium text-[#475467]">
            {intl.formatMessage(
              { id: 'pages.dataDevelopment.editor.script.runResult' },
              { language },
            )}
          </div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">
            {intl.formatMessage(
              { id: 'pages.dataDevelopment.editor.script.runHint' },
              { language },
            )}
          </div>
        </div>
      </div>
    );
  }

  if (result.status === 'RUNNING') {
    return (
      <div className="flex h-full items-center justify-center text-[12px] text-[#667085]">
        <Snail size={16} className="mr-2 animate-spin" />
        {intl.formatMessage(
          { id: 'pages.dataDevelopment.editor.script.running' },
          { language },
        )}
      </div>
    );
  }

  if (result.status !== 'SUCCESS') {
    const statusId =
      result.status === 'CANCELLED'
        ? 'pages.dataDevelopment.editor.script.cancelled'
        : result.status === 'TIMEOUT'
          ? 'pages.dataDevelopment.editor.script.timeout'
          : 'pages.dataDevelopment.editor.script.failed';
    return (
      <div className="flex h-full items-center justify-center px-6 text-center">
        <div className="max-w-[680px]">
          <div className="text-[13px] font-medium text-[#b42318]">
            {intl.formatMessage({ id: statusId }, { language })}
          </div>
          <div className="mt-2 break-words text-[11px] leading-5 text-[#667085]">
            {result.message || intl.formatMessage({ id: 'pages.dataDevelopment.editor.script.noError' })}
          </div>
          {result.output?.stderr ? (
            <pre className="mt-3 max-h-[240px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#f9fafb] p-3 text-left font-mono text-[11px] text-[#344054]">
              {String(result.output.stderr)}
            </pre>
          ) : null}
          <div className="mt-2 text-[10px] text-[#98a2b3]">
            {intl.formatMessage(
              { id: 'pages.dataDevelopment.editor.script.duration' },
              { duration: result.durationMs },
            )}
          </div>
        </div>
      </div>
    );
  }

  const output = result.output || {};
  const stdout = output.stdout ? String(output.stdout) : '';
  const stderr = output.stderr ? String(output.stderr) : '';
  const exitCode = output.exitCode ?? '—';

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex shrink-0 items-center justify-between border-b border-[#eef0f2] bg-[#fafafa] px-3 py-1.5">
        <span className="text-[12px] font-medium text-[#344054]">
          {intl.formatMessage(
            { id: 'pages.dataDevelopment.editor.script.completed' },
            { language },
          )}
        </span>
        <span className="text-[10px] text-[#98a2b3]">
          {intl.formatMessage(
            { id: 'pages.dataDevelopment.editor.script.exitDuration' },
            { exitCode: String(exitCode), duration: result.durationMs },
          )}
        </span>
      </div>
      <div className="min-h-0 flex-1 overflow-auto p-3">
        {stdout ? (
          <div className="mb-3">
            <div className="mb-1 text-[11px] font-medium text-[#475467]">stdout</div>
            <pre className="max-h-[320px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#f9fafb] p-3 font-mono text-[11px] leading-5 text-[#344054]">
              {stdout}
            </pre>
          </div>
        ) : null}
        {stderr ? (
          <div>
            <div className="mb-1 text-[11px] font-medium text-[#475467]">stderr</div>
            <pre className="max-h-[160px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#fef3f2] p-3 font-mono text-[11px] leading-5 text-[#b42318]">
              {stderr}
            </pre>
          </div>
        ) : null}
        {!stdout && !stderr ? (
          <div className="text-[12px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.editor.script.noOutput' })}
          </div>
        ) : null}
      </div>
    </div>
  );
};
