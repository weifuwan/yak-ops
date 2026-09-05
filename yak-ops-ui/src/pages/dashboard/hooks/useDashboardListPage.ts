import {
  deleteDashboard,
  getDashboard,
  listDashboards,
  saveDashboardVersion,
  toDashboardDocument,
  type DashboardSummary,
} from '@/services/dashboard';
import { history, useIntl } from '@umijs/max';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { DASHBOARD_DEFAULT_PAGE_SIZE } from '../constants';
import type {
  DashboardStatusFilter,
  DashboardTimeRange,
} from '../types';
import {
  countDashboardLifecycles,
  dashboardEditPath,
  dashboardOpenPath,
  filterDashboardSummaries,
  paginateDashboardSummaries,
} from '../utils';

export const useDashboardListPage = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const requestSequenceRef = useRef(0);
  const [dashboards, setDashboards] = useState<DashboardSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeywordState] = useState('');
  const [status, setStatusState] = useState<DashboardStatusFilter>('all');
  const [timeRange, setTimeRangeState] = useState<DashboardTimeRange>('all');
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DASHBOARD_DEFAULT_PAGE_SIZE);
  const [renameTarget, setRenameTarget] = useState<DashboardSummary>();
  const [renameValue, setRenameValue] = useState('');
  const [renaming, setRenaming] = useState(false);
  const [deletingId, setDeletingId] = useState<string>();

  const loadDashboards = useCallback(async () => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setLoading(true);

    try {
      const nextDashboards = await listDashboards();
      if (requestSequence !== requestSequenceRef.current) return;
      setDashboards(nextDashboards || []);
      setRenameTarget((current) =>
        current
          ? nextDashboards.find((dashboard) => dashboard.id === current.id)
          : undefined,
      );
    } catch (error) {
      if (requestSequence !== requestSequenceRef.current) return;
      setDashboards([]);
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.dashboard.list.loadFailed' }),
      );
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void loadDashboards();
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadDashboards]);

  const lifecycleCounts = useMemo(
    () => countDashboardLifecycles(dashboards),
    [dashboards],
  );

  const filteredDashboards = useMemo(
    () =>
      filterDashboardSummaries(dashboards, {
        keyword,
        status,
        timeRange,
      }),
    [dashboards, keyword, status, timeRange],
  );

  const pagination = useMemo(
    () => paginateDashboardSummaries(filteredDashboards, page, pageSize),
    [filteredDashboards, page, pageSize],
  );

  const setKeyword = useCallback((value: string) => {
    setKeywordState(value);
    setPage(1);
  }, []);

  const setStatus = useCallback((value: DashboardStatusFilter) => {
    setStatusState(value);
    setPage(1);
  }, []);

  const setTimeRange = useCallback((value: DashboardTimeRange) => {
    setTimeRangeState(value);
    setPage(1);
  }, []);

  const resetFilters = useCallback(() => {
    setKeywordState('');
    setStatusState('all');
    setTimeRangeState('all');
    setPage(1);
  }, []);

  const changePage = useCallback(
    (nextPage: number, nextPageSize: number) => {
      setPage(nextPageSize === pageSize ? nextPage : 1);
      setPageSize(nextPageSize);
    },
    [pageSize],
  );

  const openDashboard = useCallback((dashboard: DashboardSummary) => {
    history.push(dashboardOpenPath(dashboard));
  }, []);

  const editDashboard = useCallback((dashboard: DashboardSummary) => {
    history.push(dashboardEditPath(dashboard.id));
  }, []);

  const createDashboard = useCallback(() => {
    history.push('/dashboard/new');
  }, []);

  const openRename = useCallback((dashboard: DashboardSummary) => {
    setRenameTarget(dashboard);
    setRenameValue(dashboard.name);
  }, []);

  const closeRename = useCallback(() => {
    if (renaming) return;
    setRenameTarget(undefined);
    setRenameValue('');
  }, [renaming]);

  const renameDashboard = useCallback(async () => {
    const name = renameValue.trim();
    if (!renameTarget || !name) {
      message.warning(
        intlRef.current.formatMessage({ id: 'pages.dashboard.list.rename.required' }),
      );
      return;
    }
    if (name === renameTarget.name) {
      setRenameTarget(undefined);
      setRenameValue('');
      return;
    }

    setRenaming(true);
    try {
      const detail = await getDashboard(renameTarget.id);
      const document = toDashboardDocument(detail);
      await saveDashboardVersion(renameTarget.id, { ...document, name });
      message.success(
        intlRef.current.formatMessage({ id: 'pages.dashboard.list.rename.success' }),
      );
      setRenameTarget(undefined);
      setRenameValue('');
      await loadDashboards();
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.dashboard.list.rename.failed' }),
      );
    } finally {
      setRenaming(false);
    }
  }, [loadDashboards, renameTarget, renameValue]);

  const removeDashboard = useCallback(
    async (dashboard: DashboardSummary) => {
      setDeletingId(dashboard.id);
      try {
        await deleteDashboard(dashboard.id);
        message.success(
          intlRef.current.formatMessage({ id: 'pages.dashboard.list.deleteSuccess' }),
        );
        await loadDashboards();
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({ id: 'pages.dashboard.list.deleteFailed' }),
        );
        throw error;
      } finally {
        setDeletingId(undefined);
      }
    },
    [loadDashboards],
  );

  return {
    dashboards,
    loading,
    keyword,
    status,
    timeRange,
    page: pagination.currentPage,
    pageSize,
    pageItems: pagination.records,
    filteredDashboards,
    lifecycleCounts,
    renameTarget,
    renameValue,
    renaming,
    deletingId,
    setKeyword,
    setStatus,
    setTimeRange,
    setRenameValue,
    resetFilters,
    changePage,
    refresh: loadDashboards,
    openDashboard,
    editDashboard,
    createDashboard,
    openRename,
    closeRename,
    renameDashboard,
    removeDashboard,
  };
};
