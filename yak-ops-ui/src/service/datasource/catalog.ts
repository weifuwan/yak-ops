import HttpUtils from '@/utils/HttpUtils';

import type { DataSourceId } from './types';

const DATA_SOURCE_CATALOG_API_PREFIX = '/api/v1/data-source/catalog';

const queryString = (params: Record<string, unknown>) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const result = search.toString();
  return result ? `?${result}` : '';
};

export interface DataSourceCatalogColumn {
  name: string;
  typeName?: string;
  jdbcType?: number;
  size?: number;
  scale?: number;
  nullable?: boolean;
  ordinalPosition?: number;
  primaryKey?: boolean;
  remarks?: string;
}

export const listDataSourceColumns = (
  id: DataSourceId,
  database: string | undefined,
  schema: string | undefined,
  table: string,
): Promise<DataSourceCatalogColumn[]> =>
  HttpUtils.getData<DataSourceCatalogColumn[]>(
    `${DATA_SOURCE_CATALOG_API_PREFIX}/${id}/columns${queryString({
      database,
      schema,
      table,
    })}`,
  );
