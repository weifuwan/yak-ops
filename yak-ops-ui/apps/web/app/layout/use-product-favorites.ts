import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";
import { useAuth } from "@/hooks/use-auth";
import {
  listUserPreferences,
  updateUserPreferenceFavorite,
  type UserPreferenceRecord,
} from "@/service/preference";
import { GLOBAL_PRODUCT_MENU } from "./navigation";
import {
  PRODUCT_MENU_SCENE,
  favoriteProducts,
  updateFavoritePreference,
  upsertFavoritePreference,
} from "./product-favorites";

export const favoriteQueryKey = (userId: string) =>
  ["product-menu-favorites", userId, PRODUCT_MENU_SCENE] as const;

/** Product menu preferences follow the signed-in user. */
export function useProductFavorites(open: boolean) {
  const { currentUser } = useAuth();
  const userId = currentUser?.id ?? "";
  const client = useQueryClient();
  const key = favoriteQueryKey(userId);
  const query = useQuery({
    queryKey: key,
    queryFn: ({ signal }) => listUserPreferences(PRODUCT_MENU_SCENE, signal),
    enabled: open && Boolean(userId),
    retry: false,
    staleTime: 30000,
    refetchOnWindowFocus: false,
  });
  const preferences = query.data ?? [];
  const mutation = useMutation({
    mutationKey: ["product-menu-favorite", userId],
    mutationFn: ({ productId, favorite }: { productId: string; favorite: boolean }) =>
      updateUserPreferenceFavorite(PRODUCT_MENU_SCENE, productId, { favorite }),
    onMutate: async ({ productId }) => {
      await client.cancelQueries({ queryKey: key });
      const previous = client.getQueryData<UserPreferenceRecord[]>(key) ?? [];
      client.setQueryData(key, updateFavoritePreference(previous, productId).preferences);
      return { previous };
    },
    onSuccess: (saved) => {
      client.setQueryData<UserPreferenceRecord[]>(key, (previous) =>
        upsertFavoritePreference(previous ?? [], saved),
      );
    },
    onError: (_error, _variables, context) => {
      if (context) client.setQueryData(key, context.previous);
      toast.error("收藏操作失败");
    },
    onSettled: () => {
      void client.invalidateQueries({ queryKey: key });
    },
  });

  const toggleFavorite = (productId: string) => {
    if (mutation.isPending || !userId) return;
    const next = updateFavoritePreference(preferences, productId);
    mutation.mutate({ productId, favorite: next.favorite });
  };
  return {
    favorites: favoriteProducts(preferences),
    favoriteProductIds: new Set(
      preferences.filter((item) => item.favorite).map((item) => item.itemKey),
    ),
    mutatingIds: mutation.isPending
      ? new Set(GLOBAL_PRODUCT_MENU.map((item) => item.id))
      : new Set<string>(),
    loading: query.isPending && open && Boolean(userId),
    error: query.isError,
    retry: () => {
      void query.refetch();
    },
    toggleFavorite,
  };
}
