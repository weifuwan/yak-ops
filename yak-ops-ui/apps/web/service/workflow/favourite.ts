import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

export interface WorkflowTaskFavoriteRecord {
  taskType: string;
  taskCategory: string;
  // Jackson may expose the Lombok boolean property as either "isCollection" or "collection".
  isCollection?: boolean;
  collection?: boolean;
}

const FAVOURITE_API_PREFIX = YAK_OPS_API_PREFIX + "/favourite";
const BACKGROUND_REQUEST_OPTIONS = { skipErrorHandler: true };

export const listWorkflowTaskFavorites = (
  signal?: AbortSignal,
): Promise<WorkflowTaskFavoriteRecord[]> =>
  HttpUtils.getData<WorkflowTaskFavoriteRecord[]>(
    FAVOURITE_API_PREFIX + "/taskTypes",
    { ...BACKGROUND_REQUEST_OPTIONS, signal },
  );

export const addWorkflowTaskFavorite = (taskType: string): Promise<boolean> =>
  HttpUtils.postData<boolean>(
    FAVOURITE_API_PREFIX + "/" + encodeURIComponent(taskType),
    undefined,
    BACKGROUND_REQUEST_OPTIONS,
  );

export const removeWorkflowTaskFavorite = (taskType: string): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(
    FAVOURITE_API_PREFIX + "/" + encodeURIComponent(taskType),
    undefined,
    BACKGROUND_REQUEST_OPTIONS,
  );
