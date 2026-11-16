import { createModelingResourceApi } from "../shared/modeling-http";

export const namingDictionaryApi = createModelingResourceApi("dictionaryStandard", {
  listEndpoint: "list",
  updateWithId: true,
});
