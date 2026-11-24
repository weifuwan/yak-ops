import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  buildHttpTaskParams,
  parseHttpTaskForm,
  validateHttpTaskForm,
} from "../apps/web/app/data-development/task-editor/http-task-editor-adapter.ts";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const editor = read("../apps/web/app/data-development/task-editor/http-task-editor.tsx");
const router = read("../apps/web/app/data-development/task-editor/task-editor.tsx");
const workflowStore = read("../apps/web/app/data-development/workflow-editor/store/workflow-store.tsx");

test("HTTP adapter maps the frontend form to the existing HttpParameters contract", () => {
  const form = parseHttpTaskForm({
    url: "https://api.example.com/users",
    httpMethod: "POST",
    httpParams: [
      { prop: "Authorization", httpParametersType: "HEADERS", value: "Bearer token" },
      { prop: "page", httpParametersType: "PARAMETER", value: "2" },
    ],
    httpBody: '{"name":"\${userName}"}',
    httpCheckCondition: "BODY_CONTAINS",
    condition: "success",
    connectTimeout: "12000",
    localParams: [{ prop: "userName", direct: "IN", type: "VARCHAR", value: "Yak" }],
    resourceList: [{ resourceName: "http/payload.json" }],
  });

  const params = buildHttpTaskParams(form, { customExistingField: "keep-me" });

  assert.equal(params.url, "https://api.example.com/users");
  assert.equal(params.httpMethod, "POST");
  assert.deepEqual(params.httpParams, [
    { prop: "Authorization", httpParametersType: "HEADERS", value: "Bearer token" },
    { prop: "page", httpParametersType: "PARAMETER", value: "2" },
  ]);
  assert.equal(params.httpBody, '{"name":"\${userName}"}');
  assert.equal(params.httpCheckCondition, "BODY_CONTAINS");
  assert.equal(params.condition, "success");
  assert.equal(params.connectTimeout, 12000);
  assert.deepEqual(params.localParams.map((item) => item.prop), ["userName"]);
  assert.deepEqual(params.resourceList, [{ resourceName: "http/payload.json" }]);
  assert.equal(params.customExistingField, "keep-me");
});

test("HTTP adapter supplies backend-valid defaults for a newly created node", () => {
  const form = parseHttpTaskForm({});

  assert.equal(form.httpMethod, "GET");
  assert.equal(form.httpCheckCondition, "STATUS_CODE_DEFAULT");
  assert.equal(form.connectTimeout, "10000");
  assert.deepEqual(form.httpParams, []);
  assert.deepEqual(form.resourceList, []);
  assert.deepEqual(form.localParams, []);
  assert.equal(validateHttpTaskForm({ ...form, url: "https://api.example.com" }), undefined);
});

test("HTTP adapter preserves string taskParams and normalizes backend enums", () => {
  const form = parseHttpTaskForm(JSON.stringify({
    url: "https://api.example.com",
    httpMethod: "PATCH",
    httpParams: [{ prop: "Accept", httpParametersType: "HEADERS", value: "application/json" }],
    httpCheckCondition: "UNKNOWN",
    connectTimeout: 5000,
  }));

  assert.equal(form.httpMethod, "GET");
  assert.equal(form.httpCheckCondition, "STATUS_CODE_DEFAULT");
  assert.equal(form.connectTimeout, "5000");
  assert.equal(form.httpParams[0].httpParametersType, "HEADERS");
});

test("HTTP validation prevents invalid timeout, duplicate parameters and invalid JSON bodies", () => {
  const base = parseHttpTaskForm({ url: "https://api.example.com" });

  assert.match(validateHttpTaskForm({ ...base, connectTimeout: "0" }) ?? "", /请求超时/);
  assert.match(validateHttpTaskForm({
    ...base,
    httpParams: [
      { prop: "Authorization", httpParametersType: "HEADERS", value: "a" },
      { prop: "authorization", httpParametersType: "HEADERS", value: "b" },
    ],
  }) ?? "", /重复名称/);
  assert.match(validateHttpTaskForm({
    ...base,
    httpMethod: "POST",
    httpBody: '["not", "an object"]',
  }) ?? "", /JSON 对象/);
  assert.equal(validateHttpTaskForm({
    ...base,
    httpMethod: "GET",
    httpBody: 'not-json-but-ignored-for-get',
  }), undefined);
});

test("HTTP editor uses the shared resource and local parameter panel and has a dedicated route", () => {
  assert.match(router, /import \{ HttpTaskEditor \} from "\.\/http-task-editor"/);
  assert.match(router, /isHttpTask \? \(/);
  assert.match(editor, /NodeResourceParamsPanel/);
  assert.match(editor, /resourceList=\{form\.resourceList\}/);
  assert.match(editor, /localParams=\{form\.localParams\}/);
  assert.match(editor, /title="请求头"/);\n  assert.match(editor, /title="查询参数"/);
  assert.match(editor, /CollapseSection/);\n  assert.match(editor, /id="http-request"/);\n  assert.match(editor, /id="http-response-check"/);\n  assert.match(editor, /title="响应检查"/);
  assert.match(workflowStore, /case "HTTP":[\s\S]*?connectTimeout: 10000/);
});
