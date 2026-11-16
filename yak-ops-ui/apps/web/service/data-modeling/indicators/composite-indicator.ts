import { createModelingResourceApi } from "../shared/modeling-http";

export const compositeIndicatorApi = createModelingResourceApi("composeIndicator", {
  listEndpoint: "list",
  updateWithId: true,
  rollbackWithQuery: true,
});
