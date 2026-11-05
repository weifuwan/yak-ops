import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

import type {
  UserPreferenceFavoritePayload,
  UserPreferenceRecord,
  UserPreferenceScene,
} from "./types";

export type * from "./types";

const USER_PREFERENCE_API_PREFIX = `${YAK_OPS_API_PREFIX}/user-preferences`;

const BACKGROUND_PREFERENCE_OPTIONS = {
  skipErrorHandler: true,
};

export const listUserPreferences = (
  scene: UserPreferenceScene,
  signal?: AbortSignal,
): Promise<UserPreferenceRecord[]> =>
  HttpUtils.getData<UserPreferenceRecord[]>(
    `${USER_PREFERENCE_API_PREFIX}?scene=${encodeURIComponent(scene)}`,
    { ...BACKGROUND_PREFERENCE_OPTIONS, signal },
  );

export const updateUserPreferenceFavorite = (
  scene: UserPreferenceScene,
  itemKey: string,
  payload: UserPreferenceFavoritePayload,
): Promise<UserPreferenceRecord> =>
  HttpUtils.putData<UserPreferenceRecord>(
    `${USER_PREFERENCE_API_PREFIX}/${encodeURIComponent(scene)}/${encodeURIComponent(itemKey)}/favorite`,
    payload,
  );

export const recordUserPreferenceUsage = (
  scene: UserPreferenceScene,
  itemKey: string,
): Promise<UserPreferenceRecord> =>
  HttpUtils.postData<UserPreferenceRecord>(
    `${USER_PREFERENCE_API_PREFIX}/${encodeURIComponent(scene)}/${encodeURIComponent(itemKey)}/use`,
    undefined,
    BACKGROUND_PREFERENCE_OPTIONS,
  );
