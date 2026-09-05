import { history, useIntl } from '@umijs/max';
import { Button, Drawer, Empty, Segmented, Tooltip } from 'antd';
import { ArrowUpRight, Expand, Focus, GitBranch, LoaderCircle, RefreshCw, Shrink } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import ReactFlow, {
  Background,
  BaseEdge,
  Controls,
  type Edge,
  type EdgeProps,
  getSmoothStepPath,
  MarkerType,
  type Node,
  type ReactFlowInstance,
} from 'reactflow';
import 'reactflow/dist/style.css';

import { buildLineageView } from '@/pages/data-analysis/lineage/graph-layout';
import LineageNode, { type LineageNodeData } from '@/pages/data-analysis/lineage/LineageNode';
import type { LineageAssetType, LineageGraph } from '@/pages/data-analysis/lineage/types';
import type { DevelopmentId, DevelopmentSqlLineageColumnMapping, DevelopmentSqlLineagePreview } from '../../../types';
import ColumnLineageNode, { type ColumnLineageNodeData, columnHandleId } from './ColumnLineageNode';
import { LineageInteractionContext, useLineageInteraction } from './LineageInteractionContext';

interface Props {
  nodeId: DevelopmentId;
  preview?: DevelopmentSqlLineagePreview;
  loading: boolean;
  onRefresh: () => void;
}
type Mode = 'table' | 'column';
interface FieldSelection {
  table: string;
  column: string;
  mappings: DevelopmentSqlLineageColumnMapping[];
}

interface InteractiveEdgeData {
  defaultLabel?: string;
}
const InteractiveEdge = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
  data,
}: EdgeProps<InteractiveEdgeData>) => {
  const interaction = useLineageInteraction();
  const [path, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });
  const hasHover = Boolean(interaction.hoveredNodeId || interaction.hoveredFieldKey);
  const active = interaction.relatedEdgeIds.has(id) || interaction.relatedMappingIds.has(id);
  return (
    <>
      <BaseEdge
        id={id}
        path={path}
        markerEnd={markerEnd}
        style={{
          stroke: active ? '#f04473' : '#c5cad1',
          strokeWidth: active ? 2.2 : 1.25,
          opacity: hasHover && !active ? 0.16 : 1,
          transition: 'opacity 120ms, stroke 120ms',
        }}
      />
      {data?.defaultLabel ? (
        <text x={labelX} y={labelY} textAnchor="middle" className="fill-[#6941c6] text-[10px]">
          {data.defaultLabel}
        </text>
      ) : null}
    </>
  );
};
const InteractiveLineageNode = (props: Parameters<typeof LineageNode>[0]) => {
  const interaction = useLineageInteraction();
  const dimmed = Boolean(interaction.hoveredNodeId && !interaction.relatedNodeIds.has(props.id));
  return (
    <div style={{ opacity: dimmed ? 0.25 : 1, transition: 'opacity 120ms' }}>
      <LineageNode {...props} />
    </div>
  );
};
const nodeTypes = { lineage: InteractiveLineageNode, columnLineage: ColumnLineageNode };
const edgeTypes = { interactive: InteractiveEdge };
const visibleTypes = new Set<LineageAssetType>(['TABLE', 'SQL_TASK']);
const statusMessageIds = {
  SUCCESS: 'pages.dataDevelopment.editor.lineage.status.success',
  PARTIAL: 'pages.dataDevelopment.editor.lineage.status.partial',
  UNRESOLVED: 'pages.dataDevelopment.editor.lineage.status.unresolved',
  FAILED: 'pages.dataDevelopment.editor.lineage.status.failed',
} as const;
const statusClassName = {
  SUCCESS: 'bg-[#ecfdf3] text-[#027a48]',
  PARTIAL: 'bg-[#fffaeb] text-[#b54708]',
  UNRESOLVED: 'bg-[#f2f4f7] text-[#667085]',
  FAILED: 'bg-[#fef3f2] text-[#b42318]',
} as const;
const Metric = ({ label, value }: { label: string; value: number }) => (
  <span className="inline-flex items-center gap-1 text-[11px] text-[#8a8f99]">
    <span>{label}</span>
    <b className="font-medium tabular-nums text-[#475467]">{value}</b>
  </span>
);
const fieldKey = (table: string, column: string) => `${table}.${column}`;
const nodeIdFor = (role: 'source' | 'target', table: string) => `${role}:${table}`;

