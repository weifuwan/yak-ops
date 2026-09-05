import type { DataSourceRecord } from '@/services/data-source';
import type {
  TableAssetView,
  TableCandidateView,
} from '@/services/data-quality';

import {
  QUALITY_SOURCE_TREE_DEFAULT_WIDTH,
  QUALITY_SOURCE_TREE_MAX_WIDTH,
  QUALITY_SOURCE_TREE_MIN_WIDTH,
} from './constants';
import type {
  QualityDataSourceGroup,
  QualityDataSourceNode,
  QualityDataSourceTreeKey,
} from './types';

export const normalizeQualityDataSourceType = (value?: string) =>
  value?.trim().toUpperCase() || 'OTHER';

export const qualityDataSourceNodeKey = (
  dataSourceId: number,
): QualityDataSourceTreeKey => `data-source:${dataSourceId}`;

export const buildQualityDataSourceNodes = (
  records: DataSourceRecord[],
): QualityDataSourceNode[] =>
  records
    .map((record) => {
      const dataSourceId = Number(record.id);
      if (!Number.isFinite(dataSourceId) || dataSourceId <= 0) return undefined;

      return {
        key: qualityDataSourceNodeKey(dataSourceId),
        dataSourceId,
        dataSourceName: record.name || `#${dataSourceId}`,
        dataSourceType: normalizeQualityDataSourceType(record.dbType),
        environment: record.environmentName || record.environment,
      } satisfies QualityDataSourceNode;
    })
    .filter((item): item is QualityDataSourceNode => Boolean(item));

export const groupQualityDataSourceNodes = (
  nodes: QualityDataSourceNode[] = [],
): QualityDataSourceGroup[] => {
  const groupMap = new Map<string, QualityDataSourceNode[]>();
  nodes.forEach((node) => {
    const group = groupMap.get(node.dataSourceType) || [];
    group.push(node);
    groupMap.set(node.dataSourceType, group);
  });

  return Array.from(groupMap.entries())
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([dataSourceType, groupNodes]) => ({
      dataSourceType,
      nodes: [...groupNodes].sort((left, right) =>
        left.dataSourceName.localeCompare(right.dataSourceName),
      ),
    }));
};

export const clampQualitySourceTreeWidth = (value: number) =>
  Math.min(
    QUALITY_SOURCE_TREE_MAX_WIDTH,
    Math.max(QUALITY_SOURCE_TREE_MIN_WIDTH, value),
  );

export const parseQualitySourceTreeWidth = (value?: string | null) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0
    ? clampQualitySourceTreeWidth(parsed)
    : QUALITY_SOURCE_TREE_DEFAULT_WIDTH;
};

export const qualityTableCandidateKey = (record: TableCandidateView) =>
  [record.databaseName || '', record.schemaName || '', record.tableName].join(
    '\u0001',
  );

export const getQualityMonitorDetailPath = (record: TableAssetView) =>
  record.monitorId
    ? `/data-quality/monitor/${encodeURIComponent(String(record.monitorId))}`
    : undefined;

export const getQualityMonitorCreatePath = (record: TableAssetView) => {
  const query = new URLSearchParams({
    dataSourceId: String(record.dataSourceId),
    dataSourceName: record.dataSourceName,
    databaseName: record.databaseName || '',
    schemaName: record.schemaName || '',
    tableName: record.tableName,
  });
  return `/data-quality/monitor/create?${query.toString()}`;
};
