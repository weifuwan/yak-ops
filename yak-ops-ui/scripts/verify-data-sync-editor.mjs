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
    structuredClone,
    crypto: { randomUUID: () => "test-publication-id" },
  });
  return exports;
}

const tableConfiguration = compile("../apps/web/service/data-sync/table-configuration.ts", {});
const parameterContract = compile("../apps/web/service/data-sync/parameters.ts", {
  "./table-configuration.ts": tableConfiguration,
});
const transformContract = compile("../apps/web/service/data-sync/transforms.ts", {});
const tableCatalog = compile("../apps/web/app/data-sync/editor/table-catalog.ts", {});
const tableContract = compile("../apps/web/app/data-sync/editor/table-selection.ts", {
  "./table-catalog": tableCatalog,
  "@/service/data-sync/table-configuration": tableConfiguration,
});
const replicationContract = compile("../apps/web/app/data-sync/editor/replication-validation.ts", {
  "./table-catalog": tableCatalog,
  "@/service/data-sync/table-configuration": tableConfiguration,
});
const params = compile("../apps/web/app/data-sync/editor/definition.ts", {
  "@/service/data-sync/parameters": parameterContract,
  "@/service/data-sync/transforms": transformContract,
  "@/service/data-sync/table-configuration": tableConfiguration,
  "./table-selection": tableContract,
  "./table-catalog": tableCatalog,
  "@/app/schedule/schedule-presentation": {
    sameScheduleRule: (a, b) => a.cronExpression === b.cronExpression,
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
    where: "",
    splitKey: "",
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
    preSql: [],
    postSql: [],
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
  source: { ...mapping.source, snapshot: { chunkKeyColumn: "bucket" } },
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
const overridden = {
  ...mapping,
  source: { ...mapping.source, snapshot: { where: "tenant_id = 9" }, dml: { events: ["UPDATE"] } },
  batchSize: 37,
};
const selectionDefaults = {
  mode: "REGEX",
  databasePattern: ".*",
  tablePattern: ".*",
  snapshot: { skip: true },
  dml: { events: ["INSERT", "DELETE"] },
};
const mappingDefaults = {
  database: "new_ods",
  table: "copy_${table}",
  write: { mode: "UPSERT", batchSize: 250 },
};
const matched = tableContract.resolveMatchedMappings(
  [overridden],
  [
    { ...mapping.source, name: mapping.source.table },
    { database: "next", name: "customers" },
  ],
  selectionDefaults,
  mappingDefaults,
);
assert.equal(
  matched[0],
  overridden,
  "Existing table overrides retain their identity and authored options",
);
assert.deepEqual(plain(matched[1]), {
  source: {
    database: "next",
    table: "customers",
    snapshot: { skip: true },
    dml: { events: ["INSERT", "DELETE"] },
  },
  target: { database: "new_ods", table: "copy_customers" },
  writeMode: "UPSERT",
  batchSize: 250,
});
matched[1].source.dml.events.push("UPDATE");
assert.deepEqual(
  selectionDefaults.dml.events,
  ["INSERT", "DELETE"],
  "New rows do not share mutable defaults",
);
assert.throws(
  () => tableContract.resolveMatchedMappings([], [], selectionDefaults, mappingDefaults),
  /未匹配/,
);
assert.throws(
  () =>
    tableContract.resolveMatchedMappings(
      [],
      [
        { database: "a", name: "orders" },
        { database: "b", name: "orders" },
      ],
      selectionDefaults,
      mappingDefaults,
    ),
  /重复表/,
);
const selectedOnce = tableContract.addSelectedTables(
  [overridden],
  [mapping.source, multiple[1].source],
  [],
  {},
  "UPSERT",
  true,
  2,
);
assert.equal(selectedOnce[0], overridden);
assert.equal(selectedOnce.length, 2);
assert.throws(
  () => tableContract.addSelectedTables([mapping], [multiple[1].source], [], {}, "UPSERT", true, 1),
  /最多/,
);
assert.throws(
  () => tableContract.addSelectedTables([retract], [multiple[1].source], [], {}, "UPSERT", true, 2),
  /逐行更新/,
);
assert.deepEqual(
  plain(tableContract.parsePastedTableNames(" app.orders\n app.orders, app.customers \r\n")),
  ["app.orders", "app.customers"],
);
assert.throws(
  () => tableConfiguration.validateTableSnapshot({ skip: true, where: "id > 0" }),
  /不能同时/,
);
const columns = [
  { name: "id", typeName: "BIGINT", nullable: false, primaryKey: true },
  { name: "bucket", typeName: "BIGINT", nullable: false, primaryKey: false },
];
const metadata = (rows = [mapping], values = columns) => ({
  columns: new Map(rows.map((row) => [tableCatalog.tableKey(row.source), values])),
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

assert.equal(
  params.canSaveEditor({ ...options, published: true }),
  true,
  "Published tasks remain editable",
);
assert.equal(
  params.editorValidationIssue({ ...options, form: { ...form, name: "" } }).location,
  "properties",
);
assert.equal(
  params.editorValidationIssue({ ...options, scheduleNeedsReload: true }).parameterTab,
  "runtime",
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

const versions = compile("../apps/web/service/data-sync/versions.ts", {
  "./parameters": parameterContract,
  "./definition": transport,
});
const baseTask = transport.toSyncTaskRecord({
  id: "t-1",
  name: request.name,
  taskType: "DATA_SYNC",
  status: "UNPUBLISHED",
  debug: request.debug,
  remark: request.remark,
  definitionVersion: 1,
  revision: 1,
  publishedVersion: null,
  publishedDebug: null,
  hasUnpublishedChanges: true,
  parameterSchemaVersion: 7,
  parameters: transport.createDefinitionRequest(request).parameters,
});
function createHarness({
  failSchedule = false,
  failPublish,
  existing = false,
  concurrentPublish = false,
} = {}) {
  const calls = [];
  const savedTask = { current: existing ? baseTask : undefined };
  const scheduleDraft = { current: undefined };
  const state = [];
  const refs = [];
  let stateIndex = 0,
    refIndex = 0,
    initialized = false;
  let publication;
  let remote = baseTask;
  let receipts = [];
  let publishFailure = failPublish;
  const task = {
    createDataSyncTask: async () => {
      calls.push("create");
      return baseTask;
    },
    updateDataSyncTask: async (_id, _request, revision) => {
      calls.push("update:" + revision);
      return { ...baseTask, revision: revision + 1 };
    },
    publishDataSyncTask: async (_id, intent) => {
      calls.push("publish");
      publication = plain(intent);
      if (publishFailure) {
        const error = Error("publish failed");
        error.rejected = publishFailure === "rejected";
        throw error;
      }
      return {
        ...baseTask,
        definitionVersion: concurrentPublish ? 2 : 1,
        pipelineOptions: concurrentPublish
          ? { ...baseTask.pipelineOptions, parallelism: 6 }
          : baseTask.pipelineOptions,
        status: "PUBLISHED",
        revision: concurrentPublish ? 4 : 2,
        publishedVersion: 1,
        publishedDebug: false,
        hasUnpublishedChanges: concurrentPublish,
      };
    },
    isRejectedTaskWrite: (error) => error.rejected === true,
    getDataSyncTask: async () => {
      calls.push("readTask");
      return remote;
    },
    listTaskPublications: async () => {
      calls.push("readPublications");
      return { bizData: receipts, pagination: { total: receipts.length } };
    },
  };
  const setter = (name) => (value) => calls.push(name + ":" + String(value));
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
      useState: (initial) => {
        const index = stateIndex++;
        if (!(index in state)) state[index] = typeof initial === "function" ? initial() : initial;
        return [
          state[index],
          (value) => {
            state[index] = typeof value === "function" ? value(state[index]) : value;
          },
        ];
      },
      useRef: (initial) => {
        const index = refIndex++;
        return (refs[index] ||= { current: initial });
      },
      useEffect: (effect) => {
        if (!initialized) effect();
      },
    },
    "@tanstack/react-query": {
      useQueryClient: () => ({ invalidateQueries: async () => calls.push("invalidate") }),
    },
    "@yak-ops/yak-ui": { toast: { success: setter("success"), error: setter("toastError") } },
    "@/service/data-sync/draft": { clearTaskDraft: () => calls.push("clearDraft") },
    "@/service/data-sync": task,
    "@/service/data-sync/versions": versions,
    "@/service/schedule": schedule,
    "@/app/schedule/schedule-presentation": {
      EMPTY_SCHEDULE: { cronExpression: "", timeZone: "UTC" },
      sameScheduleRule: (a, b) => a.cronExpression === b.cronExpression,
    },
  });
  const input = {
    draftKey: "draft",
    scope: { userId: "alice", workspaceId: "workspace-1" },
    taskDirty: true,
    parametersDirty: true,
    supportsScheduling: true,
    schedule: null,
    scheduleForm: { cronExpression: "0 0 6 * * ?", timeZone: "UTC" },
    savedTask,
    scheduleDraft,
    payload: () => request,
    onPersisted: (task, payload) => {
      calls.push("persisted:" + task.id);
      if (payload) {
        input.taskDirty = false;
        input.parametersDirty = false;
      }
    },
    onComplete: (task) => calls.push("complete:" + task.id),
    onScheduleFailure: () => calls.push("focusSchedule"),
    setSchedule: (value) => {
      input.schedule = value;
    },
    setScheduleForm: (value) => {
      input.scheduleForm = value;
    },
    setScheduleError: setter("scheduleError"),
    setScheduleNeedsReload: setter("needsReload"),
  };
  const render = () => {
    stateIndex = 0;
    refIndex = 0;
    const result = module.useEditorSubmission(input);
    initialized = true;
    return result;
  };
  return {
    render,
    calls,
    savedTask,
    input,
    publication: () => publication,
    confirmReceipt: () => {
      remote = {
        ...baseTask,
        status: "PUBLISHED",
        revision: 2,
        publishedVersion: 1,
        publishedDebug: false,
        hasUnpublishedChanges: false,
      };
      receipts = [{ requestId: publication.requestId, version: 1 }];
    },
    retrySucceeds: () => {
      publishFailure = undefined;
    },
  };
}
const success = createHarness();
await success.render().save(true);
assert.deepEqual(
  success.calls.filter((x) => ["preview", "create", "schedule", "publish"].includes(x)),
  ["preview", "create", "schedule", "publish"],
);
assert.deepEqual(success.publication(), {
  version: 1,
  expectedRevision: 1,
  requestId: "test-publication-id",
});
assert.equal(success.savedTask.current.id, "t-1");
assert.equal(success.savedTask.current.publishedVersion, 1);
const update = createHarness({ existing: true });
await update.render().save();
assert.ok(update.calls.includes("update:1"));
const unknownSchedule = createHarness({ failSchedule: true });
await unknownSchedule.render().save(true);
assert.equal(unknownSchedule.savedTask.current.id, "t-1");
assert.ok(
  unknownSchedule.calls.includes("persisted:t-1"),
  "Created identity must reach the page before schedule failure",
);
assert.ok(unknownSchedule.calls.includes("clearDraft"));
assert.ok(unknownSchedule.calls.includes("needsReload:true"));
assert.ok(unknownSchedule.calls.includes("focusSchedule"));
assert.ok(!unknownSchedule.calls.includes("publish"));
const rejected = createHarness({ failPublish: "rejected" });
await rejected.render().save(true);
assert.match(rejected.render().error, /任务已保存，发布失败/);
assert.equal(rejected.render().pendingWrite, undefined);
assert.equal(rejected.savedTask.current.id, "t-1");
const unknownPublish = createHarness({ failPublish: "unknown" });
await unknownPublish.render().save(true);
assert.equal(unknownPublish.render().pendingWrite.kind, "publish");
await unknownPublish.render().save(true);
assert.equal(
  unknownPublish.calls.filter((x) => x === "publish").length,
  1,
  "Unknown writes cannot blindly retry",
);
unknownPublish.confirmReceipt();
await unknownPublish.render().reconcile();
assert.equal(unknownPublish.render().pendingWrite, undefined);
assert.equal(unknownPublish.savedTask.current.publishedVersion, 1);
assert.equal(unknownPublish.calls.filter((x) => x === "publish").length, 1);
const retry = createHarness({ failPublish: "unknown" });
await retry.render().save(true);
const originalIntent = retry.publication();
await retry.render().reconcile();
assert.deepEqual(plain(retry.render().retryPublication), originalIntent);
retry.input.taskDirty = true;
await retry.render().save(true);
assert.equal(
  retry.calls.filter((x) => x === "publish").length,
  1,
  "Recovery cannot attach an old key to new edits",
);
retry.input.taskDirty = false;
retry.retrySucceeds();
await retry.render().save(true);
assert.deepEqual(retry.publication(), originalIntent);
const concurrent = createHarness({ concurrentPublish: true });
await concurrent.render().save(true);
assert.equal(concurrent.render().pendingWrite.kind, "conflict");
assert.equal(
  concurrent.savedTask.current.revision,
  1,
  "Concurrent publication response cannot rebase the editor",
);
const duplicate = createHarness();
const duplicateHook = duplicate.render();
await Promise.all([duplicateHook.save(), duplicateHook.save()]);
assert.equal(duplicate.calls.filter((x) => x === "create").length, 1);
console.log("Editor payload, validation, ordered saves and partial failure recovery passed");

// History is parameter-only, immutable, and compared by table identity.
const oldVersion = {
  definitionId: baseTask.id,
  version: 1,
  parameterSchemaVersion: 7,
  parameters: transport.createDefinitionRequest(request).parameters,
};
const latestTask = {
  ...baseTask,
  name: "current name",
  remark: "current remark",
  debug: true,
  definitionVersion: 5,
  revision: 8,
  publishedVersion: 3,
  publishedDebug: false,
  hasUnpublishedChanges: true,
};
const oldJson = JSON.stringify(oldVersion);
const restoredVersion = versions.restoreParameterVersion(latestTask, oldVersion);
assert.equal(restoredVersion.name, latestTask.name);
assert.equal(restoredVersion.remark, latestTask.remark);
assert.equal(restoredVersion.debug, true);
assert.equal(restoredVersion.definitionVersion, 5);
assert.equal(restoredVersion.revision, 8);
assert.equal(restoredVersion.publishedVersion, 3);
assert.equal(JSON.stringify(oldVersion), oldJson);
assert.throws(
  () => versions.restoreParameterVersion(latestTask, { ...oldVersion, parameterSchemaVersion: 99 }),
  /不支持/,
);
assert.throws(
  () =>
    versions.restoreParameterVersion(latestTask, { ...oldVersion, definitionId: "another-task" }),
  /不属于/,
);
const historical = {
  ...oldVersion,
  parameters: transport.createDefinitionRequest(
    params.buildEditorPayload(form, replicationDraft, true, multiple),
  ).parameters,
};
const reordered = structuredClone(historical);
reordered.parameters.source.tables.reverse();
reordered.parameters.sink.tables.reverse();
reordered.parameters.route.reverse();
assert.equal(versions.compareParameterVersions(historical, reordered).length, 0);
reordered.parameters.pipeline.parallelism = 9;
const differences = versions.compareParameterVersions(historical, reordered);
assert.equal(differences.length, 1);
assert.match(differences[0].field, /并行度/);
assert.equal(differences[0].before, "3");
assert.equal(differences[0].after, "9");
const configurationContract = compile("../apps/web/app/data-sync/editor/configuration.ts", {
  "@/service/data-sync/parameters": parameterContract,
  "../solution/presentation": compile("../apps/web/app/data-sync/solution/presentation.ts", {}),
  "./incremental-fields": { EMPTY_INCREMENTAL: { mode: "TIMESTAMP", column: "" } },
});
assert.deepEqual(
  plain(
    configurationContract.parseEngineOptions(
      '{"parallelism":3,"local-time-zone":"Asia/Shanghai"}',
      false,
    ),
  ),
  { parallelism: 3, "local-time-zone": "Asia/Shanghai" },
);
assert.throws(
  () => configurationContract.parseEngineOptions('{"unsupported-option":true}', false),
  /不支持/,
);
assert.throws(() => configurationContract.parseEngineOptions('{"parallelism":0}', false), /正整数/);
assert.throws(
  () => configurationContract.parseEngineOptions('{"schema.change.behavior":"evolve"}', false),
  /不一致/,
);

// An asynchronous restore cannot replace newer metadata or close a subsequently opened drawer.
const restorationRefs = [];
let restorationRefIndex = 0,
  restorationMounted = false,
  disposeRestoration;
const connectionRequests = [];
const confirmations = [];
const appliedConfigurations = [];
let closedDrawers = 0;
const restorationModule = compile(
  "../apps/web/app/data-sync/editor/use-definition-restoration.ts",
  {
    react: {
      useRef: (initial) => {
        const index = restorationRefIndex++;
        return (restorationRefs[index] ||= { current: initial });
      },
      useEffect: (effect) => {
        if (!restorationMounted) disposeRestoration = effect();
      },
    },
    "@yak-ops/yak-ui": {
      toast: {
        error: (message) => {
          throw Error(message);
        },
      },
    },
    "@/service/data-sync": { getDataSyncTask: async () => baseTask },
    "@/service/data-sync/versions": versions,
    "@/service/datasource": {
      getDataSource: (id, signal) =>
        new Promise((resolve) => connectionRequests.push({ id, signal, resolve })),
    },
    "./form-state": initialFormContract,
  },
);
const renderRestoration = (currentForm = form) => {
  restorationRefIndex = 0;
  const result = restorationModule.useDefinitionRestoration({
    task: baseTask,
    form: currentForm,
    closeDrawer: () => {
      closedDrawers += 1;
    },
    confirm: (value) => confirmations.push(value),
    apply: (value) => appliedConfigurations.push(value),
    replaceRemote: () => {},
  });
  restorationMounted = true;
  return result;
};
const fulfillConnections = (requests) => {
  for (const pending of requests)
    pending.resolve({ id: pending.id, dbType: pending.id === "mysql" ? "MYSQL" : "POSTGRE_SQL" });
};
const firstRestoration = renderRestoration();
const pendingRestoration = firstRestoration.restore(oldVersion);
fulfillConnections(connectionRequests.splice(0));
await pendingRestoration;
renderRestoration({ ...form, name: "edited while preparing", remark: "new remark", debug: true });
confirmations.pop().apply();
assert.equal(appliedConfigurations.at(-1).form.name, "edited while preparing");
assert.equal(appliedConfigurations.at(-1).form.remark, "new remark");
assert.equal(appliedConfigurations.at(-1).form.debug, true);
const pendingCanceled = renderRestoration().restore(oldVersion);
const canceledRequests = connectionRequests.splice(0);
renderRestoration().cancel();
fulfillConnections(canceledRequests);
await pendingCanceled;
assert.equal(confirmations.length, 0, "Closing the version drawer cancels delayed confirmation");
assert.equal(closedDrawers, 1, "Canceled restore cannot close a different drawer");
const reopened = renderRestoration().restore({ ...oldVersion, version: 2 });
fulfillConnections(connectionRequests.splice(0));
await reopened;
assert.match(confirmations.pop().title, /V2/);
const superseded = renderRestoration().restore(oldVersion);
const olderRequests = connectionRequests.splice(0);
const newer = renderRestoration().restore({ ...oldVersion, version: 3 });
fulfillConnections(olderRequests);
await superseded;
assert.equal(confirmations.length, 0);
fulfillConnections(connectionRequests.splice(0));
await newer;
assert.match(confirmations.pop().title, /V3/);
const unmounted = renderRestoration().restore(oldVersion);
disposeRestoration();
fulfillConnections(connectionRequests.splice(0));
await unmounted;
assert.equal(confirmations.length, 0, "An old Workspace response cannot change the next editor");
console.log("History, engine validation and canceled asynchronous restoration contracts passed");
