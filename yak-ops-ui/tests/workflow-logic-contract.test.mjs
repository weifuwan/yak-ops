import test from "node:test";
import assert from "node:assert/strict";

import {
  applyWorkflowBranchTargets,
  getWorkflowBranchOutputs,
  resolveWorkflowSourceHandle,
} from "../apps/web/app/data-development/workflow-editor/adapter/workflow-branch-adapter.ts";
import { normalizeConditionsTaskParams } from "../apps/web/app/data-development/task-editor/logic/conditions/adapter.ts";
import {
  normalizeDependentTaskParams,
  createDependentItem,
  validateDependentTaskParams,
} from "../apps/web/app/data-development/task-editor/logic/dependent/adapter.ts";
import {
  getSwitchVariableOptions,
  remapSwitchCaseHandles,
  validateSwitchTaskParams,
} from "../apps/web/app/data-development/task-editor/logic/switch/adapter.ts";
import {
  hasWorkflowReferenceCycle,
  normalizeSubWorkflowTaskParams,
} from "../apps/web/app/data-development/task-editor/logic/sub-workflow/adapter.ts";

test("CONDITIONS restores success and failed handles from taskParams", () => {
  const params = {
    dependence: { relation: "AND", dependTaskList: [] },
    conditionResult: {
      conditionSuccess: false,
      successNode: [201, 202],
      failedNode: [203],
    },
  };

  assert.equal(resolveWorkflowSourceHandle("CONDITIONS", params, 201), "success");
  assert.equal(resolveWorkflowSourceHandle("CONDITIONS", params, 203), "failed");
  assert.equal(resolveWorkflowSourceHandle("CONDITIONS", params, 999), "unassigned");
  assert.deepEqual(
    getWorkflowBranchOutputs("CONDITIONS", params).map((item) => item.id),
    ["success", "failed"],
  );
});

test("CONDITIONS writes multiple targets to the backend branch arrays", () => {
  const result = applyWorkflowBranchTargets(
    "CONDITIONS",
    {
      dependence: { relation: "AND", dependTaskList: [] },
      conditionResult: { conditionSuccess: true, successNode: [], failedNode: [] },
    },
    "100",
    [
      { source: "100", sourceHandle: "success", target: "201" },
      { source: "100", sourceHandle: "success", target: "202" },
      { source: "100", sourceHandle: "failed", target: "203" },
    ],
  );

  assert.deepEqual(result.conditionResult.successNode, [201, 202]);
  assert.deepEqual(result.conditionResult.failedNode, [203]);
  assert.equal(result.conditionResult.conditionSuccess, false);
});

test("CONDITIONS rejects an incomplete branch graph", () => {
  assert.throws(
    () => applyWorkflowBranchTargets(
      "CONDITIONS",
      { dependence: { relation: "AND", dependTaskList: [] }, conditionResult: {} },
      "100",
      [{ source: "100", sourceHandle: "success", target: "201" }],
    ),
    /失败分支/,
  );
});

test("CONDITIONS normalizes serialized parameters without changing the backend field names", () => {
  const normalized = normalizeConditionsTaskParams(JSON.stringify({
    dependence: {
      relation: "OR",
      dependTaskList: [{
        relation: "AND",
        dependItemList: [{
          dependentType: "DEPENDENT_ON_TASK",
          projectCode: 1,
          definitionCode: 10,
          depTaskCode: 20,
          status: "SUCCESS",
        }],
      }],
    },
    conditionResult: { successNode: [30], failedNode: [40] },
  }));

  assert.equal(normalized.dependence.relation, "OR");
  assert.equal(normalized.dependence.dependTaskList[0].dependItemList[0].depTaskCode, 20);
  assert.deepEqual(normalized.conditionResult.successNode, [30]);
});

test("SWITCH restores Case and Default handles from taskParams", () => {
  const params = {
    switchResult: {
      dependTaskList: [
        { condition: "${amount} >= 100", nextNode: 201 },
        { condition: "${amount} >= 50", nextNode: 202 },
      ],
      nextNode: 203,
    },
  };

  assert.equal(resolveWorkflowSourceHandle("SWITCH", params, 201), "case-0");
  assert.equal(resolveWorkflowSourceHandle("SWITCH", params, 202), "case-1");
  assert.equal(resolveWorkflowSourceHandle("SWITCH", params, 203), "default");
  assert.deepEqual(
    getWorkflowBranchOutputs("SWITCH", params).map((item) => item.id),
    ["case-0", "case-1", "default"],
  );
});

test("SWITCH writes one target per Case and Default", () => {
  const result = applyWorkflowBranchTargets(
    "SWITCH",
    {
      switchResult: {
        dependTaskList: [
          { condition: "${amount} >= 100", nextNode: null },
          { condition: "${amount} >= 50", nextNode: null },
        ],
        nextNode: null,
      },
    },
    "100",
    [
      { source: "100", sourceHandle: "case-0", target: "201" },
      { source: "100", sourceHandle: "case-1", target: "202" },
      { source: "100", sourceHandle: "default", target: "203" },
    ],
  );

  assert.equal(result.switchResult.dependTaskList[0].nextNode, 201);
  assert.equal(result.switchResult.dependTaskList[1].nextNode, 202);
  assert.equal(result.switchResult.nextNode, 203);
  assert.equal(validateSwitchTaskParams(result), null);
});

