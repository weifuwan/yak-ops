import assert from "node:assert/strict";
import { test } from "node:test";
import {
  canStopWorkflow,
  isWorkflowActive,
  workflowStatusText,
} from "../apps/web/service/data-sync/workflow-state.ts";

test("serial wait is active, cancellable, and visible before task dispatch", () => {
  assert.equal(isWorkflowActive("SERIAL_WAIT"), true);
  assert.equal(canStopWorkflow("SERIAL_WAIT"), true);
  assert.equal(workflowStatusText("SERIAL_WAIT"), "排队中");
});

test("workflow submission is not confused with a running worker task", () => {
  assert.equal(workflowStatusText("SUBMITTED_SUCCESS"), "启动中");
  assert.equal(workflowStatusText("RUNNING_EXECUTION"), "启动中");
  assert.equal(canStopWorkflow("SUBMITTED_SUCCESS"), false);
  assert.equal(canStopWorkflow("RUNNING_EXECUTION"), true);
});

test("terminal states allow a new run while pause and stop-request stay protected", () => {
  for (const state of ["STOP", "FAILURE", "SUCCESS"]) {
    assert.equal(isWorkflowActive(state), false);
  }
  assert.equal(isWorkflowActive("PAUSE"), true);
  assert.equal(isWorkflowActive("READY_STOP"), true);
  assert.equal(workflowStatusText("READY_STOP"), "停止中");
});
