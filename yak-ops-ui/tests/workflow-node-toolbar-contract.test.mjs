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
const workflowToolbar = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/toolbar/workflow-toolbar.tsx", import.meta.url),
  "utf8",
);
const taskTypeBadgeIcon = readFileSync(
  new URL("../apps/web/app/data-development/workflow-editor/components/task-type-badge-icon.tsx", import.meta.url),
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
  assert.match(workflowSelector, /kind === "downstream-popover"/);
  assert.match(workflowSelector, /kind: "downstream",\s*nodeId: request\.nodeId,\s*sourceHandle: request\.sourceHandle/s);
  assert.match(workflowSelector, /"anchor" in request/);
  assert.match(workflowStore, /edgeSelectorRequest/);
  assert.match(workflowStore, /openEdgeSelector/);
  assert.match(workflowStore, /WorkflowPopoverSelectorRequest/);
  assert.match(workflowStore, /openSelector: \(selectorRequest\) =>\s*set\(\{ selectorRequest \}\)/);
  assert.match(workflowStore, /openEdgeSelector: \(edgeSelectorRequest\) =>\s*set\(\{ edgeSelectorRequest \}\)/);
  assert.match(workflowStore, /requestOverride/);
  assert.match(workflowNode, /openEdgeSelector\(\{\s*kind: "downstream-popover"/);
  assert.match(workflowNode, /data-workflow-edge-picker-trigger/);
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


test("workflow node connector plus opens a local downstream picker without touching the left sidebar", () => {
  assert.match(workflowTypes, /kind: "downstream-popover"/);
  assert.match(workflowNode, /kind: "downstream-popover"[\s\S]*?anchor:/);
  assert.match(workflowSelector, /if \(isPopover\) closeSelector\(\)/);
  assert.match(workflowStore, /openSelector: \(selectorRequest\) =>\s*set\(\{ selectorRequest \}\)/);
  assert.match(workflowStore, /openEdgeSelector: \(edgeSelectorRequest\) =>\s*set\(\{ edgeSelectorRequest \}\)/);
  const insertionStart = workflowStore.indexOf("insertNodeFromSelector: (taskType");
  const insertionEnd = workflowStore.indexOf("addConnection:", insertionStart);
  const insertion = workflowStore.slice(insertionStart, insertionEnd);
  assert.doesNotMatch(insertion, /selectorRequest\s*:/);
  assert.doesNotMatch(insertion, /edgeSelectorRequest\s*:/);
});


test("canvas toolbar plus opens an anchored root picker while top toolbar alone opens the sidebar", () => {
  assert.ok(workflowCanvas.includes("openEdgeSelector({"));
  assert.ok(workflowCanvas.includes('kind: "add-popover"'));
  assert.ok(workflowCanvas.includes("anchor: {"));
  assert.ok(workflowCanvas.includes("data-workflow-edge-picker-trigger"));
  assert.ok(workflowCanvas.includes('edgeSelectorRequest?.kind === "add-popover"'));
  assert.doesNotMatch(workflowCanvas, /openSelector/);
  assert.ok(workflowSelector.includes('request.kind === "add-popover"'));
  assert.ok(workflowSelector.includes('? { kind: "add" }'));
  assert.ok(workflowSelector.includes('request.kind === "downstream-popover"'));
  assert.ok(workflowSelector.includes('? "downstream"'));
  assert.ok(workflowToolbar.includes('onClick={() => openSelector({ kind: "add" })}'));
});


test("floating workflow node picker uses larger Dify-style icons and a wider responsive panel", () => {
  assert.ok(workflowSelector.includes("group flex h-9 w-full"));
  assert.ok(workflowSelector.includes("rounded-md border border-transparent bg-white px-3 transition-colors hover:bg-[#F3F5F9]"));
  assert.ok(workflowSelector.includes("bg-transparent px-0 text-left text-xs"));
  assert.ok(workflowSelector.includes("rounded-[2px] border border-[#dcdee3] bg-white px-1.5"));
  assert.ok(workflowSelector.includes("mr-2.5 flex size-6 shrink-0"));
  assert.ok(workflowSelector.includes('size={variant === "popover" ? 20 : 16}'));
  assert.ok(workflowSelector.includes("const panelWidth = Math.min(340, window.innerWidth - 24)"));
  assert.ok(workflowSelector.includes("w-[340px] max-w-[calc(100vw-24px)]"));
  assert.ok(workflowSelector.includes("width: panelWidth"));
});


test("workflow task icons use Dify-style solid color containers and white glyphs", () => {
  assert.match(taskTypeBadgeIcon, /const TASK_TYPE_ICON_MAP/);
  assert.ok(taskTypeBadgeIcon.includes("border-[0.5px] border-white/10 text-white"));
  assert.ok(taskTypeBadgeIcon.includes("backgroundColor: spec.background"));
  assert.ok(taskTypeBadgeIcon.includes("<Icon size={iconSize} strokeWidth={2.1}"));
  assert.doesNotMatch(taskTypeBadgeIcon, /<text/);

  for (const taskType of [
    "CONDITIONS",
    "DATA_SYNC",
    "SQL",
    "PYTHON",
    "JAVA",
    "HTTP",
    "SHELL",
    "K8S",
  ]) {
    assert.ok(taskTypeBadgeIcon.includes(taskType), `missing task icon mapping: ${taskType}`);
  }

  assert.ok(taskTypeBadgeIcon.includes('JAVA: { icon: Coffee, background: "var(--yak-color-primary)" }'));
  assert.ok(taskTypeBadgeIcon.includes('normalized === "JAVA"'));
  assert.ok(taskTypeBadgeIcon.includes("M377.642667 791.893333s-39.125333 22.784"));
  assert.ok(taskTypeBadgeIcon.includes('HTTP: { icon: Globe, background: "var(--yak-color-primary)" }'));
  assert.ok(taskTypeBadgeIcon.includes('normalized === "HTTP"'));
  assert.ok(taskTypeBadgeIcon.includes("M192 469.333h-85.333V384h-64v256"));
  assert.ok(taskTypeBadgeIcon.includes('CONDITIONS: { icon: GitBranch, background: "var(--yak-color-primary)" }'));
  assert.ok(taskTypeBadgeIcon.includes('normalized === "CONDITIONS"'));
  assert.ok(taskTypeBadgeIcon.includes('viewBox="0 0 1024 1024"'));
  assert.ok(taskTypeBadgeIcon.includes("M597.358 218.697a47.982 47.982 0 0 1 0-96.036"));
  assert.ok(workflowNode.includes("size={24}"));
});
