import type {
  DataServiceApi,
  DataServiceCallLog,
  DataSourceOption,
} from '@/services/data-service';

import {
  buildDataServiceCallCounts,
  buildDataSourceNameMap,
  describeDataServiceSource,
  filterDataServices,
  resolveDataSourceName,
  selectHotDataServices,
  selectRecommendedDataServices,
} from './utils';

const service = (
  id: number,
  values: Partial<DataServiceApi> = {},
): DataServiceApi => ({
  id,
  name: `service-${id}`,
  path: `/service-${id}`,
  runtimePath: `/api/runtime/service-${id}`,
  dataSourceId: id,
  sql: 'select 1',
  parameterNames: [],
  maxRows: 1000,
  timeoutSeconds: 30,
  enabled: true,
  authMode: 'NONE',
  ...values,
});

const log = (id: number, apiId: number): DataServiceCallLog => ({
  id,
  apiId,
  serviceName: `service-${apiId}`,
  servicePath: `/service-${apiId}`,
  callerType: 'PUBLIC',
  success: true,
  durationMs: 10,
  rowCount: 1,
});

describe('data service marketplace utilities', () => {
  it('builds data source labels with a stable fallback', () => {
    const options: DataSourceOption[] = [
      { value: '10', label: '订单库', dbType: 'MYSQL' },
    ];
    const labels = buildDataSourceNameMap(options);

    expect(resolveDataSourceName(labels, 10)).toBe('订单库');
    expect(resolveDataSourceName(labels, 11)).toBe('#11');
    expect(resolveDataSourceName(labels)).toBe('-');
  });

  it('counts recent calls and ranks hot services', () => {
    const calls = buildDataServiceCallCounts([
      log(1, 2),
      log(2, 2),
      log(3, 1),
    ]);
    const hot = selectHotDataServices(
      [service(1), service(2), service(3)],
      calls,
    );

    expect(calls.get(2)).toBe(2);
    expect(hot.map((item) => item.id)).toEqual([2, 1]);
  });

  it('recommends the most recently updated running services', () => {
    const result = selectRecommendedDataServices([
      service(1, { updateTime: '2026-08-01T10:00:00' }),
      service(2, { updateTime: '2026-08-03T10:00:00' }),
      service(3, {
        enabled: false,
        updateTime: '2026-08-05T10:00:00',
      }),
    ]);

    expect(result.map((item) => item.id)).toEqual([2, 1]);
  });

  it('falls back to disabled services when no API is running', () => {
    const result = selectRecommendedDataServices([
      service(1, {
        enabled: false,
        updateTime: '2026-08-01T10:00:00',
      }),
      service(2, {
        enabled: false,
        updateTime: '2026-08-02T10:00:00',
      }),
    ]);

    expect(result.map((item) => item.id)).toEqual([2, 1]);
  });

  it('searches API metadata and resolved data source names', () => {
    const labels = buildDataSourceNameMap([
      { value: '20', label: '会员 PostgreSQL' },
    ]);
    const services = [
      service(1, { name: '订单查询', dataSourceId: 10 }),
      service(2, {
        name: '会员查询',
        description: '提供会员画像',
        dataSourceId: 20,
      }),
    ];

    expect(filterDataServices(services, '画像', labels)).toEqual([
      services[1],
    ]);
    expect(filterDataServices(services, 'postgresql', labels)).toEqual([
      services[1],
    ]);
    expect(filterDataServices(services, '   ', labels)).toEqual([]);
  });

  it('describes current and legacy publication sources', () => {
    expect(
      describeDataServiceSource(
        service(1, {
          sourceType: 'DATA_DEVELOPMENT_DATA_SERVICE',
          sourceRevisionNo: 5,
        }),
        '订单库',
        '冻结来源',
      ),
    ).toEqual({
      primary: 'Data Service · DS R5',
      secondary: '订单库',
    });

    expect(
      describeDataServiceSource(
        service(2, {
          sourceType: 'DATA_DEVELOPMENT_RELEASE',
          sourceRevisionNo: 3,
        }),
        '历史库',
        '冻结来源',
      ),
    ).toEqual({
      primary: 'Legacy · SQL v3',
      secondary: '冻结来源 · 历史库',
      muted: true,
    });
  });
});
