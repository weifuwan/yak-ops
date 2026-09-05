import type { WorkflowTaskDefinition } from '@/services/workflow';
import { history, useIntl } from '@umijs/max';
import { Input, Tooltip } from 'antd';
import { Boxes, ChevronLeft, Database, Search } from 'lucide-react';
import type { DragEvent } from 'react';
import { useMemo, useState } from 'react';
import WorkflowNodeIcon from './node/icons/WorkflowNodeIcon';

interface WorkflowTaskLibraryProps {
  tasks: WorkflowTaskDefinition[];
  loading: boolean;
  locked: boolean;
  onDragStart: (event: DragEvent<HTMLDivElement>, task: WorkflowTaskDefinition) => void;
}

type LibraryPanel = 'nodes' | 'resources';

const WorkflowTaskLibrary = ({
  tasks,
  loading,
  locked,
  onDragStart,
}: WorkflowTaskLibraryProps) => {
  const intl = useIntl();
  const [activePanel, setActivePanel] = useState<LibraryPanel>('nodes');
  const [keyword, setKeyword] = useState('');

  const taskTypeLabel = (taskType?: string) => {
    if (!taskType || taskType === 'SYNC') {
      return intl.formatMessage({ id: 'pages.workflow.editor.taskType.sync' });
    }
    if (taskType === 'SQL') return 'SQL';
    return taskType;
  };

  const filteredTasks = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    if (!normalized) return tasks;
    return tasks.filter((task) =>
      task.name.toLowerCase().includes(normalized)
      || taskTypeLabel(task.type).toLowerCase().includes(normalized));
  }, [intl.locale, keyword, tasks]);

  const railButton = (
    active: boolean,
    label: string,
    icon: React.ReactNode,
    onClick: () => void,
  ) => (
    <Tooltip title={label} placement="right">
      <button
        type="button"
        aria-label={label}
        className={[
          'flex h-9 w-9 items-center justify-center rounded-lg border-0 transition-colors',
          active
            ? 'bg-[#f2f4f7] text-[#161823]'
            : 'bg-transparent text-[#98a2b3] hover:bg-[#f7f8fa] hover:text-[#475467]',
        ].join(' ')}
        onClick={onClick}
      >
        {icon}
      </button>
    </Tooltip>
  );

  const nodesLabel = intl.formatMessage({ id: 'pages.workflow.editor.library.nodes' });
  const resourcesLabel = intl.formatMessage({ id: 'pages.workflow.editor.library.resources' });

  return (
    <aside className="flex w-[280px] shrink-0 border-r border-[#e8eaee] bg-white">
      <div className="flex w-12 shrink-0 flex-col items-center border-r border-[#eef0f2] bg-[#fbfbfc] py-2">
        <Tooltip
          title={intl.formatMessage({ id: 'pages.workflow.editor.library.back' })}
          placement="right"
        >
          <button
            type="button"
            aria-label={intl.formatMessage({ id: 'pages.workflow.editor.library.back' })}
            className="mb-2 flex h-9 w-9 items-center justify-center rounded-lg border-0 bg-transparent text-[#667085] transition-colors hover:bg-[#f2f4f7] hover:text-[#161823]"
            onClick={() => history.push('/workflow/definitions')}
          >
            <ChevronLeft size={17} strokeWidth={1.9} />
          </button>
        </Tooltip>

        <div className="mb-2 h-px w-6 bg-[#eceef1]" />

        {railButton(
          activePanel === 'nodes',
          nodesLabel,
          <Boxes size={17} strokeWidth={1.8} />,
          () => setActivePanel('nodes'),
        )}
        {railButton(
          activePanel === 'resources',
          resourcesLabel,
          <Database size={17} strokeWidth={1.8} />,
          () => setActivePanel('resources'),
        )}
      </div>

      <div className="flex min-w-0 flex-1 flex-col bg-white">
        <div className="flex h-11 shrink-0 items-center px-4 text-[13px] font-semibold text-[#161823]">
          {activePanel === 'nodes' ? nodesLabel : resourcesLabel}
        </div>

        {activePanel === 'nodes' ? (
          <>
            <div className="px-3 pb-3">
              <Input
                allowClear
                variant="filled"
                value={keyword}
                prefix={<Search size={13} className="text-[#98a2b3]" />}
                placeholder={intl.formatMessage({ id: 'pages.workflow.editor.library.searchNodes' })}
                className="!h-8 !rounded-lg !text-[12px]"
                onChange={(event) => setKeyword(event.target.value)}
              />
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-4">
              <div className="mb-2 px-1 text-[10px] font-medium uppercase tracking-[0.08em] text-[#98a2b3]">
                {intl.formatMessage({ id: 'pages.workflow.editor.library.taskNodes' })}
              </div>

              {loading ? (
                <div className="space-y-1.5">
                  {[0, 1, 2].map((item) => (
                    <div key={item} className="h-10 animate-pulse rounded-lg bg-[#f5f6f7]" />
                  ))}
                </div>
              ) : filteredTasks.length ? (
                <div className="space-y-1.5">
                  {filteredTasks.map((task) => (
                    <div
                      key={task.id}
                      title={task.name}
                      draggable={!locked}
                      className={[
                        'group flex h-10 items-center gap-2.5 rounded-lg px-2.5 transition-[background-color,color,box-shadow]',
                        'bg-[#f7f8fa] text-[#344054]',
                        locked
                          ? 'cursor-not-allowed opacity-50'
                          : 'cursor-grab hover:bg-[#eceef2] hover:text-[#161823] hover:shadow-[inset_0_0_0_1px_rgba(16,24,40,.025)] active:cursor-grabbing active:bg-[#e7e9ee]',
                      ].join(' ')}
                      onDragStart={(event) => !locked && onDragStart(event, task)}
                    >
                      <WorkflowNodeIcon taskType={task.type} size="sm" />
                      <div className="min-w-0 flex-1 truncate text-[12px] font-medium leading-5">
                        {task.name}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="px-3 py-10 text-center text-[11px] text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.workflow.editor.library.noMatchNodes' })}
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="flex min-h-0 flex-1 flex-col px-3 pb-4">
            <div className="rounded-lg bg-[#f7f8fa] px-3 py-3 text-[11px] leading-5 text-[#667085]">
              {intl.formatMessage({ id: 'pages.workflow.editor.library.resourceHint' })}
            </div>
            <div className="flex flex-1 items-center justify-center text-[11px] text-[#b0b4bc]">
              {intl.formatMessage({ id: 'pages.workflow.editor.library.noResources' })}
            </div>
          </div>
        )}
      </div>
    </aside>
  );
};

export default WorkflowTaskLibrary;
