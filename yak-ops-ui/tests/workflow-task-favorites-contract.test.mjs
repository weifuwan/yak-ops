import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const favoriteService = read("../apps/web/service/workflow/favourite.ts");
const favoriteHook = read("../apps/web/app/data-development/workflow-editor/node-selector/use-workflow-task-favorites.ts");
const selector = read("../apps/web/app/data-development/workflow-editor/node-selector/node-selector.tsx");

test("workflow task favorite service uses the existing backend endpoints", () => {
  assert.match(favoriteService, /"\/favourite"/);
  assert.match(favoriteService, /"\/taskTypes"/);
  assert.match(favoriteService, /HttpUtils\.postData<boolean>/);
  assert.match(favoriteService, /HttpUtils\.deleteData<boolean>/);
  assert.match(favoriteService, /encodeURIComponent\(taskType\)/);
});

test("workflow task favorites are scoped to the signed-in user and shared by query cache", () => {
  assert.match(favoriteHook, /currentUser\?\.id/);
  assert.match(favoriteHook, /workflowTaskFavoriteQueryKey/);
  assert.match(favoriteHook, /useMutation/);
  assert.match(favoriteHook, /onMutate/);
  assert.match(favoriteHook, /invalidateQueries/);
  assert.match(favoriteHook, /removeQueries/);
});

test("favorite picker is derived from registered task types, not the backend default catalog", () => {
  assert.match(selector, /const source = query\.data \?\? \[\]/);
  assert.match(selector, /favorites\.favoriteTaskTypes\.has\(item\.taskType\)/);
  assert.match(selector, /listWorkflowTaskTypes\(signal\)/);
});

test("both sidebar and floating pickers show favorites and expose independent star controls", () => {
  assert.match(selector, /useWorkflowTaskFavorites\(Boolean\(request\)\)/);
  assert.match(selector, /我的收藏/);
  assert.match(selector, /<Star/);
  assert.match(selector, /onToggleFavorite/);
  assert.match(selector, /favoriteTasks\.map/);
  assert.match(selector, /grouped\.map/);
  assert.match(selector, /WorkflowNodeSelector/);
  assert.match(selector, /WorkflowEdgeNodeSelector/);
});
