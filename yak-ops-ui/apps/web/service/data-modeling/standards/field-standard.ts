import { createModelingResourceApi } from "../shared/modeling-http";

export const fieldStandardApi = createModelingResourceApi("fieldStandard", {
  listEndpoint: "list",
  updateWithId: true,
});
