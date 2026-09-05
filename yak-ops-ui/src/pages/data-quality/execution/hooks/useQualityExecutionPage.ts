import {
  listQualityExecutionWorkspace,
  listQualityRuleExecutionWorkspace,
  type CheckResult,
  type ExecutionStatus,
  type ExecutionWorkspaceListItem,
  type ExecutionWorkspaceQuery,
  type RuleExecutionWorkspaceListItem,
  type RuleScope,
  type TriggerType,
} from '@/services/data-quality';
import { useIntl } from '@umijs/max';
import { message } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type { ExecutionViewMode } from '../components/ExecutionRecordTable';

export interface ExecutionAdvancedFilterState {
  objectKeyword: string;
  executionStatus?: ExecutionStatus;
  checkResult?: CheckResult;
  triggerType?: TriggerType;
  hasIssues?: boolean;
  dimension?: string;
  scope?: RuleScope;
}

const emptyAdvancedFilters = (): ExecutionAdvancedFilterState => ({
  objectKeyword: '',
  executionStatus: undefined,
  checkResult: undefined,
  triggerType: undefined,
  hasIssues: undefined,
  dimension: undefined,
  scope: undefined,
});

const defaultDateRange = (): [Dayjs, Dayjs] => [
  dayjs().subtract(7, 'day'),
  dayjs(),
];

export const useQualityExecutionPage = (dataSourceId?: number) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const [executionRecords, setExecutionRecords] = useState<
    ExecutionWorkspaceListItem[]
  >([]);
  const [ruleRecords, setRuleRecords] = useState<
    RuleExecutionWorkspaceListItem[]
  >([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [keywordDraft, setKeywordDraft] = useState('');
  const [keyword, setKeyword] = useState('');
  const [filters, setFilters] = useState<ExecutionAdvancedFilterState>(
    emptyAdvancedFilters,
  );
  const [draftFilters, setDraftFilters] =
    useState<ExecutionAdvancedFilterState>(emptyAdvancedFilters);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [viewMode, setViewMode] = useState<ExecutionViewMode>('RULE');
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(
    defaultDateRange,
  );

  const load = useCallback(
    async (requestedCurrent = 1, requestedPageSize = pageSize) => {
      if (!dataSourceId) {
        setExecutionRecords([]);
        setRuleRecords([]);
        setTotal(0);
        setCurrent(1);
        return;
      }

      setLoading(true);
      try {
        const query: ExecutionWorkspaceQuery = {
          current: requestedCurrent,
          pageSize: requestedPageSize,
          dataSourceId,
          keyword: keyword || undefined,
          objectKeyword: filters.objectKeyword || undefined,
          executionStatus: filters.executionStatus,
          checkResult: filters.checkResult,
          triggerType: filters.triggerType,
          hasIssues: filters.hasIssues,
          dimension: filters.dimension,
          scope: filters.scope,
          queuedAfter: dateRange?.[0]
            ?.startOf('day')
            .format('YYYY-MM-DD HH:mm:ss'),
          queuedBefore: dateRange?.[1]
            ?.endOf('day')
            .format('YYYY-MM-DD HH:mm:ss'),
        };

        if (viewMode === 'RULE') {
          const result = await listQualityRuleExecutionWorkspace(query);
          setRuleRecords(result.records || []);
          setExecutionRecords([]);
          setTotal(result.total);
          setCurrent(result.current);
          setPageSize(result.pageSize);
        } else {
          const result = await listQualityExecutionWorkspace(query);
          setExecutionRecords(result.records || []);
          setRuleRecords([]);
          setTotal(result.total);
          setCurrent(result.current);
          setPageSize(result.pageSize);
        }
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({
                id: 'pages.dataQuality.execution.loadFailed',
              }),
        );
      } finally {
        setLoading(false);
      }
    },
    [dataSourceId, dateRange, filters, keyword, pageSize, viewMode],
  );

  useEffect(() => {
    void load(1);
  }, [load]);

  useEffect(() => {
    const records = viewMode === 'RULE' ? ruleRecords : executionRecords;
    if (
      !records.some((record) =>
        ['WAITING', 'RUNNING'].includes(record.executionStatus),
      )
    ) {
      return;
    }

    const timer = window.setInterval(
      () => void load(current, pageSize),
      3000,
    );
    return () => window.clearInterval(timer);
  }, [current, executionRecords, load, pageSize, ruleRecords, viewMode]);

  const advancedFilterCount = useMemo(
    () =>
      [
        filters.objectKeyword,
        filters.executionStatus,
        filters.checkResult,
        filters.triggerType,
        filters.dimension,
        filters.scope,
        filters.hasIssues === undefined ? undefined : String(filters.hasIssues),
      ].filter(Boolean).length,
    [filters],
  );

  const applySearch = useCallback(() => {
    setKeyword(keywordDraft.trim());
  }, [keywordDraft]);

  const applyAdvancedSearch = useCallback(() => {
    setFilters({
      ...draftFilters,
      objectKeyword: draftFilters.objectKeyword.trim(),
    });
    setAdvancedOpen(false);
  }, [draftFilters]);

  const resetFilters = useCallback(() => {
    const empty = emptyAdvancedFilters();
    setKeywordDraft('');
    setKeyword('');
    setDraftFilters(empty);
    setFilters(empty);
    setDateRange(defaultDateRange());
    setAdvancedOpen(false);
  }, []);

  const changePage = useCallback(
    (nextCurrent: number, nextPageSize: number) => {
      if (nextPageSize !== pageSize) {
        setPageSize(nextPageSize);
        return;
      }
      void load(nextCurrent, nextPageSize);
    },
    [load, pageSize],
  );

  return {
    executionRecords,
    ruleRecords,
    total,
    current,
    pageSize,
    loading,
    keywordDraft,
    setKeywordDraft,
    draftFilters,
    setDraftFilters,
    advancedOpen,
    setAdvancedOpen,
    advancedFilterCount,
    viewMode,
    setViewMode,
    dateRange,
    setDateRange,
    load,
    applySearch,
    applyAdvancedSearch,
    resetFilters,
    changePage,
  };
};
