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
});

test("SQL Monaco editor registers SQL syntax highlighting and supports read-only updates", () => {
  assert.match(codeEditor, /monaco-editor\/editor/);
  assert.match(codeEditor, /monaco\.languages\.register\(\{ id: "sql" \}\)/);
  assert.match(codeEditor, /setMonarchTokensProvider/);
  assert.match(codeEditor, /language: "sql"/);
  assert.match(codeEditor, /automaticLayout: true/);
  assert.match(codeEditor, /updateOptions\(\{ readOnly \}\)/);
});
