import { useQuery } from "@tanstack/react-query";

import { useAuth } from "@/hooks/use-auth";
import { listUsers, type UserPageParams } from "@/service/user";

export const userQueryKeys = {
  all: ["management", "users"] as const,
  page: (userId: string, params: UserPageParams) =>
    [...userQueryKeys.all, userId, params.pageNo, params.pageSize, params.searchVal ?? ""] as const,
};

export function useUsers(params: UserPageParams) {
  const { currentUser } = useAuth();
  const normalized = { ...params, searchVal: params.searchVal?.trim() || undefined };
  const userId = currentUser?.id ?? "";

  return useQuery({
    queryKey: userQueryKeys.page(userId, normalized),
    queryFn: () => listUsers(normalized),
    enabled: Boolean(userId),
  });
}
