import type { StandardModule } from "./types";

export const standardsQueryKeys = {
  tree: (projectCode: string, module: StandardModule) => ["modeling", "standards", "tree", projectCode, module] as const,
  detail: (projectCode: string, module: StandardModule, nodeType?: string, id?: string) =>
    ["modeling", "standards", "detail", projectCode, module, nodeType, id] as const,
  detailPrefix: (projectCode: string, module: StandardModule) => ["modeling", "standards", "detail", projectCode, module] as const,
  directories: (projectCode: string, module: StandardModule, parentId?: string, pageNo?: number) =>
    ["modeling", "standards", "directories", projectCode, module, parentId, pageNo] as const,
  directoriesPrefix: (projectCode: string, module: StandardModule) => ["modeling", "standards", "directories", projectCode, module] as const,
  records: (projectCode: string, module: StandardModule, parentId?: string, pageNo?: number, keyword?: string) =>
    ["modeling", "standards", "records", projectCode, module, parentId, pageNo, keyword] as const,
  recordsPrefix: (projectCode: string, module: StandardModule) => ["modeling", "standards", "records", projectCode, module] as const,
  dictionary: (projectCode: string, pageNo: number, keyword: string) => ["modeling", "standards", "dictionary", projectCode, pageNo, keyword] as const,
  dictionaryPrefix: (projectCode: string) => ["modeling", "standards", "dictionary", projectCode] as const,
  dictionaryDetail: (projectCode: string, id: string) => ["modeling", "standards", "dictionary-detail", projectCode, id] as const,
};
