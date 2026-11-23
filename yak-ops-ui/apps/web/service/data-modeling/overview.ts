import HttpUtils from "@/service/http/HttpUtils";
import { modelingBasePath } from "./shared/modeling-http";

export type ModelingOverviewStatistics = {
  dimension: number;
  detail: number;
  summary: number;
  application: number;
  total: number;
};

export type ModelingIndicatorStatistics = {
  atom: number;
  derived: number;
  composite: number;
  total: number;
};

export type ModelingLatestPublish = {
  id: string;
  name: string;
  assetType: string;
  assetTypeLabel: string;
  updateBy?: string;
  updateTime?: string | number;
};

export type ModelingOverview = {
  modelStatistics: ModelingOverviewStatistics;
  indicatorStatistics: ModelingIndicatorStatistics;
  totalAssets: number;
  latestPublished: ModelingLatestPublish[];
};

export const modelingOverviewApi = {
  get(projectCode: string) {
    return HttpUtils.getData<ModelingOverview>(
      modelingBasePath(projectCode) + "/overview",
    );
  },
};
