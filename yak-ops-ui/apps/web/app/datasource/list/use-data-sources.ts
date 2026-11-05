import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useAuth } from "@/hooks/use-auth";
import { listDataSources } from "@/service/datasource";

interface DataSourceListParams {
  pageNo: number;
  pageSize: number;
  keyword: string;
  dbType?: string;
}

export const dataSourceListKeys = {
  all: ["data-source", "list"] as const,
  page: (userId: string, params: DataSourceListParams) =>
    [
      ...dataSourceListKeys.all,
      userId,
      params.pageNo,
      params.pageSize,
      params.keyword,
      params.dbType ?? "",
    ] as const,
};

export function useDataSources({ pageNo, pageSize, keyword, dbType }: DataSourceListParams) {
  const { currentUser } = useAuth();
  const [debouncedKeyword, setDebouncedKeyword] = useState(keyword.trim());

  useEffect(() => {
    const timer = window.setTimeout(
      () => setDebouncedKeyword(keyword.trim()),
      keyword.trim() ? 300 : 0,
    );
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const userId = currentUser?.id ?? "";
  return useQuery({
    queryKey: dataSourceListKeys.page(userId, {
      pageNo,
      pageSize,
      keyword: debouncedKeyword,
      dbType,
    }),
    queryFn: ({ signal }) =>
      listDataSources({ pageNo, pageSize, keyword: debouncedKeyword || undefined, dbType }, signal),
    enabled: Boolean(userId),
  });
}
