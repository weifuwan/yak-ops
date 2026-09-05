import { useIntl } from '@umijs/max';
import type { LucideIcon } from 'lucide-react';
import { ChevronRight } from 'lucide-react';

import { HomeEmptyState } from './HomeEmptyState';

export interface HomeAssetOverviewState {
  data?: HomeAssetOverview;
  loading: boolean;
  failed: boolean;
}

interface SectionHeaderProps {
  title: string;
  description?: string;
  onMore?: () => void;
}

export const formatMetric = (value?: number | null, locale = 'zh-CN') =>
  value == null ? '--' : new Intl.NumberFormat(locale).format(value);

export const compactName = (value: string) =>
  value.length > 16 ? `${value.slice(0, 13)}...` : value;

export const relativeTime = (value?: string | null, locale = 'zh-CN') => {
  if (!value) return '--';
  const timestamp = new Date(value).getTime();
  if (!Number.isFinite(timestamp)) return value;

  const minutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));
  const formatter = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  if (minutes < 1) return formatter.format(0, 'minute');
  if (minutes < 60) return formatter.format(-minutes, 'minute');
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return formatter.format(-hours, 'hour');
  const days = Math.floor(hours / 24);
  if (days < 7) return formatter.format(-days, 'day');
  return new Date(timestamp).toLocaleDateString(locale, {
    month: '2-digit',
    day: '2-digit',
  });
};

export type HomeLineageRelationKey =
  | 'reads'
  | 'writes'
  | 'derives'
  | 'consumes'
  | 'contains'
  | 'default';

const relationTypeKey = (relationType?: string): HomeLineageRelationKey => {
  const keys: Record<string, HomeLineageRelationKey> = {
    READS_FROM: 'reads',
    WRITES_TO: 'writes',
    DERIVES_FROM: 'derives',
    CONSUMES: 'consumes',
    CONTAINS: 'contains',
  };
  return keys[relationType?.toUpperCase() || ''] || 'default';
};

const DEFAULT_RELATION_LABELS: Record<HomeLineageRelationKey, string> = {
  reads: '读取',
  writes: '写入',
  derives: '派生',
  consumes: '消费',
  contains: '包含',
  default: '关系',
};

export const relationTypeLabel = (
  relationType?: string,
  resolve?: (key: HomeLineageRelationKey) => string,
) => {
  const key = relationTypeKey(relationType);
  if (key === 'default' && relationType) return relationType;
  return resolve?.(key) ?? DEFAULT_RELATION_LABELS[key];
};

export const assetTypeColor = (assetType?: string) => {
  const colors: Record<string, string> = {
    TABLE: '#6f83d9',
    COLUMN: '#8ca0d9',
    SQL_TASK: '#cf7e6b',
    DATASET: '#5b9b83',
    DATASET_FIELD: '#76aa98',
    CHART: '#d29359',
    DASHBOARD: '#8a72c7',
  };
  return colors[assetType?.toUpperCase() || ''] || '#87909d';
};

export function SectionHeader({
  title,
  description,
  onMore,
}: SectionHeaderProps) {
  const intl = useIntl();
  return (
    <header className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {title}
        </h2>
        {description ? (
          <p className="mt-1 text-[12px] leading-5 text-[#92969f]">
            {description}
          </p>
        ) : null}
      </div>

      {onMore ? (
        <button
          type="button"
          onClick={onMore}
          className="mt-0.5 flex shrink-0 items-center gap-0.5 border-0 bg-transparent p-0 text-[12px] text-[#747982] transition-colors hover:text-[#252832]"
        >
          {intl.formatMessage({ id: 'pages.home.common.viewMore' })}
          <ChevronRight size={14} strokeWidth={1.8} />
        </button>
      ) : null}
    </header>
  );
}

export function EmptyList({
  loading,
  failed,
  unavailable,
  text,
  icon,
}: {
  loading: boolean;
  failed: boolean;
  unavailable: boolean;
  text: string;
  icon: LucideIcon;
}) {
  const intl = useIntl();
  if (loading || failed || unavailable) {
    return (
      <div className="flex min-h-[214px] items-center justify-center text-[11px] text-[#a0a4ac]">
        {intl.formatMessage({
          id: loading
            ? 'pages.home.common.loading'
            : failed
              ? 'pages.home.common.loadFailed'
              : 'pages.home.common.unavailable',
        })}
      </div>
    );
  }

  return (
    <HomeEmptyState
      icon={icon}
      title={text}
      size="medium"
      className="min-h-[214px]"
    />
  );
}
