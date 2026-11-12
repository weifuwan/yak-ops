import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

export interface WorkflowResourceNode {
  name?: string;
  currentDir?: string;
  fullName?: string;
  description?: string;
  isDirctory?: boolean;
  dirctory?: boolean;
  isDirectory?: boolean;
  children?: WorkflowResourceNode[];
}

export interface ResourceItemRecord {
  alias?: string;
  userName?: string;
  fileName?: string;
  fullName: string;
  isDirectory?: boolean;
  directory?: boolean;
  isDirctory?: boolean;
  type?: string;
  size?: number;
  createTime?: string | number | Date;
  updateTime?: string | number | Date;
}

export interface ResourcePage<T> {
  totalList: T[];
  total: number;
  totalPage?: number;
  pageNo?: number;
  pageSize?: number;
  currentPage?: number;
}

const RESOURCE_API = `${YAK_OPS_API_PREFIX}/resources`;

const toQuery = (values: Record<string, string | number | undefined>) => {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined) params.set(key, String(value));
  });
  return params.toString();
};

export const listWorkflowResources = (
  signal?: AbortSignal,
): Promise<WorkflowResourceNode[]> =>
  HttpUtils.getData<WorkflowResourceNode[]>(
    `${RESOURCE_API}/list?type=FILE`,
    { signal },
  );

export const getResourceBaseDir = (signal?: AbortSignal): Promise<string> =>
  HttpUtils.getData<string>(`${RESOURCE_API}/base-dir?type=FILE`, { signal });

export const listResourceItems = (
  params: {
    fullName: string;
    searchVal?: string;
    pageNo: number;
    pageSize: number;
  },
  signal?: AbortSignal,
): Promise<ResourcePage<ResourceItemRecord>> =>
  HttpUtils.getData<ResourcePage<ResourceItemRecord>>(
    `${RESOURCE_API}?${toQuery({
      fullName: params.fullName,
      type: "FILE",
      // The current backend filters with String.contains(), so an empty string
      // must be sent instead of omitting searchVal.
      searchVal: params.searchVal ?? "",
      pageNo: params.pageNo,
      pageSize: params.pageSize,
    })}`,
    { signal },
  );

export const createResourceDirectory = (
  name: string,
  currentDir: string,
): Promise<void> =>
  HttpUtils.postData<void>(
    `${RESOURCE_API}/directory?${toQuery({
      type: "FILE",
      name,
      currentDir,
    })}`,
  );

export const uploadResourceFile = async (
  file: File,
  currentDir: string,
): Promise<void> => {
  const formData = new FormData();
  formData.append("type", "FILE");
  formData.append("name", file.name);
  formData.append("file", file);
  formData.append("currentDir", currentDir);

  const response = await HttpUtils.postForm<void>(RESOURCE_API, formData);
  HttpUtils.unwrap(response);
};

export interface ResourceFileContent {
  content: string;
}

export const fetchResourceFileContent = (
  fullName: string,
  skipLineNum = 0,
  limit = -1,
): Promise<ResourceFileContent> =>
  HttpUtils.getData<ResourceFileContent>(
    `${RESOURCE_API}/view?${toQuery({ fullName, skipLineNum, limit })}`,
  );

export const createResourceFile = async (
  fileName: string,
  suffix: string,
  content: string,
  currentDir: string,
): Promise<void> => {
  const formData = new FormData();
  formData.append("type", "FILE");
  formData.append("fileName", fileName);
  formData.append("suffix", suffix);
  formData.append("content", content);
  formData.append("currentDir", currentDir);

  const response = await HttpUtils.postForm<void>(
    `${RESOURCE_API}/online-create`,
    formData,
  );
  HttpUtils.unwrap(response);
};

export const updateResourceFileContent = (
  fullName: string,
  content: string,
): Promise<void> => {
  const formData = new FormData();
  formData.append("fullName", fullName);
  formData.append("content", content);
  return HttpUtils.putData<void>(`${RESOURCE_API}/update-content`, formData);
};

export const replaceResourceFile = (
  fullName: string,
  name: string,
  file: File,
): Promise<void> => {
  const formData = new FormData();
  formData.append("file", file);
  return HttpUtils.putData<void>(
    `${RESOURCE_API}?${toQuery({ fullName, name })}`,
    formData,
  );
};

export const renameResource = (
  fullName: string,
  name: string,
): Promise<void> =>
  HttpUtils.putData<void>(
    `${RESOURCE_API}?${toQuery({ fullName, name })}`,
  );

export const deleteResource = (fullName: string): Promise<void> =>
  HttpUtils.deleteData<void>(
    `${RESOURCE_API}?${toQuery({ fullName })}`,
  );

export const downloadResource = (fullName: string): Promise<Blob> =>
  HttpUtils.download(
    `${RESOURCE_API}/download?${toQuery({ fullName })}`,
  );
