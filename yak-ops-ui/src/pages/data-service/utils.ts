import {
  DATA_SERVICE_NODE_SOURCE,
  LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE,
  type DataServiceApi,
  type DataServiceCallLog,
  type DataSourceOption,
} from '@/services/data-service';

import {
  DATA_SERVICE_HOT_LIMIT,
  DATA_SERVICE_RECOMMENDED_LIMIT,
} from './constants';

export const dataServiceTimeValue = (value?: string) => {
  if (!value) return 0;
  const result = new Date(value).getTime();
  return Number.isNaN(result) ? 0 : result;
};

export const buildDataSourceNameMap = (
  dataSources: DataSourceOption[],
): ReadonlyMap<string, string> =>
  new Map(
    dataSources.map((item) => [String(item.value), String(item.label)]),
  );

export const resolveDataSourceName = (
  dataSourceNameMap: ReadonlyMap<string, string>,
  dataSourceId?: number,
) => {
  if (dataSourceId === undefined || dataSourceId === null) return '-';
  return dataSourceNameMap.get(String(dataSourceId)) || `#${dataSourceId}`;
};

export const buildDataServiceCallCounts = (
  logs: DataServiceCallLog[],
): ReadonlyMap<number, number> => {
  const result = new Map<number, number>();
  logs.forEach((log) => {
    result.set(log.apiId, (result.get(log.apiId) || 0) + 1);
  });
  return result;
};

export const selectRunningDataServices = (services: DataServiceApi[]) =>
  services.filter((service) => service.enabled);

export const selectRecommendedDataServices = (
  services: DataServiceApi[],
  limit = DATA_SERVICE_RECOMMENDED_LIMIT,
) => {
  const runningServices = selectRunningDataServices(services);
  const source = runningServices.length > 0 ? runningServices : services;

  return [...source]
    .sort(
      (left, right) =>
        dataServiceTimeValue(right.updateTime || right.createTime) -
        dataServiceTimeValue(left.updateTime || left.createTime),
    )
    .slice(0, limit);
};

export const selectHotDataServices = (
  services: DataServiceApi[],
  callsByApiId: ReadonlyMap<number, number>,
  limit = DATA_SERVICE_HOT_LIMIT,
) =>
  [...services]
    .filter((service) => (callsByApiId.get(service.id) || 0) > 0)
    .sort(
      (left, right) =>
        (callsByApiId.get(right.id) || 0) -
        (callsByApiId.get(left.id) || 0),
    )
    .slice(0, limit);

export const filterDataServices = (
  services: DataServiceApi[],
  keyword: string,
  dataSourceNameMap: ReadonlyMap<string, string>,
) => {
  const normalizedKeyword = keyword.trim().toLowerCase();
  if (!normalizedKeyword) return [];

  return services.filter((service) =>
    [
      service.name,
      service.path,
      service.runtimePath,
      service.description,
      service.sourceRef,
      resolveDataSourceName(dataSourceNameMap, service.dataSourceId),
    ]
      .filter(Boolean)
      .some((value) =>
        String(value).toLowerCase().includes(normalizedKeyword),
      ),
  );
};

export interface DataServiceSourcePresentation {
  primary: string;
  secondary?: string;
  muted?: boolean;
}

export const describeDataServiceSource = (
  service: DataServiceApi,
  dataSourceName: string,
  frozenSourceLabel: string,
): DataServiceSourcePresentation => {
  if (service.sourceType === DATA_SERVICE_NODE_SOURCE) {
    return {
      primary: `Data Service · DS R${service.sourceRevisionNo || '-'}`,
      secondary: dataSourceName,
    };
  }

  if (service.sourceType === LEGACY_DATA_DEVELOPMENT_RELEASE_SOURCE) {
    return {
      primary: `Legacy · SQL v${service.sourceRevisionNo || '-'}`,
      secondary: `${frozenSourceLabel} · ${dataSourceName}`,
      muted: true,
    };
  }

  return { primary: dataSourceName };
};

export const copyDataServiceText = async (value: string) => {
  if (
    typeof navigator !== 'undefined' &&
    navigator.clipboard &&
    typeof window !== 'undefined' &&
    window.isSecureContext
  ) {
    await navigator.clipboard.writeText(value);
    return;
  }

  if (typeof document === 'undefined') {
    throw new Error('Clipboard is not supported in the current environment');
  }

  const textarea = document.createElement('textarea');
  textarea.value = value;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  const copied = document.execCommand('copy');
  document.body.removeChild(textarea);

  if (!copied) throw new Error('Copy failed');
};
