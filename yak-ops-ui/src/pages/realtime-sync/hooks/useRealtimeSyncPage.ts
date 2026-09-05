import {
  deleteRealtimeSyncTask,
  getRealtimeRuntimeCapabilities,
  getRealtimeSyncTask,
  listRealtimeComputeEnvironments,
  listRealtimeDataSources,
  listRealtimeSyncEvents,
  listRealtimeSyncTasks,
  performRealtimeSyncAction,
  subscribeRealtimeSyncChanges,
  type ComputeEnvironmentOption,
  type DataSourceOption,
  type RealtimeAction,
  type RealtimeEvent,
  type RealtimeJob,
  type RealtimeJobChange,
  type RuntimeCapabilities,
} from '@/services/realtime-sync';
import { useIntl } from '@umijs/max';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  getRealtimeObservedStateLabels,
  REALTIME_SYNC_DEFAULT_PAGINATION,
  REALTIME_SYNC_FALLBACK_POLL_INTERVAL,
  REALTIME_SYNC_INITIAL_FILTERS,
  REALTIME_SYNC_START_POLL_ATTEMPTS,
  REALTIME_SYNC_START_POLL_INTERVAL,
} from '../constants';
import type {
  RealtimeFilterField,
  RealtimeFilterState,
  RealtimePageStateGroup,
  RealtimePaginationState,
} from '../types';
import {
  buildRealtimePageQuery,
  copyRealtimeText,
  createsRealtimeExecution,
  isValidRealtimeTaskId,
} from '../utils';

const sleep = (duration: number) =>
  new Promise((resolve) => window.setTimeout(resolve, duration));

