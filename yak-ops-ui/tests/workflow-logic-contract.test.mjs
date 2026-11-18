import test from "node:test";
import { readFileSync } from "node:fs";
import assert from "node:assert/strict";

import {
  applyWorkflowBranchTargets,
  getWorkflowBranchOutputs,
  resolveWorkflowSourceHandle,
} from "../apps/web/app/data-development/workflow-editor/adapter/workflow-branch-adapter.ts";
import {
  buildConditionLogicExpression,
  createConditionDependentItem,
  getConditionWorkflowGraph,
  normalizeConditionsTaskParams,
  validateConditionsTaskParams,
} from "../apps/web/app/data-development/task-editor/logic/conditions/adapter.ts";
import {
  normalizeDependentTaskParams,
  createDependentItem,
  validateDependentTaskParams,
} from "../apps/web/app/data-development/task-editor/logic/dependent/adapter.ts";
import {
  getSwitchCaseWarnings,
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

test("CONDITIONS writes multiple targets and never writes the runtime result", () => {
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
  assert.equal("conditionSuccess" in result.conditionResult, false);
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

test("CONDITIONS rejects unknown handles and duplicate targets across branches", () => {
  const params = {
    dependence: { relation: "AND", dependTaskList: [] },
    conditionResult: {},
  };

  assert.throws(
    () => applyWorkflowBranchTargets(
      "CONDITIONS",
      params,
      "100",
      [
        { source: "100", sourceHandle: "success", target: "201" },
        { source: "100", sourceHandle: "failed", target: "202" },
        { source: "100", sourceHandle: "source", target: "203" },
      ],
    ),
    /未识别的分支连线/,
  );

  assert.throws(
    () => applyWorkflowBranchTargets(
      "CONDITIONS",
      params,
      "100",
      [
        { source: "100", sourceHandle: "success", target: "201" },
        { source: "100", sourceHandle: "failed", target: "201" },
      ],
    ),
    /不能重复连接/,
  );
});

test("CONDITIONS creates only the two business fields for a dependency item", () => {
  assert.deepEqual(createConditionDependentItem(), {
    depTaskCode: 0,
    status: "SUCCESS",
  });
});

test("CONDITIONS normalizes legacy fields into the minimal parameter contract", () => {
  const normalized = normalizeConditionsTaskParams(JSON.stringify({
    localParams: [{
      prop: "bizDate",
      direct: "IN",
      type: "DATE",
      value: "${system.biz.date}",
      sensitive: false,
    }],
    dependence: {
      relation: "OR",
      dependTaskList: [{
        relation: "AND",
        dependItemList: [{
          dependentType: "DEPENDENT_ON_TASK",
          projectCode: 1,
          definitionCode: 10,
          depTaskCode: 20,
          cycle: "day",
          dateValue: "today",
          dependResult: "SUCCESS",
          parameterPassing: false,
          status: "SUCCESS",
        }],
      }],
    },
    conditionResult: {
      conditionSuccess: true,
      successNode: ["30", 30],
      failedNode: [40],
    },
  }));

  const item = normalized.dependence.dependTaskList[0].dependItemList[0];
  assert.equal(normalized.dependence.relation, "OR");
  assert.deepEqual(normalized.localParams, [{
    prop: "bizDate",
    direct: "IN",
    type: "DATE",
    value: "${system.biz.date}",
    sensitive: false,
  }]);
  assert.deepEqual(item, { depTaskCode: 20, status: "SUCCESS" });
  assert.deepEqual(normalized.conditionResult, {
    successNode: [30],
    failedNode: [40],
  });
  assert.equal("conditionSuccess" in normalized.conditionResult, false);
});

test("CONDITIONS builds a live expression for a single condition group", () => {
  const params = {
    dependence: {
      relation: "OR",
      dependTaskList: [{
        relation: "AND",
        dependItemList: [
          { depTaskCode: 10, status: "SUCCESS" },
          { depTaskCode: 20, status: "FAILURE" },
        ],
      }],
    },
  };
  const tasks = [
    { code: 10, name: "上游 SQL", taskType: "SQL" },
    { code: 20, name: "上游 JAVA", taskType: "JAVA" },
  ];

  assert.equal(
    buildConditionLogicExpression(params, tasks),
    "上游 SQL = 成功 AND 上游 JAVA = 失败",
  );
});

test("CONDITIONS groups expressions and applies the outer relation", () => {
  const params = {
    dependence: {
      relation: "OR",
      dependTaskList: [
        {
          relation: "AND",
          dependItemList: [
            { depTaskCode: 10, status: "SUCCESS" },
            { depTaskCode: 20, status: "FORCED_SUCCESS" },
          ],
        },
        {
          relation: "OR",
          dependItemList: [
            { depTaskCode: 30, status: "FAILURE" },
            { depTaskCode: 40, status: "PAUSE" },
          ],
        },
      ],
    },
  };
  const tasks = [
    { code: 10, name: "任务 A", taskType: "SQL" },
    { code: 20, name: "任务 B", taskType: "JAVA" },
    { code: 30, name: "任务 C", taskType: "SHELL" },
    { code: 40, name: "任务 D", taskType: "PYTHON" },
  ];

  assert.equal(
    buildConditionLogicExpression(params, tasks),
    "(任务 A = 成功 AND 任务 B = 强制成功) OR (任务 C = 失败 OR 任务 D = 已暂停)",
  );
});

test("CONDITIONS expression preview stays empty until every task and status is resolvable", () => {
  const params = {
    dependence: {
      relation: "AND",
      dependTaskList: [{
        relation: "AND",
        dependItemList: [{ depTaskCode: 0, status: "SUCCESS" }],
      }],
    },
  };

  assert.equal(buildConditionLogicExpression(params, []), null);
  assert.equal(
    buildConditionLogicExpression(params, [{ code: 10, name: "任务 A", taskType: "SQL" }]),
    null,
  );
  assert.equal(
    buildConditionLogicExpression({
      dependence: { relation: "AND", dependTaskList: [{ relation: "OR", dependItemList: [] }] },
    }, []),
    null,
  );
});

test("CONDITIONS validates dependency tasks against the current DAG predecessors", () => {
  const params = {
    dependence: {
      relation: "AND",
      dependTaskList: [{
        relation: "OR",
        dependItemList: [{ depTaskCode: 20, status: "SUCCESS" }],
      }],
    },
    conditionResult: { successNode: [], failedNode: [] },
  };

  assert.equal(validateConditionsTaskParams(params, [20, 21]), null);
  assert.match(validateConditionsTaskParams(params, [21]), /不是当前节点的上游任务/);
  assert.match(
    validateConditionsTaskParams({
      ...params,
      dependence: { ...params.dependence, dependTaskList: [] },
    }, [20]),
    /至少需要配置一个依赖条件组/,
  );
});

test("CONDITIONS derives upstream choices and branch targets from current canvas edges", () => {
  const nodes = [
    { id: "10", data: { taskCode: 10, name: "上游 SQL", taskType: "SQL" } },
    { id: "20", data: { taskCode: 20, name: "上游 Shell", taskType: "SHELL" } },
    { id: "30", data: { taskCode: 30, name: "条件节点", taskType: "CONDITIONS" } },
    { id: "40", data: { taskCode: 40, name: "成功下游", taskType: "JAVA" } },
    { id: "50", data: { taskCode: 50, name: "失败下游", taskType: "PYTHON" } },
    { id: "60", data: { taskCode: 60, name: "未识别下游", taskType: "SHELL" } },
    { id: "70", data: { taskCode: 70, name: "无关节点", taskType: "SQL" } },
  ];
  const edges = [
    { source: "10", target: "30" },
    { source: "20", target: "30" },
    { source: "30", sourceHandle: "success", target: "40" },
    { source: "30", sourceHandle: "failed", target: "50" },
    { source: "30", sourceHandle: "unknown", target: "60" },
    { source: "70", target: "10" },
  ];

  const graph = getConditionWorkflowGraph(nodes, edges, "30");
  assert.deepEqual(graph.upstreamTasks.map((item) => item.code), [10, 20]);
  assert.deepEqual(graph.successTargets.map((item) => item.code), [40]);
  assert.deepEqual(graph.failedTargets.map((item) => item.code), [50]);
  assert.equal(graph.unassignedTargetCount, 1);
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

test("SWITCH exposes every configured Case plus Default, including six or more Cases", () => {
  const params = {
    switchResult: {
      dependTaskList: Array.from({ length: 6 }, (_item, index) => ({
        condition: "${amount} >= " + (100 - index * 10),
        nextNode: 201 + index,
      })),
      nextNode: 207,
    },
  };

  assert.deepEqual(
    getWorkflowBranchOutputs("SWITCH", params).map((item) => item.id),
    ["case-0", "case-1", "case-2", "case-3", "case-4", "case-5", "default"],
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

test("SWITCH supports a Default-only configuration", () => {
  const params = {
    switchResult: {
      dependTaskList: [],
      nextNode: 203,
    },
  };

  assert.deepEqual(
    getWorkflowBranchOutputs("SWITCH", params).map((item) => item.id),
    ["default"],
  );
  assert.equal(resolveWorkflowSourceHandle("SWITCH", params, 203), "default");
  assert.equal(validateSwitchTaskParams(params), null);

  const result = applyWorkflowBranchTargets(
    "SWITCH",
    { switchResult: { dependTaskList: [], nextNode: null } },
    "100",
    [{ source: "100", sourceHandle: "default", target: "203" }],
  );

  assert.deepEqual(result.switchResult.dependTaskList, []);
  assert.equal(result.switchResult.nextNode, 203);
});

test("SWITCH validates the Default edge for Default-only configurations", () => {
  const params = { switchResult: { dependTaskList: [], nextNode: null } };

  assert.throws(
    () => applyWorkflowBranchTargets("SWITCH", params, "100", []),
    /Default 分支\s+必须且只能连接一个下游节点/,
  );
  assert.throws(
    () => applyWorkflowBranchTargets(
      "SWITCH",
      params,
      "100",
      [
        { source: "100", sourceHandle: "default", target: "203" },
        { source: "100", sourceHandle: "default", target: "204" },
      ],
    ),
    /Default 分支\s+必须且只能连接一个下游节点/,
  );
  assert.throws(
    () => applyWorkflowBranchTargets(
      "SWITCH",
      params,
      "100",
      [
        { source: "100", sourceHandle: "default", target: "203" },
        { source: "100", sourceHandle: "unknown", target: "204" },
      ],
    ),
    /未识别的分支连线/,
  );
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

test("SWITCH keeps new preview connections attached to the same Case across subsequent edits", () => {
  const original = {
    switchResult: {
      dependTaskList: [
        { condition: "${amount} >= 100", nextNode: 201 },
        { condition: "${amount} >= 50", nextNode: 202 },
      ],
      nextNode: null,
    },
  };
  const firstPreview = {
    switchResult: {
      dependTaskList: [
        { condition: "${amount} >= 50", nextNode: 202 },
        { condition: "${amount} > 10", nextNode: null },
        { condition: "${amount} >= 100", nextNode: 201 },
      ],
      nextNode: null,
    },
  };
  const secondPreview = {
    switchResult: {
      dependTaskList: [
        { condition: "${amount} > 10", nextNode: null },
        { condition: "${amount} >= 100", nextNode: 201 },
        { condition: "${amount} >= 50", nextNode: 202 },
      ],
      nextNode: null,
    },
  };
  const originalEdges = [
    { id: "a", source: "100", sourceHandle: "case-0", target: "201" },
    { id: "b", source: "100", sourceHandle: "case-1", target: "202" },
  ];

  const firstEdges = remapSwitchCaseHandles(
    "100",
    original,
    firstPreview,
    originalEdges,
  );
  const withNewConnection = [
    ...firstEdges,
    { id: "c", source: "100", sourceHandle: "case-1", target: "203" },
  ];
  const secondEdges = remapSwitchCaseHandles(
    "100",
    firstPreview,
    secondPreview,
    withNewConnection,
  );

  assert.equal(secondEdges.find((edge) => edge.target === "201").sourceHandle, "case-1");
  assert.equal(secondEdges.find((edge) => edge.target === "202").sourceHandle, "case-2");
  assert.equal(secondEdges.find((edge) => edge.target === "203").sourceHandle, "case-0");
});

test("SWITCH keeps a connected Case edge when its expression changes", () => {
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
        { condition: "${amount} > 200", nextNode: 201 },
        { condition: "${amount} >= 50", nextNode: 202 },
      ],
      nextNode: 203,
    },
  };
  const edges = [
    { id: "a", source: "100", sourceHandle: "case-0", target: "201" },
    { id: "b", source: "100", sourceHandle: "case-1", target: "202" },
  ];

  const remapped = remapSwitchCaseHandles("100", previous, next, edges);
  assert.equal(remapped.find((edge) => edge.target === "201").sourceHandle, "case-0");
  assert.equal(remapped.find((edge) => edge.target === "202").sourceHandle, "case-1");
});

test("SWITCH removes only the deleted Case connection", () => {
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
      dependTaskList: [{ condition: "${amount} >= 50", nextNode: 202 }],
      nextNode: 203,
    },
  };
  const edges = [
    { id: "a", source: "100", sourceHandle: "case-0", target: "201" },
    { id: "b", source: "100", sourceHandle: "case-1", target: "202" },
    { id: "c", source: "100", sourceHandle: "default", target: "203" },
  ];

  const remapped = remapSwitchCaseHandles("100", previous, next, edges);
  assert.deepEqual(remapped.map((edge) => edge.id), ["b", "c"]);
  assert.equal(remapped.find((edge) => edge.target === "202").sourceHandle, "case-0");
  assert.equal(remapped.find((edge) => edge.target === "203").sourceHandle, "default");
});

test("SWITCH variable selector includes runtime system, project, workflow, and task parameters with data types", () => {
  const options = getSwitchVariableOptions(
    JSON.stringify([
      { prop: "bizDate", type: "DATE", direct: "IN" },
      { prop: "shared", type: "VARCHAR", direct: "IN" },
    ]),
    {
      localParams: [
        { prop: "amount", type: "INTEGER", direct: "IN" },
        { prop: "shared", type: "VARCHAR", direct: "IN" },
      ],
    },
    [
      { paramName: "projectRegion", paramDataType: "VARCHAR" },
      { paramName: "shared", paramDataType: "VARCHAR" },
    ],
  );
  const byValue = new Map(options.map((item) => [item.value, item]));

  assert.equal(byValue.get("${system.biz.date}")?.label, "system.biz.date（系统 · DATE）");
  assert.equal(byValue.get("${system.project.code}")?.label, "system.project.code（系统 · LONG）");
  assert.equal(byValue.get("${projectRegion}")?.label, "projectRegion（项目 · VARCHAR）");
  assert.equal(byValue.get("${bizDate}")?.label, "bizDate（全局 · DATE）");
  assert.equal(byValue.get("${amount}")?.label, "amount（节点 · INTEGER）");
  assert.equal(byValue.get("${shared}")?.label, "shared（节点 · VARCHAR）");
  assert.equal(byValue.get("${amount}")?.dataType, "INTEGER");
});

test("SWITCH variable selector exposes only transitive upstream OUT parameters", () => {
  const options = getSwitchVariableOptions(
    [],
    { localParams: [] },
    [],
    [
      {
        code: 10,
        name: "上游 SQL",
        taskParams: {
          localParams: [
            { prop: "customerLevel", direct: "OUT", type: "VARCHAR" },
            { prop: "internalValue", direct: "IN", type: "INTEGER" },
          ],
        },
      },
      {
        code: 20,
        name: "上游 JAVA",
        taskParams: {
          localParams: [{ prop: "amount", direct: "OUT", type: "DECIMAL" }],
        },
      },
      {
        code: 99,
        name: "无关任务",
        taskParams: {
          localParams: [{ prop: "ignored", direct: "OUT", type: "STRING" }],
        },
      },
    ],
    [
      { preTaskCode: 10, postTaskCode: 20 },
      { preTaskCode: 20, postTaskCode: 30 },
      { preTaskCode: 99, postTaskCode: 98 },
    ],
    30,
  );
  const byValue = new Map(options.map((item) => [item.value, item]));

  assert.equal(byValue.get("${customerLevel}")?.label, "customerLevel（上游 上游 SQL · VARCHAR）");
  assert.equal(byValue.get("${amount}")?.label, "amount（上游 上游 JAVA · DECIMAL）");
  assert.equal(byValue.has("${internalValue}"), false);
  assert.equal(byValue.has("${ignored}"), false);
});

test("SWITCH warns when an earlier numeric Case covers a later Case", () => {
  const warnings = getSwitchCaseWarnings({
    switchResult: {
      dependTaskList: [
        { condition: "${amount} >= 500", nextNode: 201 },
        { condition: "${amount} >= 1000", nextNode: 202 },
        { condition: "${age} <= 100", nextNode: 203 },
        { condition: "${age} <= 50", nextNode: 204 },
        { condition: "${score} > 10", nextNode: 205 },
        { condition: "${score} > 20", nextNode: 206 },
      ],
    },
  });

  assert.deepEqual(
    warnings.map((warning) => [warning.caseIndex, warning.previousCaseIndex]),
    [[1, 0], [3, 2], [5, 4]],
  );
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

test("SWITCH rejects multiple branches that target the same downstream task", () => {
  const params = {
    switchResult: {
      dependTaskList: [
        { condition: "${amount} > 100", nextNode: null },
        { condition: "${amount} > 50", nextNode: null },
      ],
      nextNode: null,
    },
  };

  assert.throws(
    () => applyWorkflowBranchTargets(
      "SWITCH",
      params,
      "100",
      [
        { source: "100", sourceHandle: "case-0", target: "201" },
        { source: "100", sourceHandle: "case-1", target: "201" },
        { source: "100", sourceHandle: "default", target: "203" },
      ],
    ),
    /不能连接到同一个下游节点/,
  );

  assert.throws(
    () => applyWorkflowBranchTargets(
      "SWITCH",
      { switchResult: { dependTaskList: [{ condition: "${amount} > 0", nextNode: null }], nextNode: null } },
      "100",
      [
        { source: "100", sourceHandle: "case-0", target: "201" },
        { source: "100", sourceHandle: "default", target: "201" },
      ],
    ),
    /不能连接到同一个下游节点/,
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

const conditionsRunConfigSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/logic/conditions/components/conditions-run-config-panel.tsx", import.meta.url),
  "utf8",
);
const switchRunConfigSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/logic/switch/components/switch-run-config-panel.tsx", import.meta.url),
  "utf8",
);
const loadingIndicatorSource = readFileSync(
  new URL("../apps/web/app/components/loading-indicator.tsx", import.meta.url),
  "utf8",
);
const appLoadingSource = readFileSync(
  new URL("../apps/web/app/router/AppLoading.tsx", import.meta.url),
  "utf8",
);
const taskEditorSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/task-editor.tsx", import.meta.url),
  "utf8",
);
const workflowEditorSource = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/workflow-editor.tsx", import.meta.url),
  "utf8",
);


test("CONDITIONS and SWITCH expose only the compact Master logic run configuration", () => {
  for (const source of [conditionsRunConfigSource, switchRunConfigSource]) {
    assert.match(source, /title="调度配置"/);
    assert.match(source, /title="失败重试"/);
    assert.match(source, /title="超时告警"/);

    for (const field of [
      'label="任务优先级"',
      'label="延迟执行（分钟）"',
      'label="TaskGroup"',
      'label="组内优先级"',
      'label="最大重试次数"',
      'label="重试间隔"',
      'label="超时告警"',
      'label="超时时间（分钟）"',
    ]) {
      assert.ok(source.includes(field), "Missing run config field: " + field);
    }

    assert.doesNotMatch(
      source,
      /<SettingField label="(?:Worker Group|Environment Code|CPU 配额|最大内存|执行类型|超时处理策略)"/,
    );
    assert.match(source, /environmentCode: -1/);
    assert.match(source, /cpuQuota: -1/);
    assert.match(source, /memoryMax: -1/);
    assert.match(source, /taskExecuteType: "BATCH"/);
    assert.match(source, /timeoutNotifyStrategy: settings\.timeoutFlag === "OPEN" \? "WARN" : null/);
    assert.match(source, /workerGroup: task\?\.workerGroup\?\.trim\(\) \|\| "default"/);
    assert.match(source, /Number\(settings\.failRetryTimes\) > 0/);
    assert.match(source, /\{settings\.taskGroupId > 0 \? \(/);
    assert.match(source, /\{settings\.timeoutFlag === "OPEN" \? \(/);
    assert.match(source, /超时后仅发送告警，不会主动中断逻辑节点/);
  }
});

test("workflow and Shell tab loading states reuse the shared Yak loading indicator", () => {
  assert.match(loadingIndicatorSource, /YakMarkIcon/);
  assert.match(loadingIndicatorSource, /yak-loading-shimmer/);
  assert.match(appLoadingSource, /LoadingIndicator/);
  assert.match(taskEditorSource, /LoadingIndicator/);
  assert.match(workflowEditorSource, /LoadingIndicator/);

  assert.doesNotMatch(taskEditorSource, /正在加载 Shell 编辑器\.\.\./);
  assert.doesNotMatch(workflowEditorSource, /正在加载工作流…/);
});
