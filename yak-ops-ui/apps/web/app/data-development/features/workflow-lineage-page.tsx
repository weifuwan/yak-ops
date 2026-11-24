import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import ReactFlow, {
  Background,
  BackgroundVariant,
  Controls,
  type Edge,
  type Node,
} from "reactflow";
import "reactflow/dist/style.css";
import { GitBranch, RefreshCw } from "lucide-react";
import {
  Button,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import { listWorkflowDefinitions } from "@/service/workflow/definition";
import {
  getWorkflowLineage,
  type WorkflowLineageDetail,
  type WorkflowLineageRelation,
} from "@/service/workflow/lineage";
import { OperationsPageShell } from "@/app/operations/components/operations-page-shell";

function statusLabel(value?: string | null) {
  if (value === "ONLINE") return "已上线";
  if (value === "OFFLINE") return "未上线";
  return value || "未知状态";
}

export function WorkflowLineagePage() {
  const [workflowCode, setWorkflowCode] = useState("");
  const definitionsQuery = useQuery({
    queryKey: ["data-development", "lineage", "definitions"],
    queryFn: ({ signal }) => listWorkflowDefinitions({ pageNo: 1, pageSize: 200 }, signal),
  });
  const definitions = definitionsQuery.data?.totalList ?? [];

  useEffect(() => {
    if (!workflowCode && definitions.length) {
      setWorkflowCode(String(definitions[0].code));
    }
  }, [definitions, workflowCode]);

  const selectedCode = Number(workflowCode);
  const lineageQuery = useQuery({
    queryKey: ["data-development", "lineage", selectedCode],
    queryFn: ({ signal }) => getWorkflowLineage(selectedCode, signal),
    enabled: Number.isSafeInteger(selectedCode) && selectedCode > 0,
  });

  const lineage = lineageQuery.data;
  const details = lineage?.workFlowRelationDetailList ?? [];
  const relations = lineage?.workFlowRelationList ?? [];
  const detailByCode = useMemo(
    () => new Map<number, WorkflowLineageDetail>(details.map((item) => [item.workFlowCode, item])),
    [details],
  );

  const graph = useMemo(() => {
    if (!selectedCode || !lineage) return { nodes: [] as Node[], edges: [] as Edge[] };
    const upstream = Array.from(new Set(
      relations
        .filter((relation: WorkflowLineageRelation) => relation.targetWorkFlowCode === selectedCode && relation.sourceWorkFlowCode > 0)
        .map((relation) => relation.sourceWorkFlowCode),
    ));
    const downstream = Array.from(new Set(
      relations
        .filter((relation: WorkflowLineageRelation) => relation.sourceWorkFlowCode === selectedCode && relation.targetWorkFlowCode > 0)
        .map((relation) => relation.targetWorkFlowCode),
    ));
    const relatedCodes = Array.from(new Set([selectedCode, ...upstream, ...downstream]));
    const nodes: Node[] = relatedCodes.map((code) => {
      const detail = detailByCode.get(code);
      const isRoot = code === selectedCode;
      const isUpstream = upstream.includes(code);
      const x = isRoot ? 430 : isUpstream ? 60 : 820;
      const group = isRoot ? 0 : isUpstream ? upstream.indexOf(code) : downstream.indexOf(code);
      const y = 80 + Math.max(0, group) * 150;
      return {
        id: String(code),
        position: { x, y },
        draggable: false,
        selectable: true,
        data: {
          label: (
            <div className="w-[190px] px-1 py-1">
              <div className="truncate text-xs font-semibold">{detail?.workFlowName || (isRoot ? definitions.find((item) => item.code === code)?.name : `工作流 #${code}`)}</div>
              <div className="mt-1 text-[10px] text-[#858d99]">#{code} · {statusLabel(detail?.workFlowPublishStatus)}</div>
              {detail?.crontab ? <div className="mt-1 truncate text-[10px] text-[#858d99]">Cron：{detail.crontab}</div> : null}
            </div>
          ),
        },
        style: {
          width: 220,
          border: isRoot ? "1px solid #9bb3ff" : "1px solid #e3e7ee",
          borderRadius: 8,
          background: isRoot ? "#f0f4ff" : "#ffffff",
          color: "#30343a",
          boxShadow: "0 2px 8px rgba(20, 30, 50, 0.05)",
          padding: 8,
        },
      };
    });
    const edges: Edge[] = relations
      .filter((relation) => relatedCodes.includes(relation.sourceWorkFlowCode) && relatedCodes.includes(relation.targetWorkFlowCode))
      .filter((relation) => relation.sourceWorkFlowCode > 0 && relation.targetWorkFlowCode > 0)
      .map((relation, index) => ({
        id: `${relation.sourceWorkFlowCode}-${relation.targetWorkFlowCode}-${index}`,
        source: String(relation.sourceWorkFlowCode),
        target: String(relation.targetWorkFlowCode),
        type: "smoothstep",
        animated: false,
        style: { stroke: "#aab4c3", strokeWidth: 1.5 },
      }));
    return { nodes, edges };
  }, [selectedCode, lineage, relations, detailByCode, definitions]);

  const selectedName =
    definitions.find((item) => item.code === selectedCode)?.name ||
    detailByCode.get(selectedCode)?.workFlowName ||
    "选择工作流";

  return (
    <OperationsPageShell
      title="工作流依赖分析"
      extra={
        <Button size="sm" variant="secondary" onClick={() => void lineageQuery.refetch()} isDisabled={!selectedCode || lineageQuery.isFetching}>
          <RefreshCw size={13} />
          刷新依赖
        </Button>
      }
    >
      <div className="flex min-h-0 flex-1 flex-col gap-3">
        <div className="flex flex-wrap items-center gap-3 rounded-md border border-[#e9ebef] bg-white px-3 py-2.5">
          <GitBranch size={16} className="text-[#697586]" />
          <span className="text-xs font-medium text-[#4b5563]">分析工作流</span>
          <div className="w-[320px] max-w-full">
            <Select
              size="small"
              items={Object.fromEntries(definitions.map((item) => [String(item.code), item.name]))}
              value={workflowCode}
              onValueChange={(value) => setWorkflowCode(String(value ?? ""))}
            >
              <SelectTrigger variant="outlined"><SelectValue placeholder="选择工作流" /></SelectTrigger>
              <SelectContent>
                {definitions.map((item) => (
                  <SelectItem key={item.code} value={String(item.code)}>
                    <SelectItemText>{item.name}</SelectItemText><SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <span className="text-[11px] text-[#8a9099]">
            {selectedCode ? `工作流 #${selectedCode} · ${selectedName}` : "请选择需要分析的工作流"}
          </span>
        </div>

        <div className="grid min-h-0 flex-1 grid-cols-[minmax(0,1fr)_220px] gap-3 max-lg:grid-cols-1">
          <div className="relative min-h-[420px] overflow-hidden rounded-md border border-[#e9ebef] bg-[#fafbfc]">
            {lineageQuery.isPending ? (
              <div className="absolute inset-0 flex items-center justify-center text-xs text-[#8b919b]">正在分析依赖关系...</div>
            ) : lineageQuery.isError ? (
              <div className="absolute inset-0 flex items-center justify-center text-xs text-[#c2413b]">依赖关系加载失败，请重试</div>
            ) : graph.nodes.length <= 1 && graph.edges.length === 0 ? (
              <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-xs text-[#8b919b]">
                <GitBranch size={24} />
                当前工作流没有已记录的上下游依赖
              </div>
            ) : (
              <ReactFlow
                nodes={graph.nodes}
                edges={graph.edges}
                fitView
                fitViewOptions={{ padding: 0.25 }}
                nodesDraggable={false}
                nodesConnectable={false}
                elementsSelectable
                proOptions={{ hideAttribution: true }}
              >
                <Background variant={BackgroundVariant.Dots} gap={18} size={1} color="#dfe4ec" />
                <Controls showInteractive={false} />
              </ReactFlow>
            )}
          </div>

          <aside className="flex flex-col gap-3">
            <div className="rounded-md border border-[#e9ebef] bg-white p-3">
              <div className="text-[11px] text-[#8a9099]">上游工作流</div>
              <div className="mt-1 text-2xl font-semibold tabular-nums text-[#30343a]">
                {relations.filter((relation) => relation.targetWorkFlowCode === selectedCode && relation.sourceWorkFlowCode > 0).length}
              </div>
              <div className="mt-1 text-[11px] text-[#8a9099]">直接依赖当前工作流的上游节点</div>
            </div>
            <div className="rounded-md border border-[#e9ebef] bg-white p-3">
              <div className="text-[11px] text-[#8a9099]">下游工作流</div>
              <div className="mt-1 text-2xl font-semibold tabular-nums text-[#30343a]">
                {relations.filter((relation) => relation.sourceWorkFlowCode === selectedCode && relation.targetWorkFlowCode > 0).length}
              </div>
              <div className="mt-1 text-[11px] text-[#8a9099]">依赖当前工作流的下游节点</div>
            </div>
            <div className="rounded-md border border-[#e9ebef] bg-white p-3">
              <div className="text-[11px] text-[#8a9099]">依赖边数</div>
              <div className="mt-1 text-2xl font-semibold tabular-nums text-[#30343a]">{graph.edges.length}</div>
              <div className="mt-1 text-[11px] text-[#8a9099]">当前图中展示的工作流关系</div>
            </div>
          </aside>
        </div>
      </div>
    </OperationsPageShell>
  );
}