export const useRealtimeSyncPage = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const requestSequenceRef = useRef(0);
  const [jobs, setJobs] = useState<RealtimeJob[]>([]);
  const [pagination, setPagination] = useState<RealtimePaginationState>(
    REALTIME_SYNC_DEFAULT_PAGINATION,
  );
  const [loading, setLoading] = useState(false);
  const [capabilities, setCapabilities] = useState<RuntimeCapabilities>({});
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [environments, setEnvironments] = useState<ComputeEnvironmentOption[]>([]);
  const [filterDraft, setFilterDraft] = useState<RealtimeFilterState>(
    REALTIME_SYNC_INITIAL_FILTERS,
  );
  const [filters, setFilters] = useState<RealtimeFilterState>(
    REALTIME_SYNC_INITIAL_FILTERS,
  );
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<RealtimeJob>();
  const [events, setEvents] = useState<RealtimeEvent[]>([]);
  const [streamConnected, setStreamConnected] = useState(false);

  const dataSourceMap = useMemo(
    () => new Map(dataSources.map((item) => [String(item.value), item])),
    [dataSources],
  );
  const environmentMap = useMemo(
    () => new Map(environments.map((item) => [item.id, item])),
    [environments],
  );

  const loadJobs = useCallback(async () => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setLoading(true);

    try {
      const page = await listRealtimeSyncTasks(
        buildRealtimePageQuery(filters, pagination),
      );
      if (requestSequence !== requestSequenceRef.current) return;

      setJobs(page?.records || []);
      setPagination((current) => ({
        ...current,
        total: page?.total || 0,
      }));
    } catch (error) {
      if (requestSequence === requestSequenceRef.current) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({ id: 'pages.realtimeSync.message.loadFailed' }),
        );
      }
    } finally {
      if (requestSequence === requestSequenceRef.current) setLoading(false);
    }
  }, [filters, pagination.current, pagination.pageSize]);

  const loadMetadata = useCallback(async () => {
    const [capabilityResult, sourceResult, environmentResult] =
      await Promise.allSettled([
        getRealtimeRuntimeCapabilities(),
        listRealtimeDataSources(),
        listRealtimeComputeEnvironments(),
      ]);

    setCapabilities(
      capabilityResult.status === 'fulfilled' ? capabilityResult.value || {} : {},
    );
    setDataSources(
      sourceResult.status === 'fulfilled' ? sourceResult.value || [] : [],
    );
    setEnvironments(
      environmentResult.status === 'fulfilled' ? environmentResult.value || [] : [],
    );
    if (environmentResult.status === 'rejected') {
      message.warning(
        intlRef.current.formatMessage({
          id: 'pages.realtimeSync.message.environmentUnavailable',
        }),
      );
    }
  }, []);

  useEffect(() => {
    void loadJobs();
  }, [loadJobs]);

  useEffect(() => {
    void loadMetadata();
  }, [loadMetadata]);

  useEffect(() => {
    let fallbackTimer: number | undefined;
    const stopFallback = () => {
      if (fallbackTimer !== undefined) window.clearInterval(fallbackTimer);
      fallbackTimer = undefined;
    };
    const startFallback = () => {
      if (fallbackTimer !== undefined) return;
      fallbackTimer = window.setInterval(
        () => void loadJobs(),
        REALTIME_SYNC_FALLBACK_POLL_INTERVAL,
      );
    };
    const refreshChangedJob = async (change: RealtimeJobChange) => {
      try {
        await loadJobs();
        if (detail?.id !== change.definitionId) return;
        const [nextDetail, nextEvents] = await Promise.all([
          getRealtimeSyncTask(change.definitionId),
          listRealtimeSyncEvents(change.definitionId),
        ]);
        setDetail(nextDetail);
        setEvents(nextEvents || []);
      } catch {
        startFallback();
      }
    };

    const unsubscribe = subscribeRealtimeSyncChanges({
      onOpen: () => {
        setStreamConnected(true);
        stopFallback();
      },
      onError: () => {
        setStreamConnected(false);
        startFallback();
      },
      onChange: (change) => void refreshChangedJob(change),
      onInvalidMessage: () => void loadJobs(),
    });

    return () => {
      unsubscribe();
      stopFallback();
    };
  }, [detail?.id, loadJobs]);

  const refresh = useCallback(async () => {
    await Promise.all([loadJobs(), loadMetadata()]);
  }, [loadJobs, loadMetadata]);

  const waitForStartResult = useCallback(async (id: number) => {
    for (
      let attempt = 0;
      attempt < REALTIME_SYNC_START_POLL_ATTEMPTS;
      attempt += 1
    ) {
      await sleep(REALTIME_SYNC_START_POLL_INTERVAL);
      try {
        const result = await getRealtimeSyncTask(id);
        if (
          ['RUNNING', 'FAILED', 'UNKNOWN', 'CONFLICT'].includes(
            result.observedState,
          )
        ) {
          return result.observedState;
        }
      } catch {
        // An accepted deployment must not become a failed action because one
        // status read was transiently unavailable.
      }
    }
    return 'STARTING';
  }, []);

  const performAction = useCallback(
    async (job: RealtimeJob, action: RealtimeAction) => {
      try {
        await performRealtimeSyncAction(job.id, action);

        if (createsRealtimeExecution(action)) {
          const state = await waitForStartResult(job.id);
          if (state === 'RUNNING') {
            if (action === 'restart-execution') {
              message.success(
                intlRef.current.formatMessage({
                  id: 'pages.realtimeSync.message.restartSuccess',
                }),
              );
            } else if (action === 'apply-published-version') {
              message.success(
                intlRef.current.formatMessage({
                  id: 'pages.realtimeSync.message.applyPublishedSuccess',
                }),
              );
            } else {
              message.success(
                intlRef.current.formatMessage({
                  id: 'pages.realtimeSync.message.startSuccess',
                }),
              );
            }
          } else if (state === 'STARTING') {
            message.warning(
              intlRef.current.formatMessage({
                id: 'pages.realtimeSync.message.starting',
              }),
            );
          } else {
            const stateLabels = getRealtimeObservedStateLabels(intlRef.current);
            message.warning(
              intlRef.current.formatMessage(
                { id: 'pages.realtimeSync.message.executionResult' },
                { state: stateLabels[state] || state },
              ),
            );
          }
        } else if (action === 'publish') {
          message.success(
            intlRef.current.formatMessage({
              id:
                job.desiredState === 'RUNNING'
                  ? 'pages.realtimeSync.message.publishRunning'
                  : 'pages.realtimeSync.message.publishSuccess',
            }),
          );
        } else {
          message.success(
            intlRef.current.formatMessage({
              id:
                action === 'validate'
                  ? 'pages.realtimeSync.message.validateSuccess'
                  : 'pages.realtimeSync.message.actionSuccess',
            }),
          );
        }

        await loadJobs();
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({ id: 'pages.realtimeSync.message.actionFailed' }),
        );
      }
    },
    [loadJobs, waitForStartResult],
  );

  const openDetail = useCallback(async (job: RealtimeJob) => {
    try {
      const [nextDetail, nextEvents] = await Promise.all([
        getRealtimeSyncTask(job.id),
        listRealtimeSyncEvents(job.id),
      ]);
      setDetail(nextDetail);
      setEvents(nextEvents || []);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({
              id: 'pages.realtimeSync.message.detailLoadFailed',
            }),
      );
    }
  }, []);

  const closeDetail = useCallback(() => {
    setDetail(undefined);
    setEvents([]);
  }, []);

  const deleteTask = useCallback(
    async (job: RealtimeJob) => {
      await deleteRealtimeSyncTask(job.id);
      message.success(
        intlRef.current.formatMessage({ id: 'pages.realtimeSync.message.deleted' }),
      );
      await loadJobs();
    },
    [loadJobs],
  );

  const copyTaskId = useCallback(async (value: string | number) => {
    try {
      await copyRealtimeText(value);
      message.success(
        intlRef.current.formatMessage({ id: 'pages.realtimeSync.message.copySuccess' }),
      );
    } catch {
      message.error(
        intlRef.current.formatMessage({ id: 'pages.realtimeSync.message.copyFailed' }),
      );
    }
  }, []);

  const updateFilterDraft = useCallback(
    <Field extends RealtimeFilterField>(
      field: Field,
      value: RealtimeFilterState[Field],
    ) => {
      setFilterDraft((current) => ({ ...current, [field]: value }));
    },
    [],
  );

  const applyFilters = useCallback((nextFilters: RealtimeFilterState) => {
    setFilterDraft(nextFilters);
    setFilters(nextFilters);
    setPagination((current) => ({ ...current, current: 1 }));
  }, []);

  const searchTasks = useCallback(() => {
    if (!isValidRealtimeTaskId(filterDraft.id)) {
      message.warning(
        intlRef.current.formatMessage({ id: 'pages.realtimeSync.message.invalidTaskId' }),
      );
      return false;
    }
    applyFilters({ ...filterDraft });
    return true;
  }, [applyFilters, filterDraft]);

  const changeStateGroup = useCallback(
    (stateGroup: RealtimePageStateGroup) => {
      applyFilters({ ...filterDraft, stateGroup });
    },
    [applyFilters, filterDraft],
  );

  const changeReleaseState = useCallback(
    (releaseState: RealtimeFilterState['releaseState']) => {
      applyFilters({ ...filterDraft, releaseState });
    },
    [applyFilters, filterDraft],
  );

  const resetFilters = useCallback(() => {
    applyFilters(REALTIME_SYNC_INITIAL_FILTERS);
  }, [applyFilters]);

  const changePagination = useCallback((current: number, pageSize: number) => {
    setPagination((previous) => ({ ...previous, current, pageSize }));
  }, []);

  return {
    jobs,
    pagination,
    loading,
    capabilities,
    dataSources,
    environments,
    dataSourceMap,
    environmentMap,
    filterDraft,
    filters,
    createOpen,
    detail,
    events,
    streamConnected,
    setCreateOpen,
    updateFilterDraft,
    searchTasks,
    changeStateGroup,
    changeReleaseState,
    resetFilters,
    changePagination,
    refresh,
    performAction,
    openDetail,
    closeDetail,
    deleteTask,
    copyTaskId,
  };
};
