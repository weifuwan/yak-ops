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
  "@/service/data-sync/parameters": {
    validIncrementalConfig: (x) => Boolean(x?.column),
    offlineIncremental: (solution, steps) =>
      solution.endsWith("_OFFLINE") && steps.includes("INCREMENTAL"),
  },
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
  sourceConnector: "mysql",
  sinkConnector: "jdbc-postgres",
  sourceProducesChangelog: true,
  solutionType: "SINGLE_TABLE_REALTIME",
  steps: ["SCHEMA", "FULL", "INCREMENTAL"],
};
const form = {
  name: " orders ",
  remark: " remark ",
  debug: false,
  source: {
    dataSourceId: "mysql",
    database: "test",
    schema: "",
    table: "orders",
    sourceHasPrimaryKey: true,
    chunkKeyColumn: "",
    incremental: undefined,
  },
  sink: {
    dataSourceId: "pg",
    database: "target",
    schema: "public",
    table: "ods_orders",
    schemaChangesEnabled: false,
    allowDropColumns: true,
    writeMode: "UPSERT",
    targetPrimaryKeys: [],
  },
};
const selection = compile("../apps/web/app/data-sync/editor/source-selection.ts", {});
const keyed = {
  ...form,
  source: { ...form.source, sourceHasPrimaryKey: false, chunkKeyColumn: "bucket" },
  sink: {
    ...form.sink,
    writeMode: "RETRACT",
    targetPrimaryKeys: ["bucket"],
    schemaChangesEnabled: false,
  },
};
const selectedSchema = selection.selectSourceSchema(keyed, "new_schema", true);
assert.equal(selectedSchema.source.schema, "new_schema");
assert.equal(selectedSchema.source.table, "");
assert.equal(selectedSchema.source.sourceHasPrimaryKey, undefined);
assert.equal(selectedSchema.sink.writeMode, "UPSERT");
assert.equal(selectedSchema.sink.targetPrimaryKeys.length, 0);
assert.equal(selectedSchema.sink.dataSourceId, "pg");

const selectedTable = selection.selectSourceTable(
  keyed,
  { name: "invoices", schema: "public" },
  true,
);
assert.equal(selectedTable.source.table, "invoices");
assert.equal(selectedTable.source.sourceHasPrimaryKey, undefined);
assert.equal(selectedTable.sink.writeMode, "UPSERT");
assert.equal(selectedTable.sink.table, "ods_orders");

const selectedKeyless = selection.setSourcePrimaryKeys(form, false, true);
assert.equal(selectedKeyless.source.sourceHasPrimaryKey, false);
assert.equal(selectedKeyless.sink.writeMode, "RETRACT");
assert.equal(selectedKeyless.sink.schemaChangesEnabled, false);
assert.equal(selectedKeyless.sink.allowDropColumns, false);
assert.equal(selectedKeyless.sink.targetPrimaryKeys.length, 0);
assert.equal(
  selection.setSourcePrimaryKeys(selectedKeyless, true, true).source.sourceHasPrimaryKey,
  true,
);
const selectedSnapshotKeyless = selection.setSourcePrimaryKeys(
  { ...form, sink: { ...form.sink, writeMode: "APPEND" } },
  false,
  false,
);
assert.equal(selectedSnapshotKeyless.source.sourceHasPrimaryKey, false);
assert.equal(selectedSnapshotKeyless.sink.writeMode, "APPEND");

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
assert.equal(request.sourceDataSourceId, "mysql");
assert.equal(request.targetDataSourceId, "pg");
assert.equal(request.writeMode, "UPSERT");
assert.equal("source" in request, false);
assert.equal("sink" in request, false);
assert.equal("env" in request, false);

const transport = compile("../apps/web/service/data-sync/definition.ts", {
  "./parameters.ts": {
    solutionSyncType: (solution) => (solution.endsWith("_REALTIME") ? "REALTIME" : "OFFLINE"),
    supportsMultipleTables: (solution) => solution.startsWith("DATABASE_"),
  },
});
const realtimeOnly = {
  ...draft,
  steps: ["INCREMENTAL"],
};
const realtimeOnlyPayload = params.buildEditorPayload(form, realtimeOnly, false, [], true);
assert.equal("chunkKeyColumn" in realtimeOnlyPayload, false);
const realtimeOnlyDefinition = transport.createDefinitionRequest(realtimeOnlyPayload).parameters;
assert.equal(realtimeOnlyDefinition.source.startup.mode, "LATEST_OFFSET");
assert.equal(realtimeOnlyDefinition.pipeline["execution.runtime-mode"], "STREAMING");

