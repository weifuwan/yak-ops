import assert from "node:assert/strict";
import test from "node:test";

import { resolveDocumentTitle } from "../apps/web/app/layout/document-title.ts";

test("browser titles reuse Chinese navigation labels", () => {
  assert.equal(resolveDocumentTitle("/overview"), "概览");
  assert.equal(resolveDocumentTitle("/data-source"), "数据源管理");
  assert.equal(resolveDocumentTitle("/data-sync"), "数据同步");
  assert.equal(resolveDocumentTitle("/data-development"), "数据开发");
  assert.equal(resolveDocumentTitle("/operations/workflows/instances"), "工作流实例");
  assert.equal(resolveDocumentTitle("/management/users"), "用户管理");
  assert.equal(resolveDocumentTitle("/resources"), "资源文件");
});

test("modeling titles use the deepest matching navigation item", () => {
  assert.equal(resolveDocumentTitle("/kimball"), "首页");
  assert.equal(resolveDocumentTitle("/kimball/planning/business-type"), "业务分类");
  assert.equal(resolveDocumentTitle("/kimball/standards/codes"), "标准代码");
  assert.equal(resolveDocumentTitle("/kimball/models/positive"), "维度建模");
  assert.equal(resolveDocumentTitle("/kimball/indicators/atom"), "原子指标");
});

test("data sync creation and detail routes have distinct titles", () => {
  assert.equal(resolveDocumentTitle("/data-sync/new"), "新建数据同步任务");
  assert.equal(resolveDocumentTitle("/data-sync/new/database"), "新建数据库同步任务");
  assert.equal(resolveDocumentTitle("/data-sync/new/multi-table"), "新建多表同步任务");
  assert.equal(resolveDocumentTitle("/data-sync/new/single-table"), "新建单表同步任务");
  assert.equal(resolveDocumentTitle("/data-sync/task-1/edit"), "编辑数据同步任务");
  assert.equal(resolveDocumentTitle("/data-sync/task-1/detail"), "数据同步任务详情");
});

test("Data Agent pages use a stable title and omit the product suffix", () => {
  assert.equal(resolveDocumentTitle("/data-agent"), "Data Agent");
  assert.equal(resolveDocumentTitle("/data-agent/sessions/session-1"), "Data Agent");
  assert.equal(resolveDocumentTitle("/data-agent/particle-preview"), "粒子效果预览");
  assert.doesNotMatch(resolveDocumentTitle("/data-agent"), /Yak Ops/);
});

test("trailing slashes are normalized and unknown routes fall back safely", () => {
  assert.equal(resolveDocumentTitle("/data-source/"), "数据源管理");
  assert.equal(resolveDocumentTitle("/unknown-route"), "Yak Ops");
});
