import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";

function compile(file, mocks) {
  const text = readFileSync(new URL(file, import.meta.url), "utf8");
  const { outputText } = ts.transpileModule(text, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const exports = {};
  vm.runInNewContext(outputText, {
    exports,
    require(name) {
      if (Object.hasOwn(mocks, name)) return mocks[name];
      throw Error("Unexpected dependency: " + name);
    },
    AbortController,
    Error,
    Promise,
  });
  return exports;
}

const params = compile("../apps/web/app/data-sync/editor/definition.ts", {
  "@/service/data-sync/parameters": { validIncrementalConfig: (x) => Boolean(x?.column) },
  "./table-mapping-editor": { validTableMappings: (rows) => rows.length > 0 },
  "@/app/schedule/schedule-presentation": {
    sameScheduleRule: (a, b) => a.cronExpression === b.cronExpression,
  },
  "./table-section": {
    selectedTableKey: (tables, db, schema, name) =>
      tables.some((t) => t.name === name) ? name : null,
  },
});
const draft = {
  name: "orders",
  sourceType: "MYSQL",
  targetType: "POSTGRE_SQL",
  solutionType: "SINGLE_TABLE_REALTIME",
  steps: ["SCHEMA", "FULL", "INCREMENTAL"],
};
const form = {
  name: " orders ",
  remark: " remark ",
  debug: false,
  schemaChangesEnabled: false,
  allowDropColumns: true,
  writeMode: "UPSERT",
  sourceDataSourceId: "mysql",
  sourceDatabase: "test",
  sourceSchema: "",
  sourceTable: "orders",
  targetDataSourceId: "pg",
  targetDatabase: "target",
  targetSchema: "public",
  targetTable: "ods_orders",
};
const request = params.buildEditorPayload(form, draft, false, [], true);
assert.equal(request.name, "orders");
assert.equal(request.remark, "remark");
assert.equal(request.schemaChangesEnabled, false);
assert.equal(request.allowDropColumns, false);
assert.equal(
  "schemaChangesEnabled" in params.buildEditorPayload(form, draft, false, [], false),
  false,
);
assert.equal(request.tableMappings, undefined);

const options = {
  published: false,
  combination: { writeModes: ["UPSERT"] },
  solutionsLoading: false,
  solutionsError: "",
  useIncremental: false,
  initialLoad: true,
  batch: false,
  mappings: [],
  selectedSourceDataSource: { dbType: "MYSQL" },
  selectedTargetDataSource: { dbType: "POSTGRE_SQL" },
  draft,
  supportsScheduling: false,
  scheduleLoading: false,
  scheduleNeedsReload: false,
  schedule: null,
  scheduleForm: { cronExpression: "", timeZone: "UTC" },
  form,
  prepareSchema: true,
  targetCatalog: { schemas: [], tables: [], loading: false, error: "" },
  sourceCatalog: { schemas: [], tables: [], loading: false, error: "" },
};
assert.equal(params.canSaveEditor(options), true);
assert.equal(
  params.canSaveEditor({
    ...options,
    draft: { ...draft, targetType: "MYSQL", steps: ["FULL", "INCREMENTAL"] },
    selectedTargetDataSource: { dbType: "MYSQL" },
    prepareSchema: false,
    combination: { writeModes: ["UPSERT", "RETRACT"] },
    form: {
      ...form,
      keylessEnabled: true,
      chunkKeyColumn: "bucket",
      writeMode: "RETRACT",
      schemaChangesEnabled: false,
    },
    targetCatalog: { ...options.targetCatalog, tables: [{ name: "ods_orders" }] },
  }),
  true,
);
assert.equal(params.canSaveEditor({ ...options, form: { ...form, writeMode: "RETRACT" } }), false);
assert.equal(params.canSaveEditor({ ...options, published: true }), false);
assert.equal(
  params.canSaveEditor({ ...options, supportsScheduling: true, scheduleNeedsReload: true }),
  false,
);
assert.equal(
  params.canSaveEditor({
    ...options,
    sourceCatalog: { ...options.sourceCatalog, error: "unavailable" },
  }),
  false,
);

function createHarness({ failSchedule = false, failPublish = false } = {}) {
  const calls = [];
  const savedTask = { current: undefined };
  const scheduleDraft = { current: undefined };
  const setter = (name) => (value) => calls.push(name + ":" + String(value));
  const task = {
    createDataSyncTask: async () => {
      calls.push("create");
      return { id: "t-1" };
    },
    updateDataSyncTask: async () => {
      calls.push("update");
      return { id: "t-1" };
    },
    publishDataSyncTask: async () => {
      calls.push("publish");
      if (failPublish) throw Error("publish failed");
    },
  };
  const schedule = {
    previewSchedule: async () => {
      calls.push("preview");
    },
    createSchedule: async () => {
      calls.push("schedule");
      if (failSchedule) throw Error("unknown commit");
      return {
        id: "s-1",
        revision: 1,
        enabled: false,
        cronExpression: "0 0 6 * * ?",
        timeZone: "UTC",
      };
    },
    updateSchedule: async () => {
      calls.push("updateSchedule");
    },
    scheduleErrorMessage: () => "调度未确认",
  };
  const module = compile("../apps/web/app/data-sync/editor/use-editor-submission.ts", {
    react: {
      useState: (initial) => [initial, setter("saving")],
      useRef: (initial) => ({ current: initial }),
      useEffect: (effect) => effect(),
    },
    "@yak-ops/yak-ui": { toast: { success: setter("success"), error: setter("error") } },
    "@/service/data-sync/draft": { clearTaskDraft: () => calls.push("clearDraft") },
    "@/service/data-sync": task,
    "@/service/schedule": schedule,
    "@/app/schedule/schedule-presentation": {
      EMPTY_SCHEDULE: { cronExpression: "", timeZone: "UTC" },
      sameScheduleRule: (a, b) => a.cronExpression === b.cronExpression,
    },
  });
  const hook = module.useEditorSubmission({
    id: undefined,
    editing: false,
    draftKey: "draft",
    canSave: true,
    taskDirty: true,
    parametersDirty: true,
    supportsScheduling: true,
    schedule: null,
    scheduleForm: { cronExpression: "0 0 6 * * ?", timeZone: "UTC" },
    savedTask,
    scheduleDraft,
    payload: () => request,
    setTaskDirty: setter("taskDirty"),
    setParametersDirty: setter("parametersDirty"),
    setSchedule: setter("schedule"),
    setScheduleForm: setter("scheduleForm"),
    setScheduleError: setter("scheduleError"),
    setScheduleNeedsReload: setter("needsReload"),
    navigate: (route) => calls.push("navigate:" + route),
  });
  return { save: hook.save, calls, savedTask };
}
const success = createHarness();
await success.save(true);
assert.deepEqual(
  success.calls.filter((x) => ["preview", "create", "schedule", "publish"].includes(x)),
  ["preview", "create", "schedule", "publish"],
);
assert.equal(success.savedTask.current.id, "t-1");
const unknown = createHarness({ failSchedule: true });
await unknown.save(true);
assert.equal(unknown.savedTask.current.id, "t-1");
assert.ok(unknown.calls.includes("clearDraft"));
assert.ok(unknown.calls.includes("needsReload:true"));
assert.ok(unknown.calls.includes("navigate:/data-sync/t-1/edit"));
assert.ok(!unknown.calls.includes("publish"));
const rejected = createHarness({ failPublish: true });
await rejected.save(true);
assert.ok(rejected.calls.includes("publish"));
assert.ok(rejected.calls.some((x) => x.startsWith("error:任务已保存，仍处于下线状态")));
assert.ok(rejected.calls.includes("navigate:/data-sync/t-1/edit"));
console.log("Editor payload, validation, ordered saves and partial failure recovery passed");
