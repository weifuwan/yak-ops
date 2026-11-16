import { useQuery } from "@tanstack/react-query";

import { listUsers, type UserRecord } from "@/service/user";

async function loadOwnerOptions(): Promise<UserRecord[]> {
  const firstPage = await listUsers({ pageNo: 1, pageSize: 100 });
  if (firstPage.totalPage <= 1) return firstPage.records;

  const remainingPages = await Promise.all(
    Array.from({ length: firstPage.totalPage - 1 }, (_, index) =>
      listUsers({ pageNo: index + 2, pageSize: 100 }),
    ),
  );

  return [...firstPage.records, ...remainingPages.flatMap((page) => page.records)];
}

export function useAtomIndicatorOwnerOptions(projectCode: string) {
  return useQuery({
    queryKey: ["modeling", "atom-indicator-owner-options", projectCode],
    queryFn: async () => {
      try {
        return await loadOwnerOptions();
      } catch {
        return [];
      }
    },
    enabled: Boolean(projectCode),
  });
}