// Editor projection must preserve a CDC offset even when editing other fields.
const persistedStartup = {
  mode: "SPECIFIC_OFFSETS",
  file: "mysql-bin.000123",
  pos: 45678,
  skipEvents: 2,
  skipRows: 3,
};
const offsetEdit = params.buildEditorPayload(form, realtimeOnly, false, [], true, persistedStartup);
const savedStartup = transport.createDefinitionRequest(offsetEdit).parameters.source.startup;
assert.deepEqual(JSON.parse(JSON.stringify(savedStartup)), persistedStartup);
const persistedPipelineOptions = {
  name: "orders-pipeline",
  parallelism: 3,
  "local-time-zone": "Asia/Shanghai",
};
const optionsEdit = params.buildEditorPayload(
  form,
  realtimeOnly,
  false,
  [],
  true,
  persistedStartup,
  [],
  persistedPipelineOptions,
);
assert.deepEqual(
  JSON.parse(JSON.stringify(transport.createDefinitionRequest(optionsEdit).parameters.pipeline)),
  {
    ...persistedPipelineOptions,
    "execution.runtime-mode": "STREAMING",
    "pipeline.table-scope": "SINGLE",
  },
);
const offlineStartupPayload = params.buildEditorPayload(
  form,
  { ...draft, solutionType: "SINGLE_TABLE_OFFLINE" },
  false,
  [],
  false,
  persistedStartup,
);
assert.equal("startup" in offlineStartupPayload, false);

const snapshotDraft = { ...draft, steps: ["FULL"] };
const snapshotPayload = params.buildEditorPayload(form, snapshotDraft, false, [], true);
const snapshotDefinition = transport.createDefinitionRequest(snapshotPayload).parameters;
assert.equal(snapshotDefinition.source.startup.mode, "SNAPSHOT");
assert.equal(snapshotDefinition.sink.schemaPolicy.evolution.enabled, false);
assert.equal(snapshotDefinition.pipeline["execution.runtime-mode"], "BATCH");

const incremental = {
  mode: "TIMESTAMP",
  column: "updated_at",
  initialValue: "2026-10-01T00:00:00",
  delaySeconds: 0,
  lookbackSeconds: 5,
};
const offlineDraft = {
  ...draft,
  solutionType: "SINGLE_TABLE_OFFLINE",
  sourceConnector: "jdbc-mysql",
  sourceProducesChangelog: false,
  steps: ["FULL", "INCREMENTAL"],
};
const offlineForm = {
  ...form,
  source: { ...form.source, incremental },
  sink: { ...form.sink, writeMode: "UPSERT" },
};
const offlinePayload = params.buildEditorPayload(offlineForm, offlineDraft, false, [], false);
assert.equal(offlinePayload.incremental.column, "updated_at");
assert.equal(offlinePayload.incremental.lookbackSeconds, 5);
const offlineParameters = transport.createDefinitionRequest(offlinePayload).parameters;
assert.equal(offlineParameters.source.type, "jdbc-mysql");
assert.equal(offlineParameters.source.syncMode, "FULL_AND_INCREMENTAL");
assert.equal(offlineParameters.source.tables.length, 1);
assert.deepEqual(
  JSON.parse(JSON.stringify(offlineParameters.source.tables[0].incremental)),
  incremental,
);
assert.equal(offlineParameters.source.tables[0].table, "orders");
assert.equal(offlineParameters.sink.tables[0].table, "ods_orders");
assert.equal(offlineParameters.sink.tables[0].write.mode, "UPSERT");
assert.equal("env" in offlineParameters, false);
assert.equal("incremental" in offlineParameters.source, false);

const incrementalOnly = {
  ...offlineDraft,
  steps: ["INCREMENTAL"],
};
const incrementalOnlyPayload = params.buildEditorPayload(
  offlineForm,
  incrementalOnly,
  false,
  [],
  false,
);
const incrementalOnlyParameters =
  transport.createDefinitionRequest(incrementalOnlyPayload).parameters;
