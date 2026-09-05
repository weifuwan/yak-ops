import type {
  WorkflowNodeFailurePolicy,
  WorkflowTriggerRule,
} from '@/services/workflow';
import {
  getWorkflowDefinition,
  upgradeWorkflowNodeTaskRevision,
  type WorkflowDefinitionNode,
} from '@/services/workflow/definitions';
import { useIntl, useParams } from '@umijs/max';
import { Button, Input, InputNumber, Select, Slider, Switch, Tooltip, message } from 'antd';
import { ChevronDown, CircleHelp, RefreshCw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { Node } from 'reactflow';
import WorkflowNextStep from './WorkflowNextStep';
import type { WorkflowCanvasTaskOption, WorkflowNodeData } from './types';
import WorkflowNodeIcon from './node/icons/WorkflowNodeIcon';

const MAX_RETRY_TIMES = 9;
const MAX_RETRY_DELAY_SECONDS = 3600;
const MAX_TIMEOUT_SECONDS = 24 * 60 * 60;

export interface WorkflowInspectorNextNode {
  id: string;
  label: string;
  taskType: string;
}

interface WorkflowNodeInspectorSettingsProps {
  node: Node<WorkflowNodeData>;
  locked: boolean;
  nextNodes: WorkflowInspectorNextNode[];
  appendOptions: WorkflowCanvasTaskOption[];
  onChange: (patch: Partial<WorkflowNodeData>) => void;
  onAppend: (taskId: string) => void;
}

const SectionTitle = ({ children }: { children: string }) => (
  <div className="mb-1 text-[12px] font-semibold text-[#344054]">{children}</div>
);

const Divider = () => <div className="mx-4 border-t border-[#f0f1f3]" />;

const HelpTip = ({ title }: { title: string }) => (
  <Tooltip title={title} placement="top">
    <CircleHelp size={13} className="ml-1 text-[#b0b4bc]" />
  </Tooltip>
);

const WorkflowNodeInspectorSettings = ({
  node,
  locked,
  nextNodes,
  appendOptions,
  onChange,
  onAppend,
}: WorkflowNodeInspectorSettingsProps) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const { id: workflowId = '' } = useParams<{ id: string }>();
  const [boundNode, setBoundNode] = useState<WorkflowDefinitionNode>();
  const [versionBusy, setVersionBusy] = useState(false);
  const catalogBound = node.data.taskId.startsWith('task-asset:');
  const retryTimes = Math.max(0, (node.data.maxAttempts || 1) - 1);
  const retryEnabled = retryTimes > 0;
  const mappingText = node.data.inputMappingText?.trim() || '{}';
  const hasAdvancedConfig = node.data.triggerRule !== 'ALL_SUCCESS'
    || (node.data.dispatchTimeoutSeconds || 0) > 0
    || (node.data.executionTimeoutSeconds || 0) > 0
    || (mappingText !== '{}' && mappingText !== '');

  const failureOptions = [
    { value: 'FAIL_WORKFLOW', label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.failure.none' }) },
    { value: 'BLOCK_BRANCH', label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.failure.stopBranch' }) },
    { value: 'IGNORE_FAILURE', label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.failure.ignore' }) },
  ];
  const triggerOptions = [
    { value: 'ALL_SUCCESS', label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.trigger.allSuccess' }) },
    { value: 'ALL_DONE', label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.trigger.allDone' }) },
    { value: 'NONE_FAILED', label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.trigger.noneFailed' }) },
    { value: 'ONE_SUCCESS', label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.trigger.oneSuccess' }) },
    { value: 'ALWAYS', label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.trigger.always' }) },
  ];

  const refreshVersion = async (showMessage = false) => {
    if (!catalogBound || !workflowId) return;
    setVersionBusy(true);
    try {
      const definition = await getWorkflowDefinition(workflowId);
      const current = definition.nodes.find((item) => item.id === node.id);
      setBoundNode(current);
      if (showMessage) {
        if (!current?.taskRevisionNo) {
          message.info(intlRef.current.formatMessage({ id: 'pages.workflow.editor.inspector.saveDraftFirst' }));
        } else if (current.taskRevisionUpdateAvailable) {
          message.info(
            intlRef.current.formatMessage(
              { id: 'pages.workflow.editor.inspector.newVersion' },
              { version: current.latestTaskRevisionNo || 0 },
            ),
          );
        } else {
          message.success(intlRef.current.formatMessage({ id: 'pages.workflow.editor.inspector.latestTaskVersion' }));
        }
      }
    } catch (error) {
      if (showMessage) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({ id: 'pages.workflow.editor.inspector.checkVersionFailed' }),
        );
      }
    } finally {
      setVersionBusy(false);
    }
  };

  useEffect(() => {
    setBoundNode(undefined);
    if (catalogBound) void refreshVersion(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [catalogBound, node.id, workflowId]);

  const handleUpgrade = async () => {
    if (!workflowId || !boundNode?.taskRevisionUpdateAvailable) return;
    setVersionBusy(true);
    try {
      const definition = await upgradeWorkflowNodeTaskRevision(workflowId, node.id);
      const current = definition.nodes.find((item) => item.id === node.id);
      setBoundNode(current);
      message.success(
        current?.taskRevisionNo
          ? intlRef.current.formatMessage(
              { id: 'pages.workflow.editor.inspector.upgradedTo' },
              { version: current.taskRevisionNo },
            )
          : intlRef.current.formatMessage({ id: 'pages.workflow.editor.inspector.upgraded' }),
      );
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.editor.inspector.upgradeFailed' }),
      );
    } finally {
      setVersionBusy(false);
    }
  };

  const handleRetryEnabledChange = (checked: boolean) => {
    if (!checked) {
      onChange({ maxAttempts: 1 });
      return;
    }
    onChange({ maxAttempts: Math.max(node.data.maxAttempts || 1, 4) });
  };

  const handleRetryTimesChange = (value: number | null) => {
    const nextRetryTimes = Math.min(MAX_RETRY_TIMES, Math.max(1, Number(value || 1)));
    onChange({ maxAttempts: nextRetryTimes + 1 });
  };

  return (
    <div className="pb-6">
      {catalogBound ? (
        <>
          <section className="px-4 py-4">
            <div className="mb-3 flex items-center justify-between">
              <div>
                <SectionTitle>{intl.formatMessage({ id: 'pages.workflow.editor.inspector.taskVersion' })}</SectionTitle>
                <div className="text-[10px] text-[rgba(22,24,35,.42)]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.inspector.revisionHint' })}
                </div>
              </div>
              <Tooltip title={intl.formatMessage({ id: 'pages.workflow.editor.inspector.checkLatest' })}>
                <button
                  type="button"
                  disabled={versionBusy}
                  className="flex h-7 w-7 items-center justify-center rounded-md border-0 bg-transparent text-[#667085] hover:bg-[#f5f6f7] disabled:opacity-50"
                  onClick={() => void refreshVersion(true)}
                >
                  <RefreshCw size={13} className={versionBusy ? 'animate-spin' : undefined} />
                </button>
              </Tooltip>
            </div>

            <div className="rounded-lg border border-[#e4e7ec] bg-[#fafafa] px-3 py-2.5">
              <div className="flex items-center justify-between text-[11px]">
                <span className="text-[#667085]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.inspector.pinned' })}
                </span>
                <span className="font-semibold text-[#344054]">
                  {boundNode?.taskRevisionNo
                    ? `v${boundNode.taskRevisionNo}`
                    : intl.formatMessage({ id: 'pages.workflow.editor.inspector.pinnedAfterSave' })}
                </span>
              </div>
              <div className="mt-2 flex items-center justify-between text-[11px]">
                <span className="text-[#667085]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.inspector.assetLatest' })}
                </span>
                <span className={boundNode?.taskRevisionUpdateAvailable ? 'font-semibold text-[#fe2c55]' : 'font-medium text-[#475467]'}>
                  {boundNode?.latestTaskRevisionNo ? `v${boundNode.latestTaskRevisionNo}` : '--'}
                </span>
              </div>
              {boundNode?.taskAssetStatus ? (
                <div className="mt-2 flex items-center justify-between text-[11px]">
                  <span className="text-[#667085]">
                    {intl.formatMessage({ id: 'pages.workflow.editor.inspector.assetStatus' })}
                  </span>
                  <span className="font-medium text-[#475467]">
                    {boundNode.taskAssetStatus === 'ONLINE'
                      ? intl.formatMessage({ id: 'pages.workflow.editor.inspector.online' })
                      : boundNode.taskAssetStatus}
                  </span>
                </div>
              ) : null}
            </div>

            {boundNode?.taskRevisionUpdateAvailable ? (
              <Button
                block
                size="small"
                className="mt-3"
                loading={versionBusy}
                disabled={locked}
                onClick={() => void handleUpgrade()}
              >
                {intl.formatMessage(
                  { id: 'pages.workflow.editor.inspector.upgradeTo' },
                  { version: boundNode.latestTaskRevisionNo || 0 },
                )}
              </Button>
            ) : boundNode?.taskRevisionNo ? (
              <div className="mt-2 text-center text-[10px] text-[#98a2b3]">
                {intl.formatMessage({ id: 'pages.workflow.editor.inspector.latestPinned' })}
              </div>
            ) : null}
          </section>
          <Divider />
        </>
      ) : null}

      <section className="py-2">
        <div className="flex min-h-12 items-center justify-between px-4 py-2">
          <div className="flex items-center">
            <div className="text-[12px] font-semibold text-[#344054]">
              {intl.formatMessage({ id: 'pages.workflow.editor.inspector.retryOnFailure' })}
            </div>
            <HelpTip title={intl.formatMessage({ id: 'pages.workflow.editor.inspector.retryHelp' })} />
          </div>
          <Switch size="small" disabled={locked} checked={retryEnabled} onChange={handleRetryEnabledChange} />
        </div>

        {retryEnabled ? (
          <div className="space-y-3 px-4 pb-4 pt-1">
            <div className="flex items-center gap-3">
              <div className="w-[88px] shrink-0 text-[11px] font-medium text-[#667085]">
                {intl.formatMessage({ id: 'pages.workflow.editor.inspector.retryCount' })}
              </div>
              <Slider className="m-0 min-w-0 flex-1" min={1} max={MAX_RETRY_TIMES} tooltip={{ open: false }} disabled={locked} value={retryTimes} onChange={(value) => handleRetryTimesChange(value)} />
              <div className="flex w-[82px] shrink-0 items-center gap-1">
                <InputNumber size="small" controls={false} disabled={locked} min={1} max={MAX_RETRY_TIMES} value={retryTimes} className="!w-[58px]" onChange={handleRetryTimesChange} />
                <span className="text-[10px] text-[rgba(22,24,35,.42)]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.common.times' })}
                </span>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="w-[88px] shrink-0 text-[11px] font-medium text-[#667085]">
                {intl.formatMessage({ id: 'pages.workflow.editor.inspector.retryDelay' })}
              </div>
              <Slider className="m-0 min-w-0 flex-1" min={0} max={MAX_RETRY_DELAY_SECONDS} tooltip={{ open: false }} disabled={locked} value={Math.min(node.data.retryDelaySeconds || 0, MAX_RETRY_DELAY_SECONDS)} onChange={(value) => onChange({ retryDelaySeconds: value })} />
              <div className="flex w-[82px] shrink-0 items-center gap-1">
                <InputNumber size="small" controls={false} disabled={locked} min={0} max={MAX_RETRY_DELAY_SECONDS} value={node.data.retryDelaySeconds} className="!w-[58px]" onChange={(value) => onChange({ retryDelaySeconds: Number(value || 0) })} />
                <span className="text-[10px] text-[rgba(22,24,35,.42)]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.common.seconds' })}
                </span>
              </div>
            </div>
          </div>
        ) : null}
      </section>

      <Divider />

      <section className="flex min-h-[64px] items-center justify-between gap-4 px-4 py-3">
        <div className="flex items-center">
          <div className="text-[12px] font-semibold text-[#344054]">
            {intl.formatMessage({ id: 'pages.workflow.editor.inspector.exceptionHandling' })}
          </div>
          <HelpTip title={intl.formatMessage({ id: 'pages.workflow.editor.inspector.exceptionHelp' })} />
        </div>
        <Select disabled={locked} size="small" className="w-[142px] shrink-0" value={node.data.failurePolicy} options={failureOptions} onChange={(value) => onChange({ failurePolicy: value as WorkflowNodeFailurePolicy })} />
      </section>

      <Divider />

      <details className="group px-4 py-3" open={hasAdvancedConfig || undefined}>
        <summary className="flex cursor-pointer list-none items-center justify-between rounded-lg px-0 py-1 text-[12px] font-semibold text-[#344054] [&::-webkit-details-marker]:hidden">
          <div className="flex items-center">
            {intl.formatMessage({ id: 'pages.workflow.editor.inspector.advanced' })}
            {hasAdvancedConfig ? (
              <span className="ml-2 rounded bg-[#fff1f3] px-1.5 py-0.5 text-[9px] font-medium text-[#d92d50]">
                {intl.formatMessage({ id: 'pages.workflow.editor.inspector.configured' })}
              </span>
            ) : null}
          </div>
          <ChevronDown size={14} className="text-[#98a2b3] transition-transform group-open:rotate-180" />
        </summary>
        <div className="mt-3 space-y-4 rounded-lg bg-[#fafafa] p-3">
          <div>
            <div className="mb-1.5 flex items-center text-[11px] font-medium text-[#667085]">
              {intl.formatMessage({ id: 'pages.workflow.editor.inspector.triggerRule' })}
              <HelpTip title={intl.formatMessage({ id: 'pages.workflow.editor.inspector.triggerHelp' })} />
            </div>
            <Select disabled={locked} size="small" className="w-full" value={node.data.triggerRule} options={triggerOptions} onChange={(value) => onChange({ triggerRule: value as WorkflowTriggerRule })} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <div className="mb-1.5 text-[11px] font-medium text-[#667085]">
                {intl.formatMessage({ id: 'pages.workflow.editor.inspector.dispatchTimeout' })}
              </div>
              <InputNumber size="small" controls={false} disabled={locked} min={0} max={MAX_TIMEOUT_SECONDS} value={node.data.dispatchTimeoutSeconds} className="!w-full" addonAfter={intl.formatMessage({ id: 'pages.workflow.editor.common.seconds' })} onChange={(value) => onChange({ dispatchTimeoutSeconds: Number(value || 0) })} />
              <div className="mt-1 text-[9px] text-[#98a2b3]">
                {intl.formatMessage({ id: 'pages.workflow.editor.inspector.dispatchNoLimit' })}
              </div>
            </div>
            <div>
              <div className="mb-1.5 text-[11px] font-medium text-[#667085]">
                {intl.formatMessage({ id: 'pages.workflow.editor.inspector.executionTimeout' })}
              </div>
              <InputNumber size="small" controls={false} disabled={locked} min={0} max={MAX_TIMEOUT_SECONDS} value={node.data.executionTimeoutSeconds} className="!w-full" addonAfter={intl.formatMessage({ id: 'pages.workflow.editor.common.seconds' })} onChange={(value) => onChange({ executionTimeoutSeconds: Number(value || 0) })} />
              <div className="mt-1 text-[9px] text-[#98a2b3]">
                {intl.formatMessage({ id: 'pages.workflow.editor.inspector.executionNoLimit' })}
              </div>
            </div>
          </div>
          <div>
            <div className="mb-1.5 flex items-center text-[11px] font-medium text-[#667085]">
              {intl.formatMessage({ id: 'pages.workflow.editor.inspector.inputMapping' })}
              <HelpTip title={intl.formatMessage({ id: 'pages.workflow.editor.inspector.inputMappingHelp' })} />
            </div>
            <Input.TextArea
              disabled={locked}
              autoSize={{ minRows: 3, maxRows: 8 }}
              spellCheck={false}
              value={node.data.inputMappingText}
              placeholder={'{\n  "requestId": "$workflow.requestId"\n}'}
              className="font-mono !text-[10px]"
              onChange={(event) => onChange({ inputMappingText: event.target.value })}
            />
          </div>
        </div>
      </details>

      <Divider />

      <section className="px-4 py-4">
        <SectionTitle>{intl.formatMessage({ id: 'pages.workflow.editor.inspector.nextStep' })}</SectionTitle>
        <div className="mb-3 text-[10px] leading-4 text-[rgba(22,24,35,.38)]">
          {intl.formatMessage({ id: 'pages.workflow.editor.inspector.nextStepHint' })}
        </div>
        <WorkflowNextStep
          currentIcon={<WorkflowNodeIcon taskType={node.data.taskType} size="sm" />}
          nextNodes={nextNodes}
          appendOptions={appendOptions}
          locked={locked}
          onAppend={onAppend}
        />
      </section>
    </div>
  );
};

export default WorkflowNodeInspectorSettings;
