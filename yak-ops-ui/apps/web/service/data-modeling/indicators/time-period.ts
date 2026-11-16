import { createModelingResourceApi } from "../shared/modeling-http";

export const timePeriodCategoryApi = createModelingResourceApi("timePeriodCategory", {
  listEndpoint: "list",
  updateWithId: true,
});

export const timePeriodApi = createModelingResourceApi("timePeriod", {
  listEndpoint: "list",
  updateWithId: true,
  rollbackWithQuery: true,
});