assert.equal(incrementalOnlyParameters.source.tables[0].incremental.column, "updated_at");
const fullOnlyPayload = params.buildEditorPayload(
  offlineForm,
  { ...offlineDraft, steps: ["FULL"] },
  false,
  [],
  false,
);
assert.equal("incremental" in fullOnlyPayload, false);
const mappings = [
  {
    source: { table: "customers" },
    target: { table: "ods_customers" },
    writeMode: "UPSERT",
    incremental,
  },
  {
    source: { table: "orders" },
    target: { table: "ods_orders" },
    writeMode: "UPSERT",
    incremental,
  },
];
const many = params.buildEditorPayload(
  offlineForm,
  { ...offlineDraft, solutionType: "DATABASE_OFFLINE" },
  true,
  mappings,
  false,
);
const multiParameters = transport.createDefinitionRequest(many).parameters;
assert.equal(multiParameters.source.tables.length, 2);
assert.equal(multiParameters.source.tables[0].incremental.column, "updated_at");
assert.equal(multiParameters.sink.tables[1].table, "ods_orders");
assert.equal("incremental" in many, false);
const retractForm = {
  ...form,
  source: { ...form.source, sourceHasPrimaryKey: false, chunkKeyColumn: "bucket" },
  sink: {
    ...form.sink,
    targetPrimaryKeys: [],
    writeMode: "RETRACT",
    schemaChangesEnabled: false,
  },
};
const retractPayload = params.buildEditorPayload(
  retractForm,
  { ...draft, solutionType: "SINGLE_TABLE_REALTIME", steps: ["FULL", "INCREMENTAL"] },
  false,
  [],
  true,
);
const retractParameters = transport.createDefinitionRequest(retractPayload).parameters;
assert.equal(retractParameters.sink.tables[0].write.transaction.mode, "CHECKPOINT_XA");
assert.equal(retractParameters.source.tables[0].snapshot.chunkKeyColumn, "bucket");
assert.equal(retractParameters.sink.tables[0].write.mode, "RETRACT");
assert.equal("xa" in retractParameters.source.tables[0], false);
const upsertParameters = transport.createDefinitionRequest({
  ...retractPayload,
  writeMode: "UPSERT",
  targetPrimaryKeys: ["bucket"],
}).parameters;
assert.equal(upsertParameters.sink.tables[0].write.transaction, undefined);
assert.deepEqual(Array.from(upsertParameters.sink.tables[0].write.primaryKeys), ["bucket"]);

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
const snapshotOptions = {
  ...options,
  draft: { ...draft, solutionType: "SINGLE_TABLE_OFFLINE", steps: ["FULL"] },
  combination: { writeModes: ["APPEND", "UPSERT"] },
  form: {
    ...selectedSnapshotKeyless,
    source: { ...selectedSnapshotKeyless.source, chunkKeyColumn: "bucket" },
  },
};
assert.equal(params.canSaveEditor(snapshotOptions), true);
assert.equal(
  params.canSaveEditor({
    ...snapshotOptions,
    form: {
      ...snapshotOptions.form,
      source: { ...snapshotOptions.form.source, chunkKeyColumn: "" },
    },
  }),
  false,
);
assert.equal(
  params.canSaveEditor({
    ...options,
    draft: offlineDraft,
    form: offlineForm,
    useIncremental: true,
  }),
  true,
);
assert.equal(
  params.canSaveEditor({
    ...options,
    draft: offlineDraft,
    form: { ...offlineForm, source: { ...offlineForm.source, incremental: undefined } },
    useIncremental: true,
  }),
  false,
);
assert.equal(
  params.canSaveEditor({
    ...options,
    draft: { ...draft, targetType: "MYSQL", steps: ["FULL", "INCREMENTAL"] },
    selectedTargetDataSource: { dbType: "MYSQL" },
    prepareSchema: false,
    combination: { writeModes: ["UPSERT", "RETRACT"] },
    form: {
      ...form,
      source: { ...form.source, sourceHasPrimaryKey: false, chunkKeyColumn: "bucket" },
      sink: { ...form.sink, writeMode: "RETRACT", schemaChangesEnabled: false },
    },
    targetCatalog: { ...options.targetCatalog, tables: [{ name: "ods_orders" }] },
  }),
  true,
);
assert.equal(
  params.canSaveEditor({
    ...options,
    form: { ...form, sink: { ...form.sink, writeMode: "RETRACT" } },
  }),
  false,
);
assert.equal(
  params.canSaveEditor({
    ...options,
    draft: { ...draft, targetType: "MYSQL" },
    selectedTargetDataSource: { dbType: "MYSQL" },
    combination: { writeModes: ["UPSERT", "RETRACT"] },
    form: {
      ...form,
      source: { ...form.source, sourceHasPrimaryKey: false, chunkKeyColumn: "bucket" },
      sink: {
        ...form.sink,
        targetPrimaryKeys: ["bucket"],
        writeMode: "UPSERT",
        schemaChangesEnabled: false,
      },
    },
  }),
  true,
);
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
