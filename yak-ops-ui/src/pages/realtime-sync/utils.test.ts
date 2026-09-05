import type {
  ComputeEnvironmentOption,
  RealtimeJob,
} from '@/services/realtime-sync';

import {
  buildRealtimePageQuery,
  createsRealtimeExecution,
  getRealtimeEditPath,
  getRealtimeStartAvailability,
  isValidRealtimeTaskId,
  preferredRealtimeEnvironmentId,
} from './utils';

const createJob = (patch: Partial<RealtimeJob> = {}): RealtimeJob => ({
  id: 101,
  name: 'orders-realtime',
  runtimeEnvironmentId: 1,
  releaseState: 'PUBLISHED',
  desiredState: 'STOPPED',
  observedState: 'STOPPED',
  definitionVersion: 2,
  publishedVersion: 2,
  publishedUpdateAvailable: false,
  createTime: '2026-08-01 00:00:00',
  updateTime: '2026-08-01 00:00:00',
  ...patch,
});

const createEnvironment = (
  patch: Partial<ComputeEnvironmentOption> = {},
): ComputeEnvironmentOption => ({
  id: 1,
  name: 'default-flink',
  engineType: 'FLINK_CDC',
  deploymentMode: 'REMOTE',
  submitterType: 'LOCAL',
  config: {
    restUrl: 'http://flink:8081',
    flinkHome: '/opt/flink',
    flinkCdcHome: '/opt/flink-cdc',
    flinkVersion: '1.20',
    flinkCdcVersion: '3.4',
  },
  enabled: true,
  defaultEnvironment: true,
  version: 1,
  ...patch,
});

describe('realtime sync page utilities', () => {
  it('builds a normalized service query from page state', () => {
    expect(
      buildRealtimePageQuery(
        {
          keyword: '  orders  ',
          id: ' 101 ',
          releaseState: 'PUBLISHED',
          stateGroup: 'RUNNING',
        },
        { current: 3, pageSize: 50 },
      ),
    ).toEqual({
      pageNo: 3,
      pageSize: 50,
      keyword: 'orders',
      id: 101,
      releaseState: 'PUBLISHED',
      stateGroup: 'RUNNING',
    });
  });

  it('omits ALL and rejects malformed ids before applying filters', () => {
    expect(isValidRealtimeTaskId('abc')).toBe(false);
    expect(isValidRealtimeTaskId(' 102 ')).toBe(true);
    expect(
      buildRealtimePageQuery(
        { id: 'abc', stateGroup: 'ALL' },
        { current: 1, pageSize: 20 },
      ),
    ).toEqual({
      pageNo: 1,
      pageSize: 20,
      keyword: undefined,
      id: undefined,
      releaseState: undefined,
      stateGroup: undefined,
    });
  });

  it('maps the task to its editor route', () => {
    expect(getRealtimeEditPath(createJob())).toBe(
      '/sync/realtime/101/detail?scene=edit',
    );
  });

  it('prefers the enabled default runtime environment', () => {
    expect(
      preferredRealtimeEnvironmentId([
        createEnvironment({ id: 1, defaultEnvironment: false }),
        createEnvironment({ id: 2, defaultEnvironment: true }),
      ]),
    ).toBe(2);
  });

  it('keeps deployment actions and start restrictions explicit', () => {
    expect(createsRealtimeExecution('start')).toBe(true);
    expect(createsRealtimeExecution('publish')).toBe(false);
    expect(
      getRealtimeStartAvailability(
        createJob({ publishedVersion: undefined, releaseState: 'DRAFT' }),
      ),
    ).toEqual({
      disabled: true,
      reason: 'NO_PUBLISHED_VERSION',
    });
    expect(
      getRealtimeStartAvailability(
        createJob(),
        createEnvironment({ enabled: false }),
      ),
    ).toEqual({
      disabled: true,
      reason: 'ENVIRONMENT_DISABLED',
      environmentName: 'default-flink',
    });
  });
});
