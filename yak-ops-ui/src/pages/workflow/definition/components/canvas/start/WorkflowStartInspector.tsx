import YakTab from '@/components/YakTab';
import { getWorkflowInstances } from '@/services/workflow';
import { useIntl } from '@umijs/max';
import { Input, Modal, Select, Switch, message } from 'antd';
import dayjs from 'dayjs';
import { GitBranch, Plus, RefreshCw, Trash2, Variable, X } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import WorkflowNextStep from '../WorkflowNextStep';
import type { WorkflowCanvasTaskOption } from '../types';
import useWorkflowInspectorBehavior from '../useWorkflowInspectorBehavior';
import type {
  WorkflowStartConfig,
  WorkflowStartInputField,
  WorkflowStartValueType,
  WorkflowStartVariable,
} from './types';

interface WorkflowStartNextNode {
  id: string;
  label: string;
  taskType: string;
}

interface WorkflowStartInspectorProps {
  definitionId: string;
  workflowName: string;
  config: WorkflowStartConfig;
  locked: boolean;
  nextNodes: WorkflowStartNextNode[];
  appendOptions: WorkflowCanvasTaskOption[];
  onChange: (config: WorkflowStartConfig) => void;
  onAppend: (taskId: string) => void;
  onClose: () => void;
}

type StartInspectorTab = 'settings' | 'lastRun';
type VariableScope = 'inputs' | 'variables';

interface EditorDraft {
  name: string;
  label: string;
  type: WorkflowStartValueType;
  required: boolean;
  description: string;
  defaultText: string;
}

const NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;

const TYPE_OPTIONS: WorkflowStartValueType[] = [
  'String',
  'Number',
  'Boolean',
  'Date',
  'File',
  'Object',
  'Array',
];

const createDraft = (
  value?: WorkflowStartInputField | WorkflowStartVariable,
): EditorDraft => ({
  name: value?.name || '',
  label: value?.label || '',
  type: value?.type || 'String',
  required: 'required' in (value || {}) ? Boolean((value as WorkflowStartInputField).required) : false,
  description: value?.description || '',
  defaultText:
    value?.defaultValue === undefined
      ? ''
      : typeof value.defaultValue === 'string'
        ? value.defaultValue
        : JSON.stringify(value.defaultValue),
});

const defaultValueText = (value: unknown) => {
  if (value === undefined || value === null || value === '') return '--';
  if (typeof value === 'string') return value;
  return JSON.stringify(value);
};

const formatTime = (value?: string) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '--';

