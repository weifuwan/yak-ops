import { createModelingResourceApi } from "../shared/modeling-http";

export const atomIndicatorApi = createModelingResourceApi("atomIndicator", {
  listEndpoint: "list",
  updateWithId: true,
  rollbackWithQuery: true,
});
