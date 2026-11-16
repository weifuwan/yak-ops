import { createModelingResourceApi } from "../shared/modeling-http";

export const derivativeIndicatorApi = createModelingResourceApi("derivativeIndicator", {
  listEndpoint: "list",
  updateWithId: true,
  rollbackWithQuery: true,
});
