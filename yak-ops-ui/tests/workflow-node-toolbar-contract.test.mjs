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
const workflowEdge = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/canvas/workflow-edge.tsx", import.meta.url),
  "utf8",
);
const workflowSelector = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/node-selector/node-selector.tsx", import.meta.url),
  "utf8",
);
const workflowStore = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/store/workflow-store.tsx", import.meta.url),
  "utf8",
);
const workflowEditor = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/workflow-editor.tsx", import.meta.url),
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
  assert.match(workflowCanvas, /onOpenMenu: \(\{ x, y \}(?:: \{ x: number; y: number \})?\)\s*=>\s*setContextMenu\(\{ node, x, y \}\)/);
  assert.match(
    workflowCanvas,
    /openEdgeSelector\(\{\s*kind: "upstream-popover",\s*nodeId: node\.id,\s*anchor,/,
  );
  assert.match(workflowCanvas, /flowToScreenPosition/);

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


test("edge plus opens an anchored Dify-style picker without replacing the left selector", () => {
  assert.match(workflowEdge, /openEdgeSelector/);
  assert.match(workflowEdge, /data-workflow-edge-picker-trigger/);
  assert.match(workflowEdge, /anchor:\s*\{\s*x:\s*rect\.right,\s*y:\s*rect\.top \+ rect\.height \/ 2/s);
  assert.match(workflowSelector, /createPortal/);
  assert.match(workflowSelector, /role="dialog"/);
  assert.match(workflowSelector, /搜索节点类型/);
  assert.match(workflowSelector, /kind === "upstream-popover"/);
  assert.match(workflowSelector, /kind: "upstream", nodeId: request\.nodeId/);
  assert.match(workflowSelector, /"anchor" in request/);
  assert.match(workflowStore, /edgeSelectorRequest/);
  assert.match(workflowStore, /openEdgeSelector/);
  assert.match(workflowStore, /WorkflowPopoverSelectorRequest/);
  assert.match(workflowStore, /selectorRequest: null, edgeSelectorRequest/);
  assert.match(workflowStore, /requestOverride/);
  assert.match(workflowEditor, /WorkflowEdgeNodeSelector/);
});

test("workflow branch handles preserve the full selected node border without a white outline strip", () => {
  assert.match(workflowNode, /function AddHandleButton/);
  assert.match(workflowNode, /<mask/);
  assert.match(workflowNode, /!z-\[10\] !size-4/);
  assert.match(workflowNode, /stroke="#000"/);
  assert.doesNotMatch(workflowNode, /HandleOutlineGap/);
  assert.doesNotMatch(workflowNode, /borderRightColor:\s*"transparent"/);
  assert.match(workflowNode, /border-\[var\(--yak-color-primary\)\]/);
  assert.doesNotMatch(workflowNode, /after:absolute after:(?:left|right)-1\.5/);
  assert.doesNotMatch(workflowNode, /<Plus size=\{10\}/);
});

test("workflow branch handle circle has an inset SVG viewport to avoid edge clipping", () => {
  assert.match(workflowNode, /viewBox="0 0 18 18"/);
  assert.match(workflowNode, /className="relative size-\[18px\]"/);
  assert.match(workflowNode, /cx="9"\s+cy="9"\s+r="8"/);
  assert.match(workflowNode, /width="18"\s+height="18"/);
});