const WorkflowStartInspector = ({
  definitionId,
  workflowName,
  config,
  locked,
  nextNodes,
  appendOptions,
  onChange,
  onAppend,
  onClose,
}: WorkflowStartInspectorProps) => {
  const intl = useIntl();
  const [activeTab, setActiveTab] = useState<StartInspectorTab>('settings');
  const [modalScope, setModalScope] = useState<VariableScope>();
  const [modalIndex, setModalIndex] = useState<number>();
  const [draft, setDraft] = useState<EditorDraft>(createDraft());
  const [lastRunLoading, setLastRunLoading] = useState(false);
  const [lastRunInput, setLastRunInput] = useState<Record<string, unknown>>();
  const [lastRunTime, setLastRunTime] = useState<string>();
  const { panelWidth, resizing, handleResizePointerDown } = useWorkflowInspectorBehavior({
    storageKey: 'yak-workflow-start-inspector-width',
    defaultWidth: 380,
    minWidth: 340,
    maxWidth: 560,
  });

  const usedNames = useMemo(
    () => new Set([...config.inputs, ...config.variables].map((item) => item.name)),
    [config.inputs, config.variables],
  );

  const openEditor = (
    scope: VariableScope,
    index?: number,
    value?: WorkflowStartInputField | WorkflowStartVariable,
  ) => {
    setModalScope(scope);
    setModalIndex(index);
    setDraft(createDraft(value));
  };

  const closeEditor = () => {
    setModalScope(undefined);
    setModalIndex(undefined);
    setDraft(createDraft());
  };

  const parseDefaultValue = (
    type: WorkflowStartValueType,
    text: string,
  ): unknown => {
    const normalized = text.trim();
    if (!normalized) return undefined;
    if (type === 'String' || type === 'Date') return normalized;
    if (type === 'Number') {
      const value = Number(normalized);
      if (!Number.isFinite(value)) throw new Error('Invalid number');
      return value;
    }
    if (type === 'Boolean') {
      if (normalized === 'true') return true;
      if (normalized === 'false') return false;
      throw new Error('Boolean must be true or false');
    }
    if (type === 'File') return undefined;
    try {
      return JSON.parse(normalized);
    } catch {
      if (type === 'Array') {
        return normalized
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean);
      }
      throw new Error('Invalid JSON');
    }
  };

  const saveDraft = () => {
    if (!modalScope) return;
    const name = draft.name.trim();
    if (!name) {
      message.error(intl.formatMessage({ id: 'pages.workflow.editor.startInspector.variableName' }));
      return;
    }
    if (!NAME_PATTERN.test(name)) {
      message.error(intl.formatMessage({ id: 'pages.workflow.editor.startInspector.namePattern' }));
      return;
    }
    const currentName =
      modalIndex !== undefined ? config[modalScope][modalIndex]?.name : undefined;
    if (usedNames.has(name) && currentName !== name) {
      message.error(
        intl.formatMessage(
          { id: 'pages.workflow.editor.startInspector.duplicate' },
          { name },
        ),
      );
      return;
    }

    let defaultValue: unknown;
    try {
      defaultValue = parseDefaultValue(draft.type, draft.defaultText);
    } catch (error) {
      message.error(error instanceof Error ? error.message : 'Invalid default value');
      return;
    }

    if (modalScope === 'inputs') {
      const next: WorkflowStartInputField = {
        name,
        label: draft.label.trim() || name,
        type: draft.type,
        required: draft.required,
        description: draft.description.trim() || undefined,
        defaultValue,
      };
      const inputs = [...config.inputs];
      if (modalIndex === undefined) inputs.push(next);
      else inputs.splice(modalIndex, 1, next);
      onChange({ ...config, inputs });
    } else {
      const next: WorkflowStartVariable = {
        name,
        label: draft.label.trim() || name,
        type: draft.type,
        description: draft.description.trim() || undefined,
        defaultValue,
      };
      const variables = [...config.variables];
      if (modalIndex === undefined) variables.push(next);
      else variables.splice(modalIndex, 1, next);
      onChange({ ...config, variables });
    }
    closeEditor();
  };

  const removeItem = (scope: VariableScope, index: number) => {
    const next = [...config[scope]];
    next.splice(index, 1);
    onChange({ ...config, [scope]: next });
  };

  const loadLastRun = useCallback(async () => {
    if (!definitionId) return;
    setLastRunLoading(true);
    try {
      const instances = await getWorkflowInstances();
      const latest = instances
        .filter((item) => item.definitionId === definitionId)
        .sort((left, right) =>
          String(right.startedAt || '').localeCompare(String(left.startedAt || '')),
        )[0];
      setLastRunInput(latest?.input);
      setLastRunTime(latest?.startedAt);
    } finally {
      setLastRunLoading(false);
    }
  }, [definitionId]);

  useEffect(() => {
    if (activeTab === 'lastRun') void loadLastRun();
  }, [activeTab, loadLastRun]);

  const renderVariableRow = (
    scope: VariableScope,
    item: WorkflowStartInputField | WorkflowStartVariable,
    index: number,
  ) => (
    <div
      key={`${scope}-${item.name}`}
      className="group flex min-h-[50px] items-center gap-2 border-b border-[#f0f1f3] px-1 py-2 last:border-b-0"
    >
      <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-[#f5f6f7] text-[#667085]">
        <Variable size={13} />
      </div>
      <button
        type="button"
        disabled={locked}
        className="min-w-0 flex-1 border-0 bg-transparent p-0 text-left disabled:cursor-default"
        onClick={() => openEditor(scope, index, item)}
      >
        <div className="flex min-w-0 items-center gap-1.5">
          <span className="truncate text-[11px] font-medium text-[#344054]">
            {item.label || item.name}
          </span>
          {'required' in item && item.required ? (
            <span className="shrink-0 rounded bg-[#fff1f3] px-1 py-0.5 text-[8px] font-medium text-[#d92d50]">
              {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.required' })}
            </span>
          ) : null}
        </div>
        <div className="mt-0.5 truncate font-mono text-[9px] text-[#98a2b3]">
          {scope}.{item.name} · {item.type}
          {item.defaultValue !== undefined ? ` · ${defaultValueText(item.defaultValue)}` : ''}
        </div>
      </button>
      {!locked ? (
        <button
          type="button"
          aria-label={intl.formatMessage({
            id:
              scope === 'inputs'
                ? 'pages.workflow.editor.startInspector.deleteInput'
                : 'pages.workflow.editor.startInspector.deleteVariable',
          })}
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md border-0 bg-transparent text-[#b0b7c3] opacity-0 hover:bg-[#fff1f3] hover:text-[#d92d50] group-hover:opacity-100"
          onClick={() => removeItem(scope, index)}
        >
          <Trash2 size={13} />
        </button>
      ) : null}
    </div>
  );

  const startLabel = intl.formatMessage({ id: 'pages.workflow.editor.start' });

  return (
    <>
      <aside
        className="absolute bottom-0 right-0 top-0 z-20 flex flex-col overflow-hidden border-l border-[#e8eaee] bg-white"
        style={{ width: panelWidth }}
      >
        <div
          role="separator"
          aria-label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.resize' })}
          aria-orientation="vertical"
          className="group/resize absolute left-0 top-0 z-30 flex h-full w-2 cursor-col-resize touch-none items-center justify-start"
          onPointerDown={handleResizePointerDown}
        >
          <span
            className={[
              'h-full w-0.5 transition-colors duration-150',
              resizing ? 'bg-[#6172f3]' : 'bg-transparent group-hover/resize:bg-[#6172f3]',
            ].join(' ')}
          />
        </div>

        <header className="shrink-0 bg-white">
          <div className="flex h-12 items-center gap-2 border-b border-[#eef0f2] px-4">
            <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-[#0b7a45] bg-[#079455] text-white">
              <PlayIcon />
            </div>
            <div className="min-w-0 flex-1">
              <div className="truncate text-[13px] font-semibold text-[#161823]">{startLabel}</div>
              <div className="mt-0.5 truncate text-[9px] text-[#98a2b3]">{workflowName}</div>
            </div>
            <button
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.workflow.editor.common.close' })}
              className="flex h-7 w-7 items-center justify-center rounded-md border-0 bg-transparent text-[#667085] hover:bg-[#f2f4f7]"
              onClick={onClose}
            >
              <X size={15} />
            </button>
          </div>
          <div className="px-4">
            <YakTab
              activeKey={activeTab}
              items={[
                {
                  key: 'settings',
                  label: intl.formatMessage({ id: 'pages.workflow.editor.startInspector.settings' }),
                },
                {
                  key: 'lastRun',
                  label: intl.formatMessage({ id: 'pages.workflow.editor.startInspector.lastRun' }),
                },
              ]}
              onChange={(key) => setActiveTab(key as StartInspectorTab)}
            />
          </div>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {activeTab === 'settings' ? (
            <div className="pb-6">
              <div className="px-4 py-4 text-[11px] leading-5 text-[#667085]">
                {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.descriptionText' })}
              </div>

              <StartSection
                title={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.inputs' })}
                description={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.inputsHint' })}
                action={
                  !locked ? (
                    <button
                      type="button"
                      className="flex h-7 items-center gap-1 rounded-md border-0 bg-[#f5f6f7] px-2 text-[10px] font-medium text-[#475467] hover:bg-[#eceef2]"
                      onClick={() => openEditor('inputs')}
                    >
                      <Plus size={12} />
                      {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.addInput' })}
                    </button>
                  ) : undefined
                }
              >
                {config.inputs.length ? (
                  config.inputs.map((item, index) => renderVariableRow('inputs', item, index))
                ) : (
                  <EmptySection text={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.noInputs' })} />
                )}
              </StartSection>

              <StartSection
                title={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.variables' })}
                description={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.variablesHint' })}
                action={
                  !locked ? (
                    <button
                      type="button"
                      className="flex h-7 items-center gap-1 rounded-md border-0 bg-[#f5f6f7] px-2 text-[10px] font-medium text-[#475467] hover:bg-[#eceef2]"
                      onClick={() => openEditor('variables')}
                    >
                      <Plus size={12} />
                      {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.addVariable' })}
                    </button>
                  ) : undefined
                }
              >
                {config.variables.length ? (
                  config.variables.map((item, index) => renderVariableRow('variables', item, index))
                ) : (
                  <EmptySection text="--" />
                )}
              </StartSection>

              <StartSection
                title={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.systemVariables' })}
                description={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.systemVariablesHint' })}
              >
                {config.systemVariables.map((item) => (
                  <div key={item.name} className="flex min-h-[44px] items-center gap-2 border-b border-[#f0f1f3] px-1 py-2 last:border-b-0">
                    <GitBranch size={13} className="shrink-0 text-[#98a2b3]" />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[11px] font-medium text-[#475467]">{item.label}</div>
                      <div className="mt-0.5 font-mono text-[9px] text-[#98a2b3]">sys.{item.name} · {item.type}</div>
                    </div>
                    <span className="rounded bg-[#f5f6f7] px-1.5 py-0.5 text-[8px] text-[#98a2b3]">
                      {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.readonly' })}
                    </span>
                  </div>
                ))}
              </StartSection>

              <StartSection title={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.nextNodes' })}>
                <WorkflowNextStep
                  currentIcon={<StartDot />}
                  nextNodes={nextNodes}
                  appendOptions={appendOptions}
                  locked={locked}
                  onAppend={onAppend}
                />
              </StartSection>
            </div>
          ) : (
            <div className="px-4 py-4">
              <div className="mb-3 flex items-center justify-between">
                <div className="text-[12px] font-semibold text-[#344054]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.workflowInput' })}
                </div>
                <button
                  type="button"
                  aria-label={intl.formatMessage({ id: 'pages.workflow.editor.common.refresh' })}
                  className="flex h-7 w-7 items-center justify-center rounded-md border-0 bg-transparent text-[#98a2b3] hover:bg-[#f2f4f7] hover:text-[#475467]"
                  onClick={() => void loadLastRun()}
                >
                  <RefreshCw size={14} className={lastRunLoading ? 'animate-spin' : undefined} />
                </button>
              </div>
              {lastRunLoading ? (
                <div className="py-12 text-center text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.lastRunLoading' })}
                </div>
              ) : lastRunInput ? (
                <>
                  <div className="mb-2 text-[9px] text-[#98a2b3]">{formatTime(lastRunTime)}</div>
                  <pre className="m-0 max-h-[420px] overflow-auto whitespace-pre-wrap break-words rounded-xl bg-[#f5f6f7] p-3 font-mono text-[11px] leading-5 text-[#344054]">
                    {JSON.stringify(lastRunInput, null, 2)}
                  </pre>
                </>
              ) : (
                <div className="py-12 text-center text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.lastRunEmpty' })}
                </div>
              )}
            </div>
          )}
        </div>
      </aside>

      <Modal
        open={Boolean(modalScope)}
        title={intl.formatMessage({
          id:
            modalScope === 'inputs'
              ? modalIndex === undefined
                ? 'pages.workflow.editor.startInspector.addInputTitle'
                : 'pages.workflow.editor.startInspector.editInputTitle'
              : modalIndex === undefined
                ? 'pages.workflow.editor.startInspector.addVariableTitle'
                : 'pages.workflow.editor.startInspector.editVariableTitle',
        })}
        okText={intl.formatMessage({ id: 'pages.workflow.editor.common.confirm' })}
        cancelText={intl.formatMessage({ id: 'pages.workflow.editor.common.cancel' })}
        onCancel={closeEditor}
        onOk={saveDraft}
      >
        <div className="space-y-4 pt-2">
          <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.variableName' })} required>
            <Input
              autoFocus
              value={draft.name}
              placeholder={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.variableNamePlaceholder' })}
              onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
            />
            {draft.name.trim() ? (
              <div className="mt-1 text-[9px] text-[#98a2b3]">
                {intl.formatMessage(
                  { id: 'pages.workflow.editor.startInspector.reference' },
                  { scope: modalScope, name: draft.name.trim() },
                )}
              </div>
            ) : null}
          </EditorField>
          <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.displayName' })}>
            <Input
              value={draft.label}
              placeholder={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.displayNamePlaceholder' })}
              onChange={(event) => setDraft((current) => ({ ...current, label: event.target.value }))}
            />
          </EditorField>
          <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.type' })} required>
            <Select
              className="w-full"
              value={draft.type}
              options={TYPE_OPTIONS.map((value) => ({ value, label: value }))}
              onChange={(value) =>
                setDraft((current) => ({
                  ...current,
                  type: value,
                  defaultText: value === 'File' ? '' : current.defaultText,
                }))
              }
            />
          </EditorField>
          {modalScope === 'inputs' ? (
            <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.required' })}>
              <Switch
                size="small"
                checked={draft.required}
                onChange={(checked) => setDraft((current) => ({ ...current, required: checked }))}
              />
            </EditorField>
          ) : null}
          <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.description' })}>
            <Input.TextArea
              rows={2}
              value={draft.description}
              onChange={(event) => setDraft((current) => ({ ...current, description: event.target.value }))}
            />
          </EditorField>
          <EditorField label={intl.formatMessage({ id: 'pages.workflow.editor.startInspector.defaultValue' })}>
            {draft.type === 'File' ? (
              <div className="rounded-lg bg-[#f7f8fa] px-3 py-2 text-[10px] leading-5 text-[#667085]">
                {intl.formatMessage({ id: 'pages.workflow.editor.startInspector.fileDefaultHint' })}
              </div>
            ) : draft.type === 'Boolean' ? (
              <Select
                allowClear
                className="w-full"
                value={draft.defaultText || undefined}
                options={[
                  { value: 'true', label: 'true' },
                  { value: 'false', label: 'false' },
                ]}
                onChange={(value) => setDraft((current) => ({ ...current, defaultText: value || '' }))}
              />
            ) : (
              <Input
                value={draft.defaultText}
                placeholder={
                  draft.type === 'Array'
                    ? intl.formatMessage({ id: 'pages.workflow.editor.startInspector.arrayPlaceholder' })
                    : undefined
                }
                onChange={(event) => setDraft((current) => ({ ...current, defaultText: event.target.value }))}
              />
            )}
          </EditorField>
        </div>
      </Modal>
    </>
  );
};

const PlayIcon = () => (
  <svg width="11" height="11" viewBox="0 0 11 11" fill="none" aria-hidden="true">
    <path d="M3 2.25L8.25 5.5L3 8.75V2.25Z" fill="currentColor" />
  </svg>
);

const StartDot = () => (
  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-[#079455] text-white">
    <PlayIcon />
  </span>
);

const StartSection = ({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
  children: React.ReactNode;
}) => (
  <section className="border-t border-[#f0f1f3] px-4 py-4 first:border-t-0">
    <div className="mb-2 flex items-start justify-between gap-3">
      <div>
        <div className="text-[12px] font-semibold text-[#344054]">{title}</div>
        {description ? (
          <div className="mt-0.5 text-[9px] leading-4 text-[#98a2b3]">{description}</div>
        ) : null}
      </div>
      {action}
    </div>
    {children}
  </section>
);

const EmptySection = ({ text }: { text: string }) => (
  <div className="whitespace-pre-line rounded-lg border border-dashed border-[#e4e7ec] px-3 py-4 text-center text-[10px] leading-5 text-[#98a2b3]">
    {text}
  </div>
);

const EditorField = ({
  label,
  required,
  children,
}: {
  label: string;
  required?: boolean;
  children: React.ReactNode;
}) => (
  <div>
    <div className="mb-1.5 text-[11px] font-medium text-[#475467]">
      {label}
      {required ? <span className="ml-1 text-[#d92d50]">*</span> : null}
    </div>
    {children}
  </div>
);

export default WorkflowStartInspector;
