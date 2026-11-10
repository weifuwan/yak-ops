export type UserPreferenceScene =
  | "PRODUCT_MENU"
  | "DATASOURCE_CREATE_TYPE"
  | "DATA_DEVELOPMENT_RESOURCE";

export interface UserPreferenceRecord {
  scene: UserPreferenceScene;
  itemKey: string;
  favorite: boolean;
  sortOrder: number;
  useCount: number;
  lastUsedTime?: string | null;
}

export interface UserPreferenceFavoritePayload {
  favorite: boolean;
}
