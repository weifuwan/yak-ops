import type { ComponentType, SVGProps } from 'react';
import SqlNodeIcon from './SqlNodeIcon';
import SyncNodeIcon from './SyncNodeIcon';

interface WorkflowNodeIconProps {
  taskType?: string;
  size?: 'xs' | 'sm' | 'md';
}

interface NodeIconMeta {
  icon: ComponentType<SVGProps<SVGSVGElement>>;
}

const DefaultNodeIcon = (props: SVGProps<SVGSVGElement>) => (
  <svg
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
    {...props}
  >
    <rect x="5" y="5" width="5" height="5" rx="1.5" stroke="currentColor" strokeWidth="1.6" />
    <rect x="14" y="14" width="5" height="5" rx="1.5" stroke="currentColor" strokeWidth="1.6" />
    <path d="M10 7.5h3.5a3 3 0 0 1 3 3V14" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
  </svg>
);

const DEFAULT_ICON_META: NodeIconMeta = {
  icon: DefaultNodeIcon,
};

const NODE_ICON_META: Record<string, NodeIconMeta> = {
  SYNC: {
    icon: SyncNodeIcon,
  },
  SQL: {
    icon: SqlNodeIcon,
  },
};

const DIFY_LLM_CONTAINER_SIZE: Record<NonNullable<WorkflowNodeIconProps['size']>, string> = {
  xs: 'h-4 w-4 rounded-[5px] shadow-[0_1px_2px_rgba(16,24,40,.05)]',
  sm: 'h-5 w-5 rounded-md shadow-[0_1px_2px_rgba(16,24,40,.05)]',
  md: 'h-6 w-6 rounded-lg shadow-[0_4px_8px_-2px_rgba(16,24,40,.10),0_2px_4px_-2px_rgba(16,24,40,.06)]',
};

const DIFY_LLM_ICON_SIZE: Record<NonNullable<WorkflowNodeIconProps['size']>, string> = {
  xs: 'h-3 w-3',
  sm: 'h-3.5 w-3.5',
  md: 'h-4 w-4',
};

const WorkflowNodeIcon = ({ taskType, size = 'md' }: WorkflowNodeIconProps) => {
  const normalizedTaskType = (taskType || '').toUpperCase();
  const meta = NODE_ICON_META[normalizedTaskType] || DEFAULT_ICON_META;
  const Icon = meta.icon;

  if (normalizedTaskType === 'SYNC') {
    return (
      <span
        className={[
          'flex shrink-0 items-center justify-center border-[0.5px] border-white/[0.02]',
          'bg-[#6172f3] text-white',
          DIFY_LLM_CONTAINER_SIZE[size],
        ].join(' ')}
      >
        <Icon className={DIFY_LLM_ICON_SIZE[size]} />
      </span>
    );
  }

  const compact = size === 'sm';
  const tiny = size === 'xs';

  return (
    <span
      className={[
        'flex shrink-0 items-center justify-center bg-[#6172f3] text-white',
        'shadow-[0_1px_2px_rgba(97,114,243,.22)]',
        tiny
          ? 'h-6 w-6 rounded-[7px]'
          : compact
            ? 'h-7 w-7 rounded-[8px]'
            : 'h-9 w-9 rounded-[10px]',
      ].join(' ')}
    >
      <Icon
        className={tiny
          ? 'h-[13px] w-[13px]'
          : compact
            ? 'h-[15px] w-[15px]'
            : 'h-[19px] w-[19px]'}
      />
    </span>
  );
};

export default WorkflowNodeIcon;
