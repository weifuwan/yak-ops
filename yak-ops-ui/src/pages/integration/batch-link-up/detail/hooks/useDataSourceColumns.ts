import { useEffect, useMemo, useState } from 'react';

import { dataSourceCatalogApi } from '@/services/data-source/legacy';

export interface DataSourceColumnOption {
  label: string;
  value: string;
  description?: string;
  primaryKey?: boolean;
  typeName?: string;
  jdbcType?: number;
}

const normalizeColumns = (data: any): DataSourceColumnOption[] => {
  const values = Array.isArray(data)
    ? data
    : Array.isArray(data?.bizData)
      ? data.bizData
      : Array.isArray(data?.records)
        ? data.records
        : [];

  return values
    .map((item: any) => {
      const value = item?.fieldName || item?.name || item?.value;
      if (!value) return null;
      const type = item?.fieldType || item?.typeName || item?.type;
      const comment = item?.fieldComment || item?.remarks || item?.description;
      return {
        value: String(value),
        label: String(value),
        description: [type, comment].filter(Boolean).join(' · ') || undefined,
        primaryKey: String(item?.fieldKey || '').toUpperCase() === 'PRI' || Boolean(item?.primaryKey),
        typeName: type ? String(type) : undefined,
        jdbcType: item?.jdbcType === undefined ? undefined : Number(item.jdbcType),
      };
    })
    .filter(Boolean) as DataSourceColumnOption[];
};

export default function useDataSourceColumns(
  dataSourceId: string,
  request: Record<string, unknown> | undefined,
) {
  const [columns, setColumns] = useState<DataSourceColumnOption[]>([]);
  const [loading, setLoading] = useState(false);
  const requestKey = useMemo(() => JSON.stringify(request || {}), [request]);

  useEffect(() => {
    if (!dataSourceId || !request || Object.keys(request).length === 0) {
      setColumns([]);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);

    dataSourceCatalogApi
      .listColumn(dataSourceId, request)
      .then((response) => {
        if (active) setColumns(normalizeColumns(response?.data));
      })
      .catch(() => {
        if (active) setColumns([]);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [dataSourceId, requestKey]);

  return { columns, loading };
}
