import YakTab from '@/components/YakTab';
import { useIntl } from '@umijs/max';
import { Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import { Copy, Ellipsis, Trash2, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { Node } from 'reactflow';
import type { WorkflowCanvasTaskOption, WorkflowNodeData } from './types';
import useWorkflowInspectorBehavior from './useWorkflowInspectorBehavior';
import WorkflowNodeIcon from './node/icons/WorkflowNodeIcon';
import WorkflowNodeInspectorLastRun from './WorkflowNodeInspectorLastRun';
import WorkflowNodeInspectorSettings from './WorkflowNodeInspectorSettings';
import type { WorkflowInspectorNextNode } from './WorkflowNodeInspectorSettings';

type InspectorTab = 'settings' | 'lastRun';

interface WorkflowNodeInspectorProps {
  node: Node<WorkflowNodeData>;
  locked: boolean;
  definitionId: string;
  nextNodes: WorkflowInspectorNextNode[];
  appendOptions: WorkflowCanvasTaskOption[];
  onChange: (patch: Partial<WorkflowNodeData>) => void;
  onClose: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
  onAppend: (taskId: string) => void;
}

const ActionButton = ({
  label,
  children,
  onClick,
}: {
  label: string;
  children: ReactNode;
  onClick?: () => void;
}) => (
  <button
    type="button"
    aria-label={label}
    className="flex h-7 w-7 items-center justify-center rounded-md border-0 bg-transparent text-[#667085] transition-colors hover:bg-[#f2f4f7] hover:text-[#344054]"
    onClick={onClick}
  >
    {children}
  </button>
);

const WorkflowNodeInspector = ({
  node,
  locked,
  definitionId,
  nextNodes,
  appendOptions,
  onChange,
  onClose,
  onDuplicate,
  onDelete,
  onAppend,
}: WorkflowNodeInspectorProps) => {
  const intl = useIntl();
  const [activeTab, setActiveTab] = useState<InspectorTab>('settings');
  const { panelWidth, resizing, handleResizePointerDown } = useWorkflowInspectorBehavior();

  useEffect(() => {
    setActiveTab('settings');
  }, [node.id]);

  const duplicateLabel = intl.formatMessage({ id: 'pages.workflow.editor.inspector.duplicate' });
  const deleteLabel = intl.formatMessage({ id: 'pages.workflow.editor.inspector.deleteNode' });
  const moreLabel = intl.formatMessage({ id: 'pages.workflow.editor.inspector.more' });
  const closeLabel = intl.formatMessage({ id: 'pages.workflow.editor.inspector.close' });

  const menuItems = useMemo<MenuProps['items']>(() => [
    {
      key: 'duplicate',
      icon: <Copy size={14} />,
      label: duplicateLabel,
      disabled: locked,
      onClick: onDuplicate,
    },
    {
      key: 'delete',
      icon: <Trash2 size={14} />,
      label: deleteLabel,
      danger: true,
      disabled: locked,
      onClick: onDelete,
    },
  ], [deleteLabel, duplicateLabel, locked, onDelete, onDuplicate]);

  return (
    <aside
      className="absolute bottom-0 right-0 top-0 z-20 flex w-[340px] flex-col overflow-hidden border-l border-[#e8eaee] bg-white"
      style={{ width: panelWidth }}
    >
      <div
        role="separator"
        aria-label={intl.formatMessage({ id: 'pages.workflow.editor.inspector.resize' })}
        aria-orientation="vertical"
        aria-valuenow={Math.round(panelWidth)}
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
          <WorkflowNodeIcon taskType={node.data.taskType} size="sm" />
          <div className="min-w-0 flex-1">
            <div className="truncate text-[13px] font-semibold text-[#161823]">
              {node.data.label}
            </div>
            <div className="mt-0.5 truncate text-[9px] text-[#98a2b3]">
              {node.data.typeLabel || node.data.taskType ||
                intl.formatMessage({ id: 'pages.workflow.editor.inspector.taskNode' })}
            </div>
          </div>

          <div className="flex shrink-0 items-center gap-0.5">
            <ActionButton label={duplicateLabel} onClick={locked ? undefined : onDuplicate}>
              <Copy size={14} />
            </ActionButton>
            <Dropdown menu={{ items: menuItems }} trigger={['click']} placement="bottomRight">
              <span>
                <ActionButton label={moreLabel}>
                  <Ellipsis size={15} />
                </ActionButton>
              </span>
            </Dropdown>
            <ActionButton label={closeLabel} onClick={onClose}>
              <X size={15} />
            </ActionButton>
          </div>
        </div>

        <div className="px-4">
          <YakTab
            activeKey={activeTab}
            items={[
              {
                key: 'settings',
                label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.settings' }),
              },
              {
                key: 'lastRun',
                label: intl.formatMessage({ id: 'pages.workflow.editor.inspector.lastRun' }),
              },
            ]}
            onChange={(key) => setActiveTab(key as InspectorTab)}
          />
        </div>
      </header>

      <div className="min-h-0 flex-1 overflow-y-auto bg-white">
        {activeTab === 'settings' ? (
          <WorkflowNodeInspectorSettings
            node={node}
            locked={locked}
            nextNodes={nextNodes}
            appendOptions={appendOptions}
            onChange={onChange}
            onAppend={onAppend}
          />
        ) : (
          <WorkflowNodeInspectorLastRun definitionId={definitionId} nodeId={node.id} />
        )}
      </div>
    </aside>
  );
};

export default WorkflowNodeInspector;
