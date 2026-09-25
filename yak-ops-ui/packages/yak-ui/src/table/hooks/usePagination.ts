import { useState } from "react";

import type { TablePaginationConfig } from "../interface";

const DEFAULT_PAGE = 1;
const DEFAULT_PAGE_SIZE = 10;

export interface ResolvedTablePagination {
  page: number;
  pageSize: number;
  total: number;
  pageSizeOptions?: readonly number[];
  disabled: boolean;
  showSizeChanger: boolean;
  showQuickJumper: boolean;
  renderTotal?: TablePaginationConfig["renderTotal"];
  onChange: (page: number, pageSize: number) => void;
}

export interface TablePaginationResult<RecordType extends object> {
  data: readonly RecordType[];
  pagination: ResolvedTablePagination | null;
}

export function usePagination<RecordType extends object>(
  pagination: false | TablePaginationConfig | undefined,
  dataSource: readonly RecordType[],
): TablePaginationResult<RecordType> {
  const config = pagination === false || pagination == null ? undefined : pagination;
  const [innerPage, setInnerPage] = useState(config?.current ?? DEFAULT_PAGE);
  const [innerPageSize, setInnerPageSize] = useState(config?.pageSize ?? DEFAULT_PAGE_SIZE);

  if (!config) {
    return {
      data: dataSource,
      pagination: null,
    };
  }

  const pageSize = Math.max(1, config.pageSize ?? innerPageSize);
  const total = Math.max(0, config.total ?? dataSource.length);
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const page = Math.min(Math.max(config.current ?? innerPage, 1), totalPages);
  const serverPagination = config.total != null;

  const data = serverPagination
    ? dataSource
    : dataSource.slice((page - 1) * pageSize, page * pageSize);

  const onChange = (nextPage: number, nextPageSize: number) => {
    if (config.current == null) setInnerPage(nextPage);
    if (config.pageSize == null) setInnerPageSize(nextPageSize);
    config.onChange?.(nextPage, nextPageSize);
  };

  return {
    data,
    pagination: {
      page,
      pageSize,
      total,
      pageSizeOptions: config.pageSizeOptions,
      disabled: config.disabled ?? false,
      showSizeChanger: config.showSizeChanger ?? false,
      showQuickJumper: config.showQuickJumper ?? false,
      renderTotal: config.renderTotal,
      onChange,
    },
  };
}
