import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  buildSqlTaskParams,
  parseSqlTaskForm,
  toSqlTaskDbType,
  validateSqlTaskForm,
} from "../apps/web/app/data-development/task-editor/sql/adapter.ts";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const editor = read("../apps/web/app/data-development/task-editor/sql/index.tsx");
const router = read("../apps/web/app/data-development/task-editor/task-editor.tsx");
const workflowStore = read("../apps/web/app/data-development/workflow-editor/store/workflow-store.tsx");
const codeEditor = read("../apps/web/app/data-development/task-editor/sql/sql-code-editor.tsx");
const dorisPublishModal = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/doris-publish-modal.tsx");

const treeSelect = read("../packages/yak-ui/src/tree-select/TreeSelect.tsx");

test("SQL adapter preserves the backend SqlParameters contract and unknown fields", () => {
  const form = parseSqlTaskForm({
    type: "POSTGRE_SQL",
    datasource: 12,
    sql: "select 1",
    sqlSource: "SCRIPT",
    sqlType: 0,
    sendEmail: true,
    displayRows: 25,
    showType: "TABLE",
    connParams: "keep",
    preStatements: ["set x=1", ""],
    postStatements: ["reset x"],
    groupId: 3,
    title: "daily result",
    limit: 500,
    localParams: [{ prop: "bizDate", direct: "IN", type: "VARCHAR", value: "2026-11-24" }],
    customExistingField: "keep-me",
  });

  const params = buildSqlTaskParams(form, { customExistingField: "keep-me", untouched: true });
  assert.equal(params.type, "POSTGRESQL");
  assert.equal(params.datasource, 12);
  assert.equal(params.sql, "select 1");
  assert.equal(params.sqlSource, "SCRIPT");
  assert.equal(params.sqlType, 0);
  assert.equal(params.sendEmail, true);
  assert.equal(params.displayRows, 25);
  assert.equal(params.showType, "TABLE");
  assert.equal(params.connParams, "keep");
  assert.deepEqual(params.preStatements, ["set x=1"]);
  assert.deepEqual(params.postStatements, ["reset x"]);
  assert.equal(params.groupId, 3);
  assert.equal(params.title, "daily result");
  assert.equal(params.limit, 500);
  assert.equal(params.localParams[0].prop, "bizDate");
  assert.equal(params.customExistingField, "keep-me");
  assert.equal(params.untouched, true);
});

test("SQL adapter treats the default datasource zero sentinel as unselected", () => {
  assert.equal(parseSqlTaskForm({ datasource: 0 }).datasource, "");
  assert.equal(parseSqlTaskForm({ datasource: "0" }).datasource, "");
  assert.equal(parseSqlTaskForm({}).datasource, "");
  assert.equal(parseSqlTaskForm({ datasource: 42 }).datasource, "42");

  const emptyDatasourceForm = parseSqlTaskForm({
    datasource: 0,
    type: "MYSQL",
    sql: "select 1",
    sqlSource: "SCRIPT",
  });
  assert.match(validateSqlTaskForm(emptyDatasourceForm) ?? "", /请选择有效的数据源/);
});

test("SQL file mode persists only the resource path and normalizes legacy source values", () => {
  const form = parseSqlTaskForm({
    sql: "",
    sqlResource: "/resources/report.sql",
    sqlSource: "CUSTOM",
  });
  assert.equal(form.sqlSource, "FILE");
  const params = buildSqlTaskParams(form, { oldField: "preserve" });
  assert.equal(params.sql, "");
  assert.equal(params.sqlSource, "FILE");
  assert.equal(params.sqlResource, "/resources/report.sql");
  assert.equal(params.oldField, "preserve");

  const scriptForm = { ...form, sqlSource: "SCRIPT", sql: "select 1" };
  const scriptParams = buildSqlTaskParams(scriptForm);
  assert.equal(scriptParams.sql, "select 1");
  assert.equal(scriptParams.sqlResource, "");
});

test("SQL adapter normalizes the PostgreSQL datasource enum for SqlTask DbType.valueOf", () => {
  assert.equal(toSqlTaskDbType("POSTGRE_SQL"), "POSTGRESQL");
  assert.equal(toSqlTaskDbType("POSTGRES"), "POSTGRESQL");
  assert.equal(toSqlTaskDbType("MYSQL"), "MYSQL");
});

