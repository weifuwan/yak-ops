import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const workflowNode = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/canvas/workflow-node.tsx", import.meta.url),
  "utf8",
);
const workflowCanvas = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/canvas/workflow-canvas.tsx", import.meta.url),
  "utf8",
);
const workflowTypes = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/types.ts", import.meta.url),
  "utf8",
);

test("workflow node toolbar uses a bordered Dify-style play and more control", () => {
  assert.match(workflowNode, /border-\[#D0D5DD\] bg-white/);
  assert.match(workflowNode, /<Play size=\{13\}/);
  assert.match(workflowNode, /<MoreHorizontal size=\{15\}/);
  assert.match(workflowNode, /selected \|\| data\.menuOpen/);
  assert.match(workflowNode, /data\.onOpenMenu\?\.\(\{ x: rect\.left, y: rect\.bottom \}\)/);
});

test("workflow canvas wires the node toolbar to supported node actions", () => {
  assert.match(workflowCanvas, /onRun: \(\) =>\s*requestNodeRun\(/);
  assert.match(workflowCanvas, /onOpenMenu: \(\{ x, y \}\) => setContextMenu\(\{ node, x, y \}\)/);
  assert.match(
    workflowCanvas,
    /onClick=\{\(\) =>\s*openSelector\(\{\s*kind: "upstream",\s*nodeId: contextMenu\.node\.id,\s*\}\)\}/,
  );

  for (const label of [
    "打开节点",
    "新建上游",
    "从工作流移除",
    "运行该节点",
    "运行到该节点",
    "从该节点运行",
  ]) {
    assert.ok(workflowCanvas.includes(label), `missing node menu action: ${label}`);
  }
});

test("toolbar callbacks and open state are transient workflow node data", () => {
  assert.match(workflowTypes, /menuOpen\?: boolean/);
  assert.match(workflowTypes, /onRun\?: \(\) => void/);
  assert.match(workflowTypes, /onOpenMenu\?: \(position: \{ x: number; y: number \}\) => void/);
});
