import type {
  WorkflowInstance,
  WorkflowInstanceOperations,
  WorkflowNodeInstance,
} from '@/services/workflow';
import { useIntl } from '@umijs/max';
import { useMemo } from 'react';

interface Props {
  instance: WorkflowInstance;
  operations?: WorkflowInstanceOperations;
  selectedNodeId?: string;
  onSelectNode?: (nodeId: string) => void;
}

const NODE_WIDTH = 196;
const NODE_HEIGHT = 76;
const COLUMN_GAP = 66;
const ROW_GAP = 28;
const PADDING = 24;

const STATUS_MESSAGE_IDS: Record<string, string> = {
  CREATED: 'pages.workflow.instanceDetail.status.created',
  WAITING: 'pages.workflow.instanceDetail.status.waiting',
  READY: 'pages.workflow.instanceDetail.status.ready',
  SUBMITTED: 'pages.workflow.instanceDetail.status.submitted',
  RUNNING: 'pages.workflow.instanceDetail.status.running',
  SUCCESS: 'pages.workflow.instanceDetail.status.success',
  SUCCESS_WITH_WARNINGS: 'pages.workflow.instanceDetail.status.successWarnings',
  FAILED: 'pages.workflow.instanceDetail.status.failed',
  UPSTREAM_FAILED: 'pages.workflow.instanceDetail.status.upstreamFailed',
  SKIPPED: 'pages.workflow.instanceDetail.status.skipped',
  CANCELED: 'pages.workflow.instanceDetail.status.canceled',
  PAUSED: 'pages.workflow.instanceDetail.status.paused',
};

const nodeClassName = (status: string, selected: boolean) => {
  const common = 'absolute rounded-lg border bg-white px-3 py-2.5 text-left transition-shadow';
  if (status === 'FAILED') {
    return `${common} border-[#fda29b] bg-[#fff7f6] ${selected ? 'ring-2 ring-[#fda29b]/40' : ''}`;
  }
  if (status === 'RUNNING' || status === 'SUBMITTED' || status === 'READY') {
    return `${common} border-[#98a2b3] ${selected ? 'ring-2 ring-[#98a2b3]/30' : ''}`;
  }
  if (status === 'SUCCESS' || status === 'SUCCESS_WITH_WARNINGS') {
    return `${common} border-[#d0d5dd] ${selected ? 'ring-2 ring-[#d0d5dd]/50' : ''}`;
  }
  return `${common} border-[#eaecf0] bg-[#fafafa] ${selected ? 'ring-2 ring-[#d0d5dd]/50' : ''}`;
};

const statusTextClassName = (status: string) => {
  if (status === 'FAILED') return 'text-[#d92d20]';
  if (status === 'RUNNING' || status === 'SUBMITTED' || status === 'READY') {
    return 'font-medium text-[#344054]';
  }
  return 'text-[#667085]';
};

