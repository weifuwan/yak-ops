import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import ts from "typescript";

function compile(file, mocks) {
  const text = readFileSync(new URL(file, import.meta.url), "utf8");
  const { outputText } = ts.transpileModule(text, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      jsx: ts.JsxEmit.ReactJSX,
    },
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

const parameterContract = compile("../apps/web/service/data-sync/parameters.ts", {});
const transformContract = compile("../apps/web/service/data-sync/transforms.ts", {});
const tableContract = compile("../apps/web/app/data-sync/editor/table-mapping-editor.tsx", {
  react: {},
  "react/jsx-runtime": {},
  "@yak-ops/yak-ui": {},
  "../components/searchable-select": {},
});
const replicationContract = compile("../apps/web/app/data-sync/editor/replication-options.tsx", {
  "react/jsx-runtime": {},
  "@yak-ops/yak-ui": {},
  "../components/searchable-select": {},
  "./table-mapping-editor": tableContract,
});
const params = compile("../apps/web/app/data-sync/editor/definition.ts", {
  "@/service/data-sync/parameters": parameterContract,
  "@/service/data-sync/transforms": transformContract,
  "./table-mapping-editor": tableContract,
  "@/app/schedule/schedule-presentation": {
    sameScheduleRule: (a, b) => a.cronExpression === b.cronExpression,
  },
  "./table-section": {
    selectedTableKey: (tables, db, schema, name) =>
      tables.some(
        (t) => t.name === name && (t.database || "") === db && (t.schema || "") === schema,
      )
        ? name
        : null,
  },
});
const transport = compile("../apps/web/service/data-sync/definition.ts", {
  "./parameters.ts": parameterContract,
});
const plain = (value) => JSON.parse(JSON.stringify(value));
const draft = {
  name: "orders",
  sourceType: "MYSQL",
  targetType: "POSTGRE_SQL",
  sourceConnector: "jdbc-mysql",
  sinkConnector: "jdbc-postgres",
  sourceProducesChangelog: false,
  productType: "SINGLE_TABLE_BATCH",
  steps: ["FULL"],
};
const incremental = {
  mode: "TIMESTAMP",
  column: "updated_at",
  initialValue: "2026-10-01T00:00:00",
  delaySeconds: 0,
  lookbackSeconds: 5,
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
    chunkKeyColumn: "",
    incremental,
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
  transforms: [
    {
      "source-table": "test.orders",
      projection: "`id` AS `order_id`, UPPER(name) AS name",
      filter: "id > 0",
      "primary-keys": "order_id",
      description: "keep authored text",
    },
  ],
  pipelineOptions: { name: "orders-pipeline", parallelism: 3, "local-time-zone": "Asia/Shanghai" },
};
const initialFormContract = compile("../apps/web/app/data-sync/editor/form-state.ts", {
  "@/service/data-sync/parameters": parameterContract,
  "./incremental-fields": { EMPTY_INCREMENTAL: { mode: "TIMESTAMP", column: "" } },
  "./types": compile("../apps/web/app/data-sync/editor/types.ts", {}),
});
const restored = initialFormContract.createEditorForm({
  ...draft,
  form: {
    ...form,
    sourceDataSourceId: form.source.dataSourceId,
    sourceDatabase: form.source.database,
    sourceTable: form.source.table,
    targetDataSourceId: form.sink.dataSourceId,
    targetDatabase: form.sink.database,
    targetSchema: form.sink.schema,
    targetTable: form.sink.table,
    transforms: form.transforms,
    pipelineOptions: form.pipelineOptions,
  },
});
assert.deepEqual(plain(restored.transforms), form.transforms);
assert.equal(restored.sink.schemaChangesEnabled, false);
assert.equal(
  initialFormContract.createEditorForm({
    ...draft,
    sourceProducesChangelog: true,
    productType: "DATABASE_REPLICATION",
    steps: ["FULL", "INCREMENTAL"],
  }).sink.writeMode,
  "UPSERT",
);
assert.equal(
  initialFormContract.createEditorForm({
    ...draft,
    sourceProducesChangelog: true,
    productType: "DATABASE_REPLICATION",
    steps: ["FULL"],
  }).sink.schemaChangesEnabled,
  false,
);
const request = params.buildEditorPayload(form, draft, false, []);
assert.equal(request.name, "orders");
assert.equal(request.remark, "remark");
assert.equal("schemaChangesEnabled" in request, false);
assert.equal(request.tableMappings, undefined);
assert.equal("incremental" in request, false);
assert.equal(request.sourceDataSourceId, "mysql");
assert.equal(request.targetDataSourceId, "pg");
assert.deepEqual(plain(request.transforms), form.transforms);
const single = transport.createDefinitionRequest(request).parameters;
assert.deepEqual(plain(single.transform), form.transforms);
assert.equal(single.source.type, "jdbc-mysql");
assert.equal(single.source.syncMode, "FULL");
assert.equal(single.pipeline["pipeline.table-scope"], "SINGLE");
assert.equal(single.pipeline["local-time-zone"], "Asia/Shanghai");
for (const [steps, syncMode] of [
  [["FULL", "INCREMENTAL"], "FULL_AND_INCREMENTAL"],
  [["INCREMENTAL"], "INCREMENTAL"],
]) {
  const payload = params.buildEditorPayload(form, { ...draft, steps }, false, []);
  const definition = transport.createDefinitionRequest(payload).parameters;
  assert.equal(definition.source.syncMode, syncMode);
  assert.deepEqual(plain(definition.source.tables[0].incremental), incremental);
  assert.equal(definition.pipeline["execution.runtime-mode"], "BATCH");
}
const replicationDraft = {
  ...draft,
  productType: "DATABASE_REPLICATION",
  sourceConnector: "mysql",
  sourceProducesChangelog: true,
  steps: ["FULL", "INCREMENTAL"],
};
const mapping = {
  source: { database: "test", table: "orders" },
  target: { database: "target", schema: "public", table: "ods_orders" },
  writeMode: "UPSERT",
};
for (const [steps, mode, runtime] of [
  [["FULL", "INCREMENTAL"], "INITIAL", "STREAMING"],
  [["INCREMENTAL"], "LATEST_OFFSET", "STREAMING"],
  [["FULL"], "SNAPSHOT", "BATCH"],
]) {
  const pipeline = transport.createDefinitionRequest(
    params.buildEditorPayload(form, { ...replicationDraft, steps }, true, [mapping]),
  ).parameters;
  assert.equal(pipeline.source.startup.mode, mode);
  assert.equal(pipeline.pipeline["execution.runtime-mode"], runtime);
  assert.equal(pipeline.pipeline["pipeline.table-scope"], "DATABASE");
  assert.equal(parameterContract.pipelineProductType(pipeline), "DATABASE_REPLICATION");
  assert.equal(pipeline.sink.schemaPolicy.evolution.allowDropColumns, false);
}
const startup = {
  mode: "SPECIFIC_OFFSETS",
  file: "mysql-bin.000123",
  pos: 45678,
  skipEvents: 2,
  skipRows: 3,
};
const offsetPayload = params.buildEditorPayload(
  { ...form, startup },
  { ...replicationDraft, steps: ["INCREMENTAL"] },
  true,
  [mapping],
);
assert.deepEqual(
  plain(transport.createDefinitionRequest(offsetPayload).parameters.source.startup),
  startup,
);
assert.equal("startup" in params.buildEditorPayload({ ...form, startup }, draft, false, []), false);
const multiple = [
  mapping,
  {
    source: { database: "test", table: "customers" },
    target: { database: "target", schema: "public", table: "customers" },
    writeMode: "UPSERT",
  },
];
assert.equal(
  transport.createDefinitionRequest(
    params.buildEditorPayload(form, replicationDraft, true, multiple),
  ).parameters.source.tables.length,
  2,
);
const retract = {
  ...mapping,
  source: { ...mapping.source, chunkKeyColumn: "bucket" },
  writeMode: "RETRACT",
};
const retractPipeline = transport.createDefinitionRequest(
  params.buildEditorPayload(
    { ...form, transforms: [], sink: { ...form.sink, schemaChangesEnabled: false } },
    { ...replicationDraft, sinkConnector: "jdbc-mysql" },
    true,
    [retract],
  ),
).parameters;
assert.equal(retractPipeline.pipeline["pipeline.table-scope"], "SINGLE");
assert.equal(retractPipeline.sink.tables[0].write.transaction.mode, "CHECKPOINT_XA");
assert.equal(retractPipeline.source.tables[0].snapshot.chunkKeyColumn, "bucket");
parameterContract.validateSyncParameters(retractPipeline);
assert.throws(
  () =>
    transport.createDefinitionRequest(
      params.buildEditorPayload(form, replicationDraft, true, [retract, multiple[1]]),
    ),
  /仅支持选择一张表/,
);

const targetTables = [{ database: "target", schema: "public", name: "ods_orders" }];
assert.equal(tableContract.validTableMappings([mapping], targetTables, ["UPSERT"], false), true);
assert.equal(
  tableContract.validTableMappings([mapping, mapping], targetTables, ["UPSERT"], false),
  false,
);
assert.equal(tableContract.validTableMappings([mapping], [], ["UPSERT"], false), false);
assert.equal(tableContract.validTableMappings([mapping], [], ["UPSERT"], true), true);
assert.equal(
  tableContract.validTableMappings([{ ...mapping, target: { table: "" } }], [], ["UPSERT"], true),
  false,
);
const columns = [
  { name: "id", typeName: "BIGINT", nullable: false, primaryKey: true },
  { name: "bucket", typeName: "BIGINT", nullable: false, primaryKey: false },
];
const metadata = (rows = [mapping], values = columns) => ({
  columns: new Map(rows.map((row) => [tableContract.tableKey(row.source), values])),
  loading: false,
  error: "",
});
const replicationError = (
  rows,
  values,
  initial = true,
  continuous = true,
  evolution = false,
  targetType = "MYSQL",
) =>
  replicationContract.replicationConfigurationError(
    rows,
    metadata(rows, values),
    initial,
    continuous,
    evolution,
    targetType,
  );
assert.equal(replicationError([mapping], columns), "");
assert.equal(replicationError(multiple, columns), "");
const keylessColumns = columns.map((column) => ({ ...column, primaryKey: false }));
assert.match(replicationError([mapping], keylessColumns), /快照分片字段/);
assert.match(replicationError([mapping], keylessColumns, false), /不支持仅增量/);
assert.match(replicationError(multiple, keylessColumns), /单独选择/);
assert.match(replicationError([retract], keylessColumns, true, true, true), /关闭结构变更/);
assert.match(replicationError([retract], keylessColumns, true, false), /持续同步/);
assert.match(
  replicationError([retract], keylessColumns, true, true, false, "POSTGRE_SQL"),
  /MySQL/,
);
assert.equal(replicationError([retract], keylessColumns), "");
assert.match(
  replicationError([{ ...retract, writeMode: "UPSERT" }], keylessColumns),
  /明确目标主键/,
);
assert.equal(
  replicationError(
    [{ ...retract, writeMode: "UPSERT", target: { ...mapping.target, primaryKeys: ["bucket"] } }],
    keylessColumns,
  ),
  "",
);
assert.equal(
  replicationError([{ ...retract, writeMode: "APPEND" }], keylessColumns, true, false),
  "",
);
assert.match(
  replicationContract.replicationConfigurationError(
    [mapping],
    { ...metadata(), error: "来源字段加载失败" },
    true,
    true,
    false,
    "MYSQL",
  ),
  /加载失败/,
);

const options = {
  published: false,
  combination: { writeModes: ["APPEND", "UPSERT"] },
  solutionsLoading: false,
  solutionsError: "",
  useIncremental: false,
  initialLoad: true,
  replication: false,
  replicationError: "",
  mappings: [],
  selectedSourceDataSource: { dbType: "MYSQL" },
  selectedTargetDataSource: { dbType: "POSTGRE_SQL" },
  draft,
  supportsScheduling: true,
  scheduleLoading: false,
  scheduleNeedsReload: false,
  schedule: null,
  scheduleForm: { cronExpression: "", timeZone: "UTC" },
  form,
  prepareSchema: false,
  targetCatalog: { schemas: [], tables: targetTables, loading: false, error: "" },
  sourceCatalog: { schemas: [], tables: [], loading: false, error: "" },
};
assert.equal(params.canSaveEditor(options), true);
for (const change of [
  { published: true },
  { solutionsLoading: true },
  { solutionsError: "unavailable" },
  { scheduleNeedsReload: true },
  { scheduleLoading: true },
  { replicationError: "invalid write" },
  { sourceCatalog: { ...options.sourceCatalog, error: "unavailable" } },
  { form: { ...form, name: " " } },
  { form: { ...form, sink: { ...form.sink, writeMode: "RETRACT" } } },
  { form: { ...form, transforms: [{ "source-table": "" }] } },
]) {
  assert.equal(params.canSaveEditor({ ...options, ...change }), false);
}
assert.equal(params.canSaveEditor({ ...options, useIncremental: true }), true);
assert.equal(
  params.canSaveEditor({
    ...options,
    useIncremental: true,
    form: { ...form, source: { ...form.source, incremental: undefined } },
  }),
  false,
);
assert.equal(
  params.canSaveEditor({ ...options, schedule: { enabled: true, cronExpression: "different" } }),
  false,
);
assert.equal(
  params.canSaveEditor({
    ...options,
    replication: true,
    draft: replicationDraft,
    mappings: [mapping],
    supportsScheduling: false,
  }),
  true,
);
assert.equal(
  params.canSaveEditor({
    ...options,
    replication: true,
    draft: replicationDraft,
    mappings: [mapping, mapping],
    supportsScheduling: false,
  }),
  false,
);

// Session storage accepts the current two-workflow draft only and stays scoped by identity.
const stored = new Map();
const draftText = readFileSync(
  new URL("../apps/web/service/data-sync/draft.ts", import.meta.url),
  "utf8",
);
const draftModule = {};
vm.runInNewContext(
  ts.transpileModule(draftText, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText,
  {
    exports: draftModule,
    window: {
      sessionStorage: {
        getItem: (key) => stored.get(key),
        setItem: (key, value) => stored.set(key, value),
        removeItem: (key) => stored.delete(key),
      },
    },
  },
);
const key = draftModule.taskDraftKey("alice", "workspace-1");
const completeDraft = { ...draft, form: request };
draftModule.writeTaskDraft(key, completeDraft);
assert.equal(JSON.parse(stored.get(key)).version, 3);
assert.deepEqual(plain(draftModule.readTaskDraft(key)), plain(completeDraft));
assert.equal(
  draftModule.readTaskDraft(draftModule.taskDraftKey("alice", "workspace-2")),
  undefined,
);
stored.set(key, JSON.stringify({ version: 2, draft: completeDraft }));
assert.equal(draftModule.readTaskDraft(key), undefined);

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
