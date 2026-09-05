import {
  deleteDataService,
  listDataServiceDataSources,
  listDataServices,
  listRecentDataServiceLogs,
  setDataServiceEnabled,
  type DataServiceApi,
  type DataServiceCallLog,
  type DataSourceOption,
} from '@/services/data-service';
import { useAccess, useIntl } from '@umijs/max';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  buildDataServiceCallCounts,
  buildDataSourceNameMap,
  copyDataServiceText,
  filterDataServices,
  resolveDataSourceName,
  selectHotDataServices,
  selectRecommendedDataServices,
  selectRunningDataServices,
} from '../utils';

export const useDataServiceMarketplace = () => {
  const access = useAccess();
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const canObserve = access.hasPermission('data-service:observe');
  const canManage = access.hasPermission('data-service:manage');
  const canDelete = access.hasPermission('data-service:delete');
  const requestSequenceRef = useRef(0);
  const [services, setServices] = useState<DataServiceApi[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [logs, setLogs] = useState<DataServiceCallLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [submittedKeyword, setSubmittedKeyword] = useState('');
  const [detailTarget, setDetailTarget] = useState<DataServiceApi>();

  const loadMarketplace = useCallback(async () => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setLoading(true);

    try {
      const [serviceResult, dataSourceResult, logResult] = await Promise.all([
        listDataServices(),
        listDataServiceDataSources(),
        canObserve ? listRecentDataServiceLogs() : Promise.resolve([]),
      ]);
      if (requestSequence !== requestSequenceRef.current) return;

      const nextServices = serviceResult || [];
      setServices(nextServices);
      setDataSources(dataSourceResult || []);
      setLogs(logResult || []);
      setDetailTarget((current) =>
        current
          ? nextServices.find((service) => service.id === current.id)
          : undefined,
      );
    } catch (error) {
      if (requestSequence === requestSequenceRef.current) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({
                id: 'pages.dataService.message.loadFailed',
              }),
        );
      }
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setLoading(false);
      }
    }
  }, [canObserve]);

  useEffect(() => {
    void loadMarketplace();
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadMarketplace]);

  const dataSourceNameMap = useMemo(
    () => buildDataSourceNameMap(dataSources),
    [dataSources],
  );
  const callsByApiId = useMemo(
    () => buildDataServiceCallCounts(logs),
    [logs],
  );
  const runningServices = useMemo(
    () => selectRunningDataServices(services),
    [services],
  );
  const recommendedServices = useMemo(
    () => selectRecommendedDataServices(services),
    [services],
  );
  const hotServices = useMemo(
    () => (canObserve ? selectHotDataServices(services, callsByApiId) : []),
    [callsByApiId, canObserve, services],
  );
  const searchResults = useMemo(
    () => filterDataServices(services, submittedKeyword, dataSourceNameMap),
    [dataSourceNameMap, services, submittedKeyword],
  );

  const dataSourceName = useCallback(
    (dataSourceId?: number) =>
      resolveDataSourceName(dataSourceNameMap, dataSourceId),
    [dataSourceNameMap],
  );

  const changeKeyword = useCallback((value: string) => {
    setKeyword(value);
    if (!value) setSubmittedKeyword('');
  }, []);

  const search = useCallback(() => {
    setSubmittedKeyword(keyword.trim());
  }, [keyword]);

  const resetSearch = useCallback(() => {
    setKeyword('');
    setSubmittedKeyword('');
  }, []);

  const openDetail = useCallback((service: DataServiceApi) => {
    setDetailTarget(service);
  }, []);

  const closeDetail = useCallback(() => {
    setDetailTarget(undefined);
  }, []);

  const deleteService = useCallback(
    async (service: DataServiceApi) => {
      if (!canDelete) {
        message.warning(
          intlRef.current.formatMessage({
            id: 'pages.dataService.message.noDeletePermission',
          }),
        );
        return;
      }
      try {
        await deleteDataService(service.id);
        setDetailTarget((current) =>
          current?.id === service.id ? undefined : current,
        );
        message.success(
          intlRef.current.formatMessage({
            id: 'pages.dataService.message.deleted',
          }),
        );
        await loadMarketplace();
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({
                id: 'pages.dataService.message.deleteFailed',
              }),
        );
        throw error;
      }
    },
    [canDelete, loadMarketplace],
  );

  const toggleService = useCallback(
    async (service: DataServiceApi, enabled: boolean) => {
      if (!canManage) {
        message.warning(
          intlRef.current.formatMessage({
            id: 'pages.dataService.message.noManagePermission',
          }),
        );
        return;
      }
      try {
        await setDataServiceEnabled(service.id, enabled);
        message.success(
          intlRef.current.formatMessage({
            id: enabled
              ? 'pages.dataService.message.enabled'
              : 'pages.dataService.message.disabled',
          }),
        );
        await loadMarketplace();
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({
                id: 'pages.dataService.message.statusUpdateFailed',
              }),
        );
      }
    },
    [canManage, loadMarketplace],
  );

  const copyEndpoint = useCallback(async (endpoint: string) => {
    try {
      await copyDataServiceText(endpoint);
      message.success(
        intlRef.current.formatMessage({
          id: 'pages.dataService.message.endpointCopied',
        }),
      );
    } catch {
      message.warning(
        intlRef.current.formatMessage({
          id: 'pages.dataService.message.copyFailed',
        }),
      );
    }
  }, []);

  return {
    services,
    loading,
    keyword,
    submittedKeyword,
    detailTarget,
    callsByApiId,
    runningServices,
    recommendedServices,
    hotServices,
    searchResults,
    searching: Boolean(submittedKeyword.trim()),
    totalCalls: canObserve ? logs.length : undefined,
    canObserve,
    canManage,
    canDelete,
    dataSourceName,
    changeKeyword,
    search,
    resetSearch,
    openDetail,
    closeDetail,
    deleteService,
    toggleService,
    copyEndpoint,
  };
};
