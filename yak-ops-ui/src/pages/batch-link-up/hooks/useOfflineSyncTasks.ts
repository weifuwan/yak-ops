import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import {
  listOfflineSyncTasks,
  type OfflineJobDefinitionVO,
} from '@/services/batch-link-up';
import { history, useIntl } from '@umijs/max';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { createDefaultOfflineSyncTimeRange } from '../constants';
import type {
  OfflineSyncPaginationState,
  OfflineSyncSearchField,
  OfflineSyncSearchState,
  OfflineSyncSelectedRowKeys,
} from '../types';
import {
  buildOfflineSyncPageQuery,
  buildOfflineSyncQueryString,
  copyTextToClipboard,
  parseOfflineSyncPaginationFromUrl,
  parseOfflineSyncSearchFromUrl,
} from '../utils';

const currentLocationSearch = () =>
  typeof window === 'undefined' ? '' : window.location.search;

export const useOfflineSyncTasks = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const { currentProject } = useSecurityProject();
  const currentProjectId = currentProject?.id;
  const requestSequenceRef = useRef(0);
  const activeProjectIdRef = useRef(currentProjectId);
  const previousProjectIdRef = useRef(currentProjectId);
  activeProjectIdRef.current = currentProjectId;

  const initialSearchState = useMemo(
    () => parseOfflineSyncSearchFromUrl(currentLocationSearch()),
    [],
  );

  const [records, setRecords] = useState<OfflineJobDefinitionVO[]>([]);
  const [search, setSearch] = useState<OfflineSyncSearchState>(initialSearchState);
  const [filterDraft, setFilterDraft] =
    useState<OfflineSyncSearchState>(initialSearchState);
  const [pagination, setPagination] = useState<OfflineSyncPaginationState>(() =>
    parseOfflineSyncPaginationFromUrl(currentLocationSearch()),
  );
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] =
    useState<OfflineSyncSelectedRowKeys>([]);
  const [refreshVersion, setRefreshVersion] = useState(0);

  const refresh = useCallback(() => {
    setRefreshVersion((value) => value + 1);
  }, []);

  const loadTasks = useCallback(async () => {
    const requestProjectId = currentProjectId;
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;

    if (!requestProjectId) {
      setRecords([]);
      setPagination((current) =>
        current.total === 0 ? current : { ...current, total: 0 },
      );
      setLoading(false);
      return;
    }

    setLoading(true);

    try {
      const result = await listOfflineSyncTasks(
        buildOfflineSyncPageQuery(search, pagination),
      );
      if (
        requestSequence !== requestSequenceRef.current ||
        requestProjectId !== activeProjectIdRef.current
      ) {
        return;
      }

      setRecords(result?.bizData || []);
      setPagination((current) => ({
        ...current,
        total: result?.pagination?.total || 0,
      }));
    } catch (error) {
      if (
        requestSequence === requestSequenceRef.current &&
        requestProjectId === activeProjectIdRef.current
      ) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({
                id: 'pages.batchLinkUp.message.queryFailed',
              }),
        );
      }
    } finally {
      if (
        requestSequence === requestSequenceRef.current &&
        requestProjectId === activeProjectIdRef.current
      ) {
        setLoading(false);
      }
    }
  }, [
    currentProjectId,
    pagination.current,
    pagination.pageSize,
    refreshVersion,
    search,
  ]);

  useEffect(() => {
    if (previousProjectIdRef.current === currentProjectId) return;
    previousProjectIdRef.current = currentProjectId;
    requestSequenceRef.current += 1;
    setRecords([]);
    setSelectedRowKeys([]);
    setPagination((current) => {
      if (current.current === 1 && current.total === 0) return current;
      return { ...current, current: 1, total: 0 };
    });
  }, [currentProjectId]);

  useEffect(() => {
    const query = buildOfflineSyncQueryString(search, pagination);
    history.replace({ search: query ? `?${query}` : '' });
  }, [pagination.current, pagination.pageSize, search]);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  const updateFilterDraft = useCallback(
    (
      field: OfflineSyncSearchField,
      value: OfflineSyncSearchState[OfflineSyncSearchField],
    ) => {
      setFilterDraft((current) => ({ ...current, [field]: value }));
    },
    [],
  );

  const applySearch = useCallback((nextSearch: OfflineSyncSearchState) => {
    setFilterDraft(nextSearch);
    setSearch(nextSearch);
    setPagination((current) => ({ ...current, current: 1 }));
  }, []);

  const searchTasks = useCallback(() => {
    applySearch({ ...filterDraft });
  }, [applySearch, filterDraft]);

  const resetFilters = useCallback(() => {
    applySearch({ createTime: createDefaultOfflineSyncTimeRange() });
  }, [applySearch]);

  const changeStatus = useCallback(
    (value: string) => {
      applySearch({
        ...filterDraft,
        status: value === 'ALL' ? undefined : value,
      });
    },
    [applySearch, filterDraft],
  );

  const changeQuickFilter = useCallback(
    (
      field: OfflineSyncSearchField,
      value: OfflineSyncSearchState[OfflineSyncSearchField],
    ) => {
      applySearch({ ...filterDraft, [field]: value });
    },
    [applySearch, filterDraft],
  );

  const resetAdvancedFilters = useCallback(() => {
    applySearch({
      ...filterDraft,
      id: undefined,
      sinkType: undefined,
      sourceTable: undefined,
      sinkTable: undefined,
    });
  }, [applySearch, filterDraft]);

  const changePagination = useCallback((current: number, pageSize: number) => {
    setPagination((previous) => ({ ...previous, current, pageSize }));
  }, []);

  const handleCreated = useCallback(() => {
    setCreateOpen(false);
    setSelectedRowKeys([]);
    if (pagination.current === 1) {
      refresh();
      return;
    }
    setPagination((current) => ({ ...current, current: 1 }));
  }, [pagination.current, refresh]);

  const copyTaskId = useCallback(async (value: string | number) => {
    try {
      await copyTextToClipboard(value);
      message.success(
        intlRef.current.formatMessage({
          id: 'pages.batchLinkUp.message.copyTaskIdSuccess',
        }),
      );
    } catch {
      message.error(
        intlRef.current.formatMessage({
          id: 'pages.batchLinkUp.message.copyFailed',
        }),
      );
    }
  }, []);

  const currentStatus = filterDraft.status || search.status || 'ALL';
  const advancedFilterCount = [
    filterDraft.id,
    filterDraft.sinkType,
    filterDraft.sourceTable,
    filterDraft.sinkTable,
  ].filter(Boolean).length;

  return {
    records,
    search,
    filterDraft,
    pagination,
    loading,
    createOpen,
    selectedRowKeys,
    currentStatus,
    advancedFilterCount,
    setCreateOpen,
    setSelectedRowKeys,
    updateFilterDraft,
    searchTasks,
    resetFilters,
    changeStatus,
    changeQuickFilter,
    resetAdvancedFilters,
    changePagination,
    handleCreated,
    copyTaskId,
    refresh,
  };
};
