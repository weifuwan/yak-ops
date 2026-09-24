import {
  deleteDataSource as deleteDataSourceById,
  getDataSource,
  getDataSourceSummary,
  listDataSources,
  testDataSourceConnection as testDataSourceConnectionById,
  type DataSourceId,
  type DataSourcePageParams,
  type DataSourceRecord,
  type DataSourceSummary,
  type PaginationInfo,
} from '@/service/datasource';
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  EMPTY_DATA_SOURCE_SUMMARY,
  PAGE_DEFAULT_PAGINATION,
} from '../constants';
import type { DataSourcePermissions } from '../types';
import { dataSourceRecordKey } from '../utils';

const DATA_SOURCE_PERMISSIONS: DataSourcePermissions = {
  canCreate: true,
  canUpdate: true,
  canDelete: true,
  canTest: true,
};

export const useDatasources = () => {
  const requestSequenceRef = useRef(0);
  const permissions = DATA_SOURCE_PERMISSIONS;

  const [loading, setLoading] = useState(false);
  const [records, setRecords] = useState<DataSourceRecord[]>([]);
  const [summary, setSummary] = useState<DataSourceSummary>(
    EMPTY_DATA_SOURCE_SUMMARY,
  );
  const [pagination, setPagination] = useState<PaginationInfo>(
    PAGE_DEFAULT_PAGINATION,
  );
  const [keyword, setKeywordState] = useState('');
  const [dbType, setDbTypeState] = useState<string>();
  const [environment, setEnvironmentState] = useState<string>();
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [testingId, setTestingId] = useState('');
  const [editingId, setEditingId] = useState('');

  const hasActiveFilters = Boolean(keyword.trim() || dbType || environment);

  const resetPage = useCallback(() => {
    setPagination((current) => ({ ...current, pageNo: 1 }));
  }, []);

  const refresh = useCallback(() => {
    setRefreshVersion((value) => value + 1);
  }, []);

  const loadPage = useCallback(async () => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setLoading(true);

    const params: DataSourcePageParams = {
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      keyword: keyword.trim() || undefined,
      dbType,
      environment,
    };

    try {
      const [pageResult, summaryResult] = await Promise.allSettled([
        listDataSources(params),
        getDataSourceSummary(),
      ]);

      if (requestSequence !== requestSequenceRef.current) return;

      if (pageResult.status === 'fulfilled') {
        const pageData = pageResult.value;
        const nextRecords = pageData?.bizData || [];
        const nextPagination =
          pageData?.pagination || PAGE_DEFAULT_PAGINATION;

        if (
          nextRecords.length === 0 &&
          nextPagination.total > 0 &&
          params.pageNo > 1
        ) {
          setPagination((current) => ({
            ...current,
            pageNo: Math.max(1, params.pageNo - 1),
            total: nextPagination.total,
          }));
        } else {
          setRecords(nextRecords);
          setPagination(nextPagination);
        }
      }

      if (summaryResult.status === 'fulfilled') {
        setSummary(summaryResult.value || EMPTY_DATA_SOURCE_SUMMARY);
      }
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setLoading(false);
      }
    }
  }, [
    dbType,
    environment,
    keyword,
    pagination.pageNo,
    pagination.pageSize,
    refreshVersion,
  ]);

  useEffect(() => {
    const timer = window.setTimeout(
      () => void loadPage(),
      keyword.trim() ? 300 : 0,
    );
    return () => window.clearTimeout(timer);
  }, [keyword, loadPage]);

  const setKeyword = useCallback(
    (value: string) => {
      setKeywordState(value);
      resetPage();
    },
    [resetPage],
  );

  const setDbType = useCallback(
    (value?: string) => {
      setDbTypeState(value);
      resetPage();
    },
    [resetPage],
  );

  const setEnvironment = useCallback(
    (value?: string) => {
      setEnvironmentState(value);
      resetPage();
    },
    [resetPage],
  );

  const resetFilters = useCallback(() => {
    setKeywordState('');
    setDbTypeState(undefined);
    setEnvironmentState(undefined);
    resetPage();
  }, [resetPage]);

  const changePage = useCallback((pageNo: number, pageSize: number) => {
    setPagination((current) => ({ ...current, pageNo, pageSize }));
  }, []);

  const loadRecordForEdit = useCallback(
    async (record: DataSourceRecord): Promise<DataSourceRecord | undefined> => {
      if (
        !permissions.canUpdate ||
        record.id === undefined ||
        record.id === null ||
        editingId
      ) {
        return undefined;
      }

      const id = dataSourceRecordKey(record.id);
      setEditingId(id);
      try {
        return await getDataSource(record.id);
      } finally {
        setEditingId('');
      }
    },
    [editingId, permissions.canUpdate],
  );

  const removeRecord = useCallback(
    async (id: DataSourceId): Promise<boolean> => {
      if (!permissions.canDelete) return false;
      await deleteDataSourceById(id);
      refresh();
      return true;
    },
    [permissions.canDelete, refresh],
  );

  const testRecord = useCallback(
    async (record: DataSourceRecord): Promise<boolean> => {
      if (
        !permissions.canTest ||
        record.id === undefined ||
        record.id === null ||
        testingId
      ) {
        return false;
      }

      const id = dataSourceRecordKey(record.id);
      setTestingId(id);
      try {
        await testDataSourceConnectionById(record.id);
        refresh();
        return true;
      } finally {
        setTestingId('');
      }
    },
    [permissions.canTest, refresh, testingId],
  );

  return {
    loading,
    records,
    summary,
    pagination,
    keyword,
    dbType,
    environment,
    hasActiveFilters,
    permissions,
    testingId,
    editingId,
    setKeyword,
    setDbType,
    setEnvironment,
    resetFilters,
    changePage,
    refresh,
    loadRecordForEdit,
    removeRecord,
    testRecord,
  };
};
