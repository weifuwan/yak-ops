import { createModelingResourceApi } from "../shared/modeling-http";

export const modifierApi = createModelingResourceApi("modifier", {
  listEndpoint: "list",
  updateWithId: true,
  rollbackWithQuery: true,
});
