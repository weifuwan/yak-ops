import { useMemo } from "react";
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
import { Button } from "@yak-ops/yak-ui";

import type { WorkflowDefinitionRecord } from "@/service/workflow/definition";
import {
  getWorkflowLineage,
  type WorkflowLineageDetail,
  type WorkflowLineageRelation,
} from "@/service/workflow/lineage";

function statusLabel(value?: string | null) {
  if (value === "ONLINE") return "已上线";
  if (value === "OFFLINE") return "未上线";
  return value || "未知状态";
}

export function WorkflowLineageEditor({
  workflow,
}: {
  workflow: WorkflowDefinitionRecord;
}) {
  const query = useQuery({
    queryKey: ["data-development", "workflow-lineage-editor", workflow.code],
    queryFn: ({ signal }) => getWorkflowLineage(workflow.code, signal),
  });

  const lineage = query.data;
  const details = lineage?.workFlowRelationDetailList ?? [];
  const relations = lineage?.workFlowRelationList ?? [];
  const detailByCode = useMemo(
    () =>
      new Map<number, WorkflowLineageDetail>(
        details.map((item) => [item.workFlowCode, item]),
      ),
    [details],
  );

  const graph = useMemo(() => {
    if (!lineage) return { nodes: [] as Node[], edges: [] as Edge[] };

    const upstream = Array.from(
      new Set(
        relations
          .filter(
            (relation: WorkflowLineageRelation) =>
              relation.targetWorkFlowCode === workflow.code &&
              relation.sourceWorkFlowCode > 0,
          )
          .map((relation) => relation.sourceWorkFlowCode),
      ),
    );
    const downstream = Array.from(
      new Set(
        relations
          .filter(
            (relation: WorkflowLineageRelation) =>
              relation.sourceWorkFlowCode === workflow.code &&
              relation.targetWorkFlowCode > 0,
          )
          .map((relation) => relation.targetWorkFlowCode),
      ),
    );
    const relatedCodes = Array.from(
      new Set([workflow.code, ...upstream, ...downstream]),
    );

    const nodes: Node[] = relatedCodes.map((code) => {
      const detail = detailByCode.get(code);
      const root = code === workflow.code;
      const upstreamNode = upstream.includes(code);
      const index = root
        ? 0
        : upstreamNode
          ? upstream.indexOf(code)
          : downstream.indexOf(code);

      return {
        id: String(code),
        position: {
          x: root ? 440 : upstreamNode ? 70 : 820,
          y: 80 + Math.max(0, index) * 150,
        },
        draggable: false,
        data: {
          label: (
            <div className="w-[190px] px-1 py-1">
              <div className="truncate text-xs font-semibold">
                {detail?.workFlowName || (root ? workflow.name : `工作流 #${code}`)}
              </div>
              <div className="mt-1 text-[10px] text-[#858d99]">
                #{code} · {statusLabel(detail?.workFlowPublishStatus)}
              </div>
              {detail?.crontab ? (
                <div className="mt-1 truncate text-[10px] text-[#858d99]">
                  Cron：{detail.crontab}
                </div>
              ) : null}
            </div>
          ),
        },
        style: {
          width: 220,
          border: root ? "1px solid #9bb3ff" : "1px solid #e3e7ee",
          borderRadius: 8,
          background: root ? "#f0f4ff" : "#ffffff",
          color: "#30343a",
          boxShadow: "0 2px 8px rgba(20, 30, 50, 0.05)",
          padding: 8,
        },
      };
    });

    const edges: Edge[] = relations
      .filter(
        (relation) =>
          relatedCodes.includes(relation.sourceWorkFlowCode) &&
          relatedCodes.includes(relation.targetWorkFlowCode) &&
          relation.sourceWorkFlowCode > 0 &&
          relation.targetWorkFlowCode > 0,
      )
      .map((relation, index) => ({
        id:
          relation.sourceWorkFlowCode +
          "-" +
          relation.targetWorkFlowCode +
          "-" +
          index,
        source: String(relation.sourceWorkFlowCode),
        target: String(relation.targetWorkFlowCode),
        type: "smoothstep",
        style: { stroke: "#aab4c3", strokeWidth: 1.5 },
      }));

    return { nodes, edges };
  }, [detailByCode, lineage, relations, workflow.code, workflow.name]);

  const upstreamCount = relations.filter(
    (relation) =>
      relation.targetWorkFlowCode === workflow.code &&
      relation.sourceWorkFlowCode > 0,
  ).length;
  const downstreamCount = relations.filter(
    (relation) =>
      relation.sourceWorkFlowCode === workflow.code &&
      relation.targetWorkFlowCode > 0,
  ).length;

  return (
    <div className="flex h-full min-h-0 flex-col bg-[#f7f8fa] p-4">
      <div className="mb-3 flex shrink-0 items-center gap-3 rounded-md border border-[#e6e9ee] bg-white px-4 py-3">
        <GitBranch size={16} className="text-[#697586]" />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-semibold text-[#30343a]">
            {workflow.name}
          </div>
          <div className="mt-0.5 text-[11px] text-[#8a9099]">
            工作流 #{workflow.code} · 上游 {upstreamCount} · 下游 {downstreamCount}
          </div>
        </div>
        <Button
          size="sm"
          variant="secondary"
          onClick={() => void query.refetch()}
          isDisabled={query.isFetching}
        >
          <RefreshCw size={13} className={query.isFetching ? "animate-spin" : ""} />
          刷新
        </Button>
      </div>

      <div className="relative min-h-0 flex-1 overflow-hidden rounded-md border border-[#e6e9ee] bg-[#fafbfc]">
        {query.isPending ? (
          <div className="absolute inset-0 flex items-center justify-center text-xs text-[#8b919b]">
            正在分析依赖关系...
          </div>
        ) : query.isError ? (
          <div className="absolute inset-0 flex items-center justify-center text-xs text-[#c2413b]">
            依赖关系加载失败
          </div>
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
            proOptions={{ hideAttribution: true }}
          >
            <Background
              variant={BackgroundVariant.Dots}
              gap={18}
              size={1}
              color="#dfe4ec"
            />
            <Controls showInteractive={false} />
          </ReactFlow>
        )}
      </div>
    </div>
  );
}
