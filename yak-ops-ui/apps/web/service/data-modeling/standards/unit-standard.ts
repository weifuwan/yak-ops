import { createModelingResourceApi } from "../shared/modeling-http";

export const unitStandardApi = createModelingResourceApi("unitStandard", {
  listEndpoint: "list",
  updateWithId: true,
});