test("SQL form validation checks datasource, source, result limits and notification group", () => {
  const base = parseSqlTaskForm({
    type: "MYSQL",
    datasource: 1,
    sql: "select 1",
    sqlSource: "SCRIPT",
  });
  assert.equal(validateSqlTaskForm(base), undefined);
  assert.match(validateSqlTaskForm({ ...base, datasource: "0" }) ?? "", /数据源/);
  assert.match(validateSqlTaskForm({ ...base, sql: " " }) ?? "", /SQL 脚本/);
  assert.match(validateSqlTaskForm({ ...base, sqlSource: "FILE", sqlResource: "" }) ?? "", /SQL 资源文件/);
  assert.match(validateSqlTaskForm({ ...base, displayRows: "0" }) ?? "", /日志展示行数/);
  assert.match(validateSqlTaskForm({ ...base, limit: "-1" }) ?? "", /查询结果限制/);
  assert.match(validateSqlTaskForm({ ...base, sendEmail: true, groupId: "0" }) ?? "", /告警组/);
  assert.match(validateSqlTaskForm({ ...base, sqlType: "1", sendEmail: true, groupId: "1" }) ?? "", /仅支持查询 SQL/);
});

test("SQL task editor uses datasource, resource, local params, common settings and dedicated route", () => {
  assert.match(router, /const SqlTaskEditor = lazy/);
  assert.match(router, /isSqlTask/);
  assert.match(editor, /NodeEditorFrame/);
  assert.match(editor, /DataSyncSearchableSelect/);
  assert.match(editor, /listDataSources/);
  assert.match(editor, /DatabaseIcons/);
  assert.match(editor, /listWorkflowResources/);
  assert.match(editor, /TreeSelect/);
  assert.match(editor, /NodeResourceParamsPanel/);
  assert.match(editor, /TaskCommonSettingsSection/);
  assert.match(editor, /listAlertGroups/);
  assert.match(editor, /title="前置 SQL"/);
  assert.match(editor, /title="后置 SQL"/);
  assert.match(editor, /title="结果通知"/);
  assert.doesNotMatch(editor, /文件模式只保存资源路径/);
  assert.doesNotMatch(editor, /将查询结果发送到告警组/);
  assert.doesNotMatch(editor, /日志展示行数只控制执行日志打印的结果行数/);
  assert.match(editor, /最大返回行数填写 0 表示使用后端默认上限 10000。/);
  assert.match(editor, /function SqlSectionHeader/);
  assert.match(editor, /bg-\[#5664ff\]/);
  const statementsSection = editor.slice(
    editor.indexOf("function SqlStatementsSection"),
    editor.indexOf("function SqlTaskWorkspace"),
  );
  assert.match(statementsSection, /<SqlSectionHeader title={title} \/>/);
  assert.doesNotMatch(statementsSection, /<CollapseSection/);
  assert.match(statementsSection, /添加\{title\}/);
  assert.match(workflowStore, /sqlSource: "SCRIPT"/);
  const sqlCaseStart = workflowStore.indexOf('case "SQL":');
  const sqlCaseEnd = workflowStore.indexOf("\n    default:", sqlCaseStart);
  const sqlDefaults = workflowStore.slice(sqlCaseStart, sqlCaseEnd < 0 ? undefined : sqlCaseEnd);
  assert.match(sqlDefaults, /datasource: ""/);
  assert.doesNotMatch(sqlDefaults, /datasource:\s*0/);
  assert.ok(editor.includes("const legacyDatasourceId = Number(form.datasource);"));
  assert.ok(editor.includes("legacyDatasourceId > 0"));
});

test("SQL script section does not repeat the selected source mode as a header badge", () => {
  assert.match(editor, /id="sql-script"\s+title="SQL 脚本"/);
  assert.doesNotMatch(editor, /extra=\{<span[^>]*>\{form\.sqlSource === "SCRIPT" \? "SCRIPT" : "FILE"\}<\/span>\}/);
  assert.match(editor, /items=\{\{ SCRIPT: "自定义 SQL", FILE: "资源文件" \}\}/);
});

test("SQL editor keeps space input native to Monaco and preserves database type for the backend", () => {
  assert.match(codeEditor, /className="nokey h-full/);
  assert.match(codeEditor, /className="nokey flex/);
  assert.doesNotMatch(codeEditor, /renderWhitespace:\s*"all"/);
  assert.match(codeEditor, /onChangeRef\.current\(editor\.getValue\(\)\)/);
  assert.doesNotMatch(editor, /<SqlField label="数据库类型"/);
  assert.doesNotMatch(editor, /getDataSourceTypeLabel/);

  const adapter = read("../apps/web/app/data-development/task-editor/sql/adapter.ts");
  assert.match(adapter, /type: toSqlTaskDbType\(form\.type\)/);
  assert.equal(
    buildSqlTaskParams(
      parseSqlTaskForm({ type: "MYSQL", datasource: 12, sql: "select 1" }),
    ).type,
    "MYSQL",
  );
});

test("TreeSelect search autofocus does not move the surrounding page", () => {
  assert.match(treeSelect, /searchInputRef\.current\?\.focus\(\{ preventScroll: true \}\)/);
  assert.match(treeSelect, /ref=\{searchInputRef\}/);
  assert.doesNotMatch(treeSelect, /autoFocus/);
});

test("SQL Monaco editor registers SQL syntax highlighting and supports read-only updates", () => {
  assert.match(codeEditor, /monaco-editor\/editor/);
  assert.match(codeEditor, /monaco\.languages\.register\(\{ id: "sql" \}\)/);
  assert.match(codeEditor, /setMonarchTokensProvider/);
  assert.match(codeEditor, /language: "sql"/);
  assert.match(codeEditor, /automaticLayout: true/);
  assert.match(codeEditor, /updateOptions\(\{ readOnly \}\)/);
  assert.match(codeEditor, /resizable\?: SqlCodeEditorResizeOptions/);
  assert.match(codeEditor, /setPointerCapture/);
  assert.ok(codeEditor.includes('role="separator"'));
  assert.ok(codeEditor.includes("aria-valuemin={resizable.minHeight}"));
  assert.ok(codeEditor.includes("onKeyDown={handleResizeKeyDown}"));
});


test("Doris publish modal defaults to UNIQUE and incremental publish whenever it opens", () => {
  assert.match(dorisPublishModal, /useState<DorisTableModel \| "">\("UNIQUE"\)/);
  assert.match(dorisPublishModal, /useState<DorisPublishMode \| "">\("INCREMENTAL"\)/);

  const openReset = dorisPublishModal.slice(
    dorisPublishModal.indexOf("useEffect(() => {"),
    dorisPublishModal.indexOf("}, [open, tableId]);"),
  );
  assert.match(openReset, /setTableModel\("UNIQUE"\)/);
  assert.match(openReset, /setPublishMode\("INCREMENTAL"\)/);
});

test("Doris DDL preview reuses the SQL node Monaco editor without redundant helper labels", () => {
  assert.match(dorisPublishModal, /import \{ SqlCodeEditor \} from "@\/app\/data-development\/task-editor\/sql\/sql-code-editor";/);
  assert.match(
    dorisPublishModal,
    /<SqlCodeEditor\s+value=\{preview\?\.ddl \|\| ""\}\s+readOnly\s+onChange=\{\(\) => undefined\}\s+resizable=\{\{ initialHeight: 300, minHeight: 220, maxHeight: 560 \}\}\s+\/>/,
  );
  assert.doesNotMatch(dorisPublishModal, /UNIQUE KEY · 主键表|DUPLICATE KEY · 明细表|只读 · 自动生成/);

  const previewStart = dorisPublishModal.indexOf('<section className="border-t border-[var(--yak-components-panel-border)] pt-4">');
  const previewEnd = dorisPublishModal.indexOf("{previewError ? (", previewStart);
  const ddlPreview = dorisPublishModal.slice(previewStart, previewEnd);
  assert.doesNotMatch(ddlPreview, /<Textarea/);
  assert.doesNotMatch(ddlPreview, /h-\[300px\]/);
});