test("SWITCH remaps connected edges when Case expressions are reordered", () => {
  const previous = {
    switchResult: {
      dependTaskList: [
        { condition: "${amount} >= 100", nextNode: 201 },
        { condition: "${amount} >= 50", nextNode: 202 },
      ],
      nextNode: 203,
    },
  };
  const next = {
    switchResult: {
      dependTaskList: [
        { condition: "${amount} >= 50", nextNode: 202 },
        { condition: "${amount} >= 100", nextNode: 201 },
      ],
      nextNode: 203,
    },
  };
  const edges = [
    { id: "a", source: "100", sourceHandle: "case-0", target: "201" },
    { id: "b", source: "100", sourceHandle: "case-1", target: "202" },
    { id: "c", source: "100", sourceHandle: "default", target: "203" },
  ];

  const remapped = remapSwitchCaseHandles("100", previous, next, edges);
  assert.equal(remapped.find((edge) => edge.target === "201").sourceHandle, "case-1");
  assert.equal(remapped.find((edge) => edge.target === "202").sourceHandle, "case-0");
  assert.equal(remapped.find((edge) => edge.target === "203").sourceHandle, "default");
});

test("SWITCH variable selector includes workflow and task input parameters", () => {
  const options = getSwitchVariableOptions(
    JSON.stringify([{ prop: "bizDate", type: "DATE", direct: "IN" }]),
    {
      localParams: [{ prop: "amount", type: "INTEGER", direct: "IN" }],
    },
  );
  assert.deepEqual(options.map((item) => item.value), ["${bizDate}", "${amount}"]);
});

test("SWITCH rejects missing expressions and duplicate cases", () => {
  assert.match(
    validateSwitchTaskParams({ switchResult: { dependTaskList: [{ condition: "" }] } }),
    /判断表达式/,
  );
  assert.match(
    validateSwitchTaskParams({
      switchResult: {
        dependTaskList: [
          { condition: "${amount} > 0" },
          { condition: "${amount} > 0" },
        ],
      },
    }),
    /不能重复/,
  );
});

test("DEPENDENT normalizes the existing backend dependency fields", () => {
  const normalized = normalizeDependentTaskParams({
    dependence: {
      relation: "OR",
      checkInterval: 30,
      failurePolicy: "DEPENDENT_FAILURE_WAITING",
      failureWaitingTime: 15,
      dependTaskList: [{
        relation: "AND",
        dependItemList: [{
          dependentType: "DEPENDENT_ON_WORKFLOW",
          projectCode: 1,
          definitionCode: 10,
          depTaskCode: 0,
          cycle: "day",
          dateValue: "today",
          dependResult: "SUCCESS",
          parameterPassing: false,
        }],
      }],
    },
  });

  assert.equal(normalized.dependence.relation, "OR");
  assert.equal(normalized.dependence.checkInterval, 30);
  assert.equal(normalized.dependence.failureWaitingTime, 15);
  assert.equal(normalized.dependence.dependTaskList[0].dependItemList[0].depTaskCode, 0);
});

test("DEPENDENT validates selected workflows and task codes", () => {
  const item = createDependentItem(1, 10);
  item.depTaskCode = 20;
  const valid = {
    dependence: {
      relation: "AND",
      checkInterval: 60,
      failurePolicy: "DEPENDENT_FAILURE_FAILURE",
      failureWaitingTime: 30,
      dependTaskList: [{ relation: "AND", dependItemList: [item] }],
    },
  };
  assert.equal(validateDependentTaskParams(valid), null);

  const invalid = {
    dependence: {
      relation: "AND",
      checkInterval: 60,
      failurePolicy: "DEPENDENT_FAILURE_FAILURE",
      failureWaitingTime: 30,
      dependTaskList: [{ relation: "AND", dependItemList: [createDependentItem(1, 10)] }],
    },
  };
  assert.match(validateDependentTaskParams(invalid), /请选择依赖任务/);

  const invalidInterval = {
    ...valid,
    dependence: { ...valid.dependence, checkInterval: 0 },
  };
  assert.match(validateDependentTaskParams(invalidInterval), /检查间隔/);
});

test("SUB_WORKFLOW detects direct and transitive circular references", async () => {
  const graphs = new Map([
    [200, { taskDefinitionList: [{ taskType: "SUB_WORKFLOW", taskParams: { workflowDefinitionCode: 300 } }] }],
    [300, { taskDefinitionList: [{ taskType: "SUB_WORKFLOW", taskParams: { workflowDefinitionCode: 100 } }] }],
    [400, { taskDefinitionList: [] }],
  ]);
  const loadWorkflow = async (code) => {
    const dag = graphs.get(code);
    if (!dag) throw new Error("missing workflow " + code);
    return dag;
  };

  assert.equal(await hasWorkflowReferenceCycle(100, 200, loadWorkflow), true);
  assert.equal(await hasWorkflowReferenceCycle(100, 400, loadWorkflow), false);
  assert.equal(await hasWorkflowReferenceCycle(100, 100, loadWorkflow), true);
  assert.equal(
    normalizeSubWorkflowTaskParams('{"workflowDefinitionCode":200}').workflowDefinitionCode,
    200,
  );
});

test("Non-logic task parameters pass through branch serialization unchanged", () => {
  const raw = "{\"rawScript\":\"echo ok\"}";
  assert.equal(applyWorkflowBranchTargets("SHELL", raw, "100", []), raw);
});