/** Dagre-style left-to-right rank layout. Tables in each rank are centered vertically. */
export const layoutColumnTables = (mappings: DevelopmentSqlLineageColumnMapping[]) => {
  const sourceTables = [...new Set(mappings.map((item) => item.sourceTable))].sort();
  const targetTables = [
    ...new Set(mappings.map((item) => item.targetTable).filter((item): item is string => Boolean(item))),
  ].sort();
  const position = (rank: number, index: number, total: number) => ({
    x: rank * 430,
    y: (index - (total - 1) / 2) * 250,
  });
  return {
    sourceTables: sourceTables.map((table, index) => ({ table, position: position(0, index, sourceTables.length) })),
    targetTables: targetTables.map((table, index) => ({ table, position: position(1, index, targetTables.length) })),
  };
};

export default function SqlLineagePreviewPanel({ nodeId, preview, loading, onRefresh }: Props) {
  const intl = useIntl();
  const [mode, setMode] = useState<Mode>('table');
  const [fullscreen, setFullscreen] = useState(false);
  const [hoveredAsset, setHoveredAsset] = useState<string>();
  const [hoveredField, setHoveredField] = useState<{ table: string; column: string }>();
  const [selection, setSelection] = useState<FieldSelection>();
  const [flow, setFlow] = useState<ReactFlowInstance>();
  const graph: LineageGraph | undefined = preview?.graph;
  const view = useMemo(() => (graph ? buildLineageView(graph, 'BOTH', visibleTypes) : undefined), [graph]);

  useEffect(() => {
    if (!fullscreen) return undefined;
    const exitFullscreen = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setFullscreen(false);
    };
    document.addEventListener('keydown', exitFullscreen);
    return () => document.removeEventListener('keydown', exitFullscreen);
  }, [fullscreen]);

  const relatedAssets = useMemo(() => {
    if (!hoveredAsset || !view) return new Set<string>();
    const ids = new Set([hoveredAsset]);
    let changed = true;
    while (changed) {
      changed = false;
      view.relations.forEach((edge) => {
        if (ids.has(edge.sourceAssetId) || ids.has(edge.targetAssetId)) {
          if (!ids.has(edge.sourceAssetId) || !ids.has(edge.targetAssetId)) changed = true;
          ids.add(edge.sourceAssetId);
          ids.add(edge.targetAssetId);
        }
      });
    }
    return ids;
  }, [hoveredAsset, view]);
  const relatedTableEdges = useMemo(
    () =>
      new Set(
        view?.relations
          .filter((edge) => relatedAssets.has(edge.sourceAssetId) && relatedAssets.has(edge.targetAssetId))
          .map((edge) => edge.id) || [],
      ),
    [relatedAssets, view?.relations],
  );

  const tableNodes = useMemo<Array<Node<LineageNodeData>>>(
    () =>
      view?.nodes.map(({ asset, position }) => ({
        id: asset.id,
        type: 'lineage',
        position,
        draggable: true,
        data: { asset, root: asset.id === graph?.root.id },
      })) || [],
    [graph?.root.id, view?.nodes],
  );
  const tableEdges = useMemo<Edge[]>(
    () =>
      view?.relations.map((relation) => ({
        id: relation.id,
        source: relation.sourceAssetId,
        target: relation.targetAssetId,
        type: 'interactive',
        markerEnd: { type: MarkerType.ArrowClosed, width: 15, height: 15, color: '#98a2b3' },
      })) || [],
    [view?.relations],
  );

  const activeMappingIds = useMemo(
    () =>
      new Set(
        (preview?.columnMappings || [])
          .filter(
            (mapping) =>
              !hoveredField ||
              fieldKey(mapping.sourceTable, mapping.sourceColumn) ===
                fieldKey(hoveredField.table, hoveredField.column) ||
              fieldKey(mapping.targetTable || '', mapping.targetColumn) ===
                fieldKey(hoveredField.table, hoveredField.column),
          )
          .map((mapping) => `${mapping.outputOrdinal}:${mapping.sourceOrdinal}`),
      ),
    [hoveredField, preview?.columnMappings],
  );
  const layout = useMemo(() => layoutColumnTables(preview?.columnMappings || []), [preview?.columnMappings]);
  const openField = useCallback(
    (table: string, column: string) => {
      const mappings = (preview?.columnMappings || []).filter(
        (item) =>
          fieldKey(item.sourceTable, item.sourceColumn) === fieldKey(table, column) ||
          fieldKey(item.targetTable || '', item.targetColumn) === fieldKey(table, column),
      );
      setSelection({ table, column, mappings });
    },
    [preview?.columnMappings],
  );
  const hoverField = useCallback(
    (table?: string, column?: string) => setHoveredField(table && column ? { table, column } : undefined),
    [],
  );
  const columnNodes = useMemo<Array<Node<ColumnLineageNodeData>>>(() => {
    const makeNode = (
      role: 'source' | 'target',
      table: string,
      position: { x: number; y: number },
    ): Node<ColumnLineageNodeData> => {
      const mappings = (preview?.columnMappings || []).filter((item) =>
        role === 'source' ? item.sourceTable === table : item.targetTable === table,
      );
      const names = [...new Set(mappings.map((item) => (role === 'source' ? item.sourceColumn : item.targetColumn)))];
      return {
        id: nodeIdFor(role, table),
        type: 'columnLineage',
        position,
        draggable: true,
        data: {
          tableName: table,
          role,
          fields: names.map((name) => ({
            name,
            dataType: mappings.find(
              (item) => (role === 'source' ? item.sourceColumn : item.targetColumn) === name,
            )?.[role === 'source' ? 'sourceDataType' : 'targetDataType'] || undefined,
            transformed: mappings.some(
              (item) =>
                (role === 'source' ? item.sourceColumn : item.targetColumn) === name && item.mappingKind !== 'IDENTITY',
            ),
          })),
          onFieldClick: openField,
          onFieldHover: hoverField,
        },
      };
    };
    return [
      ...layout.sourceTables.map(({ table, position }) => makeNode('source', table, position)),
      ...layout.targetTables.map(({ table, position }) => makeNode('target', table, position)),
    ];
  }, [hoverField, layout, openField, preview?.columnMappings]);
  const columnEdges = useMemo<Edge[]>(
    () =>
      (preview?.columnMappings || [])
        .filter((item) => item.targetTable)
        .map((item) => {
          const id = `${item.outputOrdinal}:${item.sourceOrdinal}`;
          return {
            id,
            source: nodeIdFor('source', item.sourceTable),
            sourceHandle: columnHandleId(item.sourceColumn),
            target: nodeIdFor('target', item.targetTable!),
            targetHandle: columnHandleId(item.targetColumn),
            type: 'interactive',
            data: {
              defaultLabel:
                item.mappingKind === 'IDENTITY'
                  ? undefined
                  : item.mappingKind === 'AGGREGATION'
                    ? 'SUM / COUNT'
                    : 'TRANSFORM',
            },
            markerEnd: { type: MarkerType.ArrowClosed, color: '#b9c0c9', width: 13, height: 13 },
          };
        }),
    [preview?.columnMappings],
  );

  const interaction = useMemo(
    () => ({
      hoveredNodeId: hoveredAsset,
      hoveredFieldKey: hoveredField ? fieldKey(hoveredField.table, hoveredField.column) : undefined,
      relatedNodeIds: relatedAssets,
      relatedEdgeIds: relatedTableEdges,
      relatedMappingIds: hoveredField ? activeMappingIds : new Set<string>(),
    }),
    [activeMappingIds, hoveredAsset, hoveredField, relatedAssets, relatedTableEdges],
  );

  if (loading && !preview) {
    return (
      <div className="flex h-full items-center justify-center bg-[#fbfcfd] text-[12px] text-[#667085]">
        <LoaderCircle size={16} className="mr-2 animate-spin" />
        {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.loading' })}
      </div>
    );
  }
  if (!preview) {
    return (
      <div className="flex h-full items-center justify-center bg-[#fbfcfd]">
        <Empty
          image={
            <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-[9px] bg-[#eef0f3]">
              <GitBranch size={20} />
            </div>
          }
          description={
            <>
              <div className="text-[13px] font-medium">
                {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.emptyTitle' })}
              </div>
              <div className="mt-1 text-[11px] text-[#98a2b3]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.emptyDescription' })}
              </div>
            </>
          }
        >
          <Button size="small" icon={<RefreshCw size={13} />} onClick={onRefresh}>
            {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.parse' })}
          </Button>
        </Empty>
      </div>
    );
  }
  if (preview.status === 'FAILED') {
    return (
      <div className="flex h-full items-center justify-center bg-[#fbfcfd]">
        <Empty
          description={
            <>
              <div className="font-medium text-[#b42318]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.failed' })}
              </div>
              <div className="text-[11px] text-[#8a8f99]">
                {preview.parseError ||
                  intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.checkSyntax' })}
              </div>
            </>
          }
        >
          <Button size="small" onClick={onRefresh}>
            {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.parseAgain' })}
          </Button>
        </Empty>
      </div>
    );
  }

  const isColumn = mode === 'column';
  return (
    <div
      className={`flex min-h-0 flex-col bg-white ${
        fullscreen ? 'fixed inset-0 z-[1000] h-screen border border-[#e4e7ec]' : 'h-full'
      }`}
    >
      <div className="flex h-11 shrink-0 items-center gap-3 border-b border-[#eef0f2] px-3">
        <span className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${statusClassName[preview.status]}`}>
          {intl.formatMessage({ id: statusMessageIds[preview.status] })}
        </span>
        <Segmented
          size="small"
          value={mode}
          options={[
            {
              label: intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.table' }),
              value: 'table',
            },
            {
              label: intl.formatMessage(
                { id: 'pages.dataDevelopment.editor.lineage.column' },
                { count: preview.columnMappingCount },
              ),
              value: 'column',
              disabled: preview.columnMappingCount === 0,
            },
          ]}
          onChange={(value) => {
            setMode(value as Mode);
            setHoveredAsset(undefined);
            setHoveredField(undefined);
          }}
        />
        <Metric
          label={intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.inputTables' })}
          value={preview.inputTableCount}
        />
        <Metric
          label={intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.outputTables' })}
          value={preview.outputTableCount}
        />
        <Metric
          label={intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.unresolved' })}
          value={preview.unresolvedColumnReferenceCount}
        />
        <span className="min-w-0 flex-1 truncate text-[11px] text-[#b54708]">
          {preview.columnParseError
            ? intl.formatMessage(
                { id: 'pages.dataDevelopment.editor.lineage.degraded' },
                { error: preview.columnParseError },
              )
            : ''}
        </span>
        <Tooltip title={intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.focusTip' })}>
          <Button
            type="text"
            size="small"
            icon={<Focus size={13} />}
            onClick={() => flow?.fitView({ padding: 0.25, duration: 350 })}
          >
            {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.focus' })}
          </Button>
        </Tooltip>
        <Button type="text" size="small" loading={loading} icon={<RefreshCw size={13} />} onClick={onRefresh}>
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.parseAgain' })}
        </Button>
        <Tooltip
          title={intl.formatMessage({
            id: fullscreen
              ? 'pages.dataDevelopment.editor.lineage.exitFullscreen'
              : 'pages.dataDevelopment.editor.lineage.fullscreen',
          })}
        >
          <Button
            type="text"
            size="small"
            aria-label={intl.formatMessage({
              id: fullscreen
                ? 'pages.dataDevelopment.editor.lineage.exitFullscreenAria'
                : 'pages.dataDevelopment.editor.lineage.fullscreen',
            })}
            icon={fullscreen ? <Shrink size={13} /> : <Expand size={13} />}
            onClick={() => setFullscreen((value) => !value)}
          />
        </Tooltip>
        <Button
          type="text"
          size="small"
          icon={<ArrowUpRight size={13} />}
          onClick={() =>
            history.push(`/data-analysis/lineage?assetKey=${encodeURIComponent(`sql-task:data-development:${nodeId}`)}`)
          }
        >
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.production' })}
        </Button>
      </div>
      <div className="relative min-h-0 flex-1 bg-[#f8fafc]">
        <LineageInteractionContext.Provider value={interaction}>
          <ReactFlow
            nodes={isColumn ? columnNodes : tableNodes}
            edges={isColumn ? columnEdges : tableEdges}
            nodeTypes={nodeTypes}
            edgeTypes={edgeTypes}
            fitView
            fitViewOptions={{ padding: 0.25, minZoom: 0.45, maxZoom: 1 }}
            minZoom={0.25}
            maxZoom={1.5}
            nodesDraggable
            nodesConnectable={false}
            elementsSelectable
            onInit={setFlow}
            onNodeMouseEnter={(_, node) => !isColumn && setHoveredAsset(node.id)}
            onNodeMouseLeave={() => setHoveredAsset(undefined)}
            proOptions={{ hideAttribution: true }}
          >
            <Background gap={18} size={1} color="#dfe3e8" />
            <Controls showInteractive={false} position="bottom-right" />
          </ReactFlow>
        </LineageInteractionContext.Provider>
      </div>
      <Drawer
        width={430}
        title={intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.fieldDetail' })}
        open={Boolean(selection)}
        onClose={() => setSelection(undefined)}
      >
        {selection ? (
          <div>
            <div className="rounded-lg bg-[#f8fafc] p-3">
              <div className="text-[11px] text-[#667085]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.currentField' })}
              </div>
              <div className="mt-1 font-mono text-[13px] font-semibold text-[#1d2939]">
                {selection.table}.{selection.column}
              </div>
            </div>
            {selection.mappings.map((item) => (
              <div
                key={`${item.outputOrdinal}:${item.sourceOrdinal}`}
                className="mt-3 rounded-lg border border-[#e4e7ec] p-3"
              >
                <div className="flex items-center gap-2 text-[12px]">
                  <code>
                    {item.sourceTable}.{item.sourceColumn}
                  </code>
                  <span className="text-[#98a2b3]">→</span>
                  <code>
                    {item.targetTable ||
                      intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.targetTable' })}.{item.targetColumn}
                  </code>
                </div>
                <div className="mt-2 inline-flex rounded bg-[#f4f0ff] px-2 py-1 text-[10px] font-medium text-[#6941c6]">
                  {item.mappingKind}
                </div>
                <div className="mt-3 text-[11px] text-[#667085]">
                  {intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.transformLogic' })}
                </div>
                <pre className="mt-1 whitespace-pre-wrap rounded bg-[#101828] p-3 text-[11px] leading-5 text-[#eaecf0]">
                  {item.expression ||
                    (item.mappingKind === 'IDENTITY'
                      ? item.sourceColumn
                      : intl.formatMessage({ id: 'pages.dataDevelopment.editor.lineage.noExpression' }))}
                </pre>
              </div>
            ))}
          </div>
        ) : null}
      </Drawer>
    </div>
  );
}