const WorkflowDagView = ({ instance, operations, selectedNodeId, onSelectNode }: Props) => {
  const intl = useIntl();
  const layout = useMemo(() => {
    const nodes = instance.nodes || [];
    const nodeIds = new Set(nodes.map((node) => node.id));
    const edges = (operations?.edges || []).filter(
      (edge) => nodeIds.has(edge.source) && nodeIds.has(edge.target),
    );
    const successors = new Map<string, string[]>();
    const indegree = new Map<string, number>();
    nodes.forEach((node) => {
      successors.set(node.id, []);
      indegree.set(node.id, 0);
    });
    edges.forEach((edge) => {
      successors.get(edge.source)?.push(edge.target);
      indegree.set(edge.target, (indegree.get(edge.target) || 0) + 1);
    });
    const queue = nodes
      .filter((node) => (indegree.get(node.id) || 0) === 0)
      .map((node) => node.id);
    const level = new Map<string, number>();
    queue.forEach((id) => level.set(id, 0));
    let cursor = 0;
    while (cursor < queue.length) {
      const current = queue[cursor++];
      const currentLevel = level.get(current) || 0;
      (successors.get(current) || []).forEach((next) => {
        level.set(next, Math.max(level.get(next) || 0, currentLevel + 1));
        const nextDegree = (indegree.get(next) || 0) - 1;
        indegree.set(next, nextDegree);
        if (nextDegree === 0) queue.push(next);
      });
    }
    const fallbackLevel = Math.max(0, ...Array.from(level.values())) + 1;
    nodes.forEach((node) => {
      if (!level.has(node.id)) level.set(node.id, fallbackLevel);
    });
    const columns = new Map<number, WorkflowNodeInstance[]>();
    nodes.forEach((node) => {
      const value = level.get(node.id) || 0;
      const column = columns.get(value) || [];
      column.push(node);
      columns.set(value, column);
    });
    const positions = new Map<string, { x: number; y: number }>();
    let maxRows = 1;
    columns.forEach((column, columnIndex) => {
      maxRows = Math.max(maxRows, column.length);
      column.forEach((node, rowIndex) => {
        positions.set(node.id, {
          x: PADDING + columnIndex * (NODE_WIDTH + COLUMN_GAP),
          y: PADDING + rowIndex * (NODE_HEIGHT + ROW_GAP),
        });
      });
    });
    const maxLevel = Math.max(0, ...Array.from(level.values()));
    return {
      positions,
      edges,
      width: PADDING * 2 + (maxLevel + 1) * NODE_WIDTH + maxLevel * COLUMN_GAP,
      height: PADDING * 2 + maxRows * NODE_HEIGHT + (maxRows - 1) * ROW_GAP,
    };
  }, [instance.nodes, operations?.edges]);

  if (!instance.nodes.length) {
    return (
      <div className="py-10 text-center text-[12px] text-[#98a2b3]">
        {intl.formatMessage({ id: 'pages.workflow.instanceDetail.dagEmpty' })}
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-[#eaecf0] bg-[#fcfcfd]">
      <div
        className="relative"
        style={{ width: Math.max(layout.width, 520), height: Math.max(layout.height, 124) }}
      >
        <svg
          className="pointer-events-none absolute inset-0 h-full w-full"
          width={Math.max(layout.width, 520)}
          height={Math.max(layout.height, 124)}
          aria-hidden="true"
        >
          <defs>
            <marker id="workflow-dag-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 z" fill="#c7cdd6" />
            </marker>
          </defs>
          {layout.edges.map((edge) => {
            const source = layout.positions.get(edge.source);
            const target = layout.positions.get(edge.target);
            if (!source || !target) return null;
            const x1 = source.x + NODE_WIDTH;
            const y1 = source.y + NODE_HEIGHT / 2;
            const x2 = target.x;
            const y2 = target.y + NODE_HEIGHT / 2;
            const mid = x1 + Math.max(20, (x2 - x1) / 2);
            return (
              <path
                key={`${edge.source}->${edge.target}`}
                d={`M ${x1} ${y1} C ${mid} ${y1}, ${mid} ${y2}, ${x2 - 8} ${y2}`}
                fill="none"
                stroke="#d0d5dd"
                strokeWidth="1.4"
                markerEnd="url(#workflow-dag-arrow)"
              />
            );
          })}
        </svg>

        {instance.nodes.map((node) => {
          const position = layout.positions.get(node.id) || { x: PADDING, y: PADDING };
          const messageId = STATUS_MESSAGE_IDS[node.status];
          const statusText = messageId ? intl.formatMessage({ id: messageId }) : node.status;
          return (
            <button
              key={node.id}
              type="button"
              className={nodeClassName(node.status, selectedNodeId === node.id)}
              style={{ left: position.x, top: position.y, width: NODE_WIDTH, height: NODE_HEIGHT }}
              onClick={() => onSelectNode?.(node.id)}
            >
              <div className="truncate text-[12px] font-medium text-[#344054]" title={node.name}>
                {node.name || node.id}
              </div>
              <div className="mt-1 flex items-center justify-between gap-2 text-[11px]">
                <span className={statusTextClassName(node.status)}>{statusText}</span>
                <span className="text-[#98a2b3]">
                  {intl.formatMessage(
                    { id: 'pages.workflow.instanceDetail.attemptLabel' },
                    { count: node.attemptCount },
                  )}
                </span>
              </div>
              <div className="mt-1 truncate font-mono text-[10px] text-[#b0b7c3]" title={node.id}>
                {node.id}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
};

export default WorkflowDagView;
