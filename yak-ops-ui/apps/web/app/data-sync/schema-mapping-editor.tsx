import { Button, Empty, Input, Popover, PopoverContent, PopoverTrigger } from "@yak-ops/yak-ui";
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from "react";

import { DataSyncSearchableSelect } from "@/app/data-sync/searchable-select";
import type { DataSourceCatalogColumn } from "@/service/datasource";
import type {
  DataSyncColumnMapping,
  DataSyncMappingConfig,
  DataSyncMappingPreview,
} from "@/service/data-sync";

interface SchemaMappingEditorProps {
  value?: DataSyncMappingConfig;
  onChange: (value: DataSyncMappingConfig) => void;
  sourceColumns: DataSourceCatalogColumn[];
  targetColumns: DataSourceCatalogColumn[];
  sourceLoading: boolean;
  targetLoading: boolean;
  sourceReady: boolean;
  targetReady: boolean;
  targetDerived: boolean;
  preview?: DataSyncMappingPreview;
}

interface MappingGeometry {
  key: string;
  startX: number;
  startY: number;
  endX: number;
  endY: number;
  middleX: number;
  middleY: number;
}

interface FieldNodeGeometry {
  key: string;
  field: string;
  fieldKey: string;
  role: "source" | "target";
  x: number;
  y: number;
}

interface DragState {
  pointerId: number;
  source: string;
  startX: number;
  startY: number;
  currentX: number;
  currentY: number;
}

const normalizeFieldName = (value: string) => value.trim().toLocaleLowerCase();

const mappingKey = (mapping: DataSyncColumnMapping) =>
  `${normalizeFieldName(mapping.source)}::${normalizeFieldName(mapping.target)}`;

const columnType = (column?: DataSourceCatalogColumn) => {
  if (!column?.typeName) return "-";
  if (
    column.size &&
    column.size > 0 &&
    ["VARCHAR", "CHAR", "NVARCHAR", "NCHAR", "VARBINARY", "BINARY"].some((type) =>
      column.typeName?.toLocaleUpperCase().includes(type),
    )
  ) {
    return `${column.typeName}(${column.size})`;
  }
  return column.typeName;
};

const filterColumns = (columns: DataSourceCatalogColumn[], keyword: string) => {
  const normalized = keyword.trim().toLocaleLowerCase();
  if (!normalized) return columns;

  return columns.filter((column) =>
    [column.name, column.typeName, column.remarks]
      .filter(Boolean)
      .join(" ")
      .toLocaleLowerCase()
      .includes(normalized),
  );
};

const buildSameNameMappings = (
  sourceColumns: DataSourceCatalogColumn[],
  targetColumns: DataSourceCatalogColumn[],
): DataSyncColumnMapping[] => {
  const targets = new Map(
    targetColumns.map((column) => [normalizeFieldName(column.name), column.name]),
  );

  return sourceColumns.flatMap((column) => {
    const target = targets.get(normalizeFieldName(column.name));
    return target ? [{ source: column.name, target }] : [];
  });
};

const buildPositionMappings = (
  sourceColumns: DataSourceCatalogColumn[],
  targetColumns: DataSourceCatalogColumn[],
): DataSyncColumnMapping[] =>
  Array.from({ length: Math.min(sourceColumns.length, targetColumns.length) }, (_, index) => ({
    source: sourceColumns[index].name,
    target: targetColumns[index].name,
  }));

export function SchemaMappingEditor({
  value,
  onChange,
  sourceColumns,
  targetColumns,
  sourceLoading,
  targetLoading,
  sourceReady,
  targetReady,
  targetDerived,
  preview,
}: SchemaMappingEditorProps) {
  const canvasRef = useRef<HTMLDivElement>(null);
  const sourceRefs = useRef(new Map<string, HTMLButtonElement>());
  const targetRefs = useRef(new Map<string, HTMLButtonElement>());
  const hoverClearTimerRef = useRef<number>();

  const [sourceKeyword, setSourceKeyword] = useState("");
  const [targetKeyword, setTargetKeyword] = useState("");
  const [selectedSource, setSelectedSource] = useState<string>();
  const [hoveredMapping, setHoveredMapping] = useState<string>();
  const [hoveredNode, setHoveredNode] = useState<string>();
  const [geometries, setGeometries] = useState<MappingGeometry[]>([]);
  const [fieldNodes, setFieldNodes] = useState<FieldNodeGeometry[]>([]);
  const [drag, setDrag] = useState<DragState>();
  const [addOpen, setAddOpen] = useState(false);
  const [addSource, setAddSource] = useState<string | null>(null);
  const [addTarget, setAddTarget] = useState<string | null>(null);
  const [editingMappingKey, setEditingMappingKey] = useState<string>();

  const sourceMap = useMemo(
    () => new Map(sourceColumns.map((column) => [normalizeFieldName(column.name), column])),
    [sourceColumns],
  );

  const baseTargetColumns = useMemo(
    () => (targetDerived ? sourceColumns : targetColumns),
    [sourceColumns, targetColumns, targetDerived],
  );

  const implicitMappings = useMemo(() => {
    const targets = new Map(
      baseTargetColumns.map((column) => [normalizeFieldName(column.name), column.name]),
    );
    return sourceColumns.map((column) => ({
      source: column.name,
      target: targets.get(normalizeFieldName(column.name)) || column.name,
    }));
  }, [baseTargetColumns, sourceColumns]);

  const mappings = value?.columns ?? implicitMappings;

  const displayTargetColumns = useMemo(() => {
    const result = [...baseTargetColumns];
    const existing = new Set(result.map((column) => normalizeFieldName(column.name)));

    mappings.forEach((mapping) => {
      const normalizedTarget = normalizeFieldName(mapping.target);
      if (existing.has(normalizedTarget)) return;

      existing.add(normalizedTarget);
      const sourceColumn = sourceMap.get(normalizeFieldName(mapping.source));
      result.push({
        name: mapping.target,
        typeName: sourceColumn?.typeName,
        jdbcType: sourceColumn?.jdbcType,
        size: sourceColumn?.size,
        scale: sourceColumn?.scale,
        nullable: sourceColumn?.nullable,
        primaryKey: sourceColumn?.primaryKey,
        remarks: sourceColumn?.remarks,
      });
    });

    return result;
  }, [baseTargetColumns, mappings, sourceMap]);

  const selectableTargetMap = useMemo(
    () => new Map(baseTargetColumns.map((column) => [normalizeFieldName(column.name), column])),
    [baseTargetColumns],
  );

  const visibleSources = useMemo(
    () => filterColumns(sourceColumns, sourceKeyword),
    [sourceColumns, sourceKeyword],
  );
  const visibleTargets = useMemo(
    () => filterColumns(displayTargetColumns, targetKeyword),
    [displayTargetColumns, targetKeyword],
  );

  const mappingReady =
    sourceColumns.length > 0 && (targetDerived || displayTargetColumns.length > 0);
  const loading = sourceLoading || targetLoading;
  const showSearch = sourceColumns.length > 12 || displayTargetColumns.length > 12;

  const commitMappings = useCallback(
    (columns: DataSyncColumnMapping[]) => onChange({ columns }),
    [onChange],
  );

  const connectFields = useCallback(
    (source: string, target: string, replacingKey?: string) => {
      const sourceColumn = sourceMap.get(normalizeFieldName(source));
      const normalizedTarget = target.trim();
      if (
        !sourceColumn ||
        !normalizedTarget ||
        (!targetDerived && !selectableTargetMap.has(normalizeFieldName(normalizedTarget)))
      ) {
        return;
      }

      const sourceKey = normalizeFieldName(sourceColumn.name);
      const targetKey = normalizeFieldName(normalizedTarget);
      const next = mappings.filter(
        (item) =>
          mappingKey(item) !== replacingKey &&
          normalizeFieldName(item.source) !== sourceKey &&
          normalizeFieldName(item.target) !== targetKey,
      );

      next.push({ source: sourceColumn.name, target: normalizedTarget });
      commitMappings(next);
      setSelectedSource(undefined);
    },
    [commitMappings, mappings, selectableTargetMap, sourceMap, targetDerived],
  );

  const removeMapping = useCallback(
    (key: string) => {
      commitMappings(mappings.filter((item) => mappingKey(item) !== key));
      setHoveredMapping(undefined);
    },
    [commitMappings, mappings],
  );

  const calculateGeometry = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      setGeometries([]);
      setFieldNodes([]);
      return;
    }

    const canvasRect = canvas.getBoundingClientRect();
    const next = mappings.flatMap((mapping) => {
      const sourceElement = sourceRefs.current.get(normalizeFieldName(mapping.source));
      const targetElement = targetRefs.current.get(normalizeFieldName(mapping.target));
      if (!sourceElement || !targetElement) return [];

      const sourceRect = sourceElement.getBoundingClientRect();
      const targetRect = targetElement.getBoundingClientRect();
      const startX = sourceRect.right - canvasRect.left;
      const startY = sourceRect.top - canvasRect.top + sourceRect.height / 2;
      const endX = targetRect.left - canvasRect.left;
      const endY = targetRect.top - canvasRect.top + targetRect.height / 2;

      return [
        {
          key: mappingKey(mapping),
          startX,
          startY,
          endX,
          endY,
          middleX: (startX + endX) / 2,
          middleY: (startY + endY) / 2,
        },
      ];
    });

    const nodes: FieldNodeGeometry[] = [];
    visibleSources.forEach((column) => {
      const fieldKey = normalizeFieldName(column.name);
      const element = sourceRefs.current.get(fieldKey);
      if (!element) return;
      const rect = element.getBoundingClientRect();
      nodes.push({
        key: `source::${fieldKey}`,
        field: column.name,
        fieldKey,
        role: "source",
        x: rect.right - canvasRect.left,
        y: rect.top - canvasRect.top + rect.height / 2,
      });
    });
    visibleTargets.forEach((column) => {
      const fieldKey = normalizeFieldName(column.name);
      const element = targetRefs.current.get(fieldKey);
      if (!element) return;
      const rect = element.getBoundingClientRect();
      nodes.push({
        key: `target::${fieldKey}`,
        field: column.name,
        fieldKey,
        role: "target",
        x: rect.left - canvasRect.left,
        y: rect.top - canvasRect.top + rect.height / 2,
      });
    });

    setGeometries(next);
    setFieldNodes(nodes);
  }, [mappings, visibleSources, visibleTargets]);

  useLayoutEffect(() => {
    calculateGeometry();
  }, [calculateGeometry, visibleSources, visibleTargets]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;

    const observer = new ResizeObserver(calculateGeometry);
    observer.observe(canvas);
    window.addEventListener("resize", calculateGeometry);

    return () => {
      observer.disconnect();
      window.removeEventListener("resize", calculateGeometry);
    };
  }, [calculateGeometry]);

  const beginDrag = (
    pointerId: number,
    source: string,
    startX: number,
    startY: number,
    currentX: number,
    currentY: number,
  ) => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    canvas.setPointerCapture(pointerId);
    setSelectedSource(source);
    setDrag({
      pointerId,
      source,
      startX,
      startY,
      currentX,
      currentY,
    });
  };

  const startDrag = (event: ReactPointerEvent<HTMLButtonElement>, source: string) => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const canvasRect = canvas.getBoundingClientRect();
    const sourceRect = event.currentTarget.getBoundingClientRect();
    beginDrag(
      event.pointerId,
      source,
      sourceRect.right - canvasRect.left,
      sourceRect.top - canvasRect.top + sourceRect.height / 2,
      event.clientX - canvasRect.left,
      event.clientY - canvasRect.top,
    );
  };

  const startNodeDrag = (event: ReactPointerEvent<SVGCircleElement>, node: FieldNodeGeometry) => {
    const canvas = canvasRef.current;
    if (!canvas || node.role !== "source") return;

    event.stopPropagation();
    const canvasRect = canvas.getBoundingClientRect();
    beginDrag(
      event.pointerId,
      node.field,
      node.x,
      node.y,
      event.clientX - canvasRect.left,
      event.clientY - canvasRect.top,
    );
  };

  const moveDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!drag || drag.pointerId !== event.pointerId) return;
    const canvasRect = event.currentTarget.getBoundingClientRect();
    setDrag({
      ...drag,
      currentX: event.clientX - canvasRect.left,
      currentY: event.clientY - canvasRect.top,
    });
  };

  const finishDrag = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!drag || drag.pointerId !== event.pointerId) return;

    const target = (
      document.elementFromPoint(event.clientX, event.clientY) as HTMLElement | null
    )?.closest<HTMLElement>("[data-target-field]")?.dataset.targetField;

    if (target) connectFields(drag.source, target);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    setDrag(undefined);
  };

  const sourceOptions = useMemo(
    () =>
      sourceColumns.map((column) => ({
        value: column.name,
        label: column.name,
        searchText: [column.typeName, column.remarks].filter(Boolean).join(" "),
      })),
    [sourceColumns],
  );
  const targetOptions = useMemo(
    () =>
      displayTargetColumns.map((column) => ({
        value: column.name,
        label: column.name,
        searchText: [column.typeName, column.remarks].filter(Boolean).join(" "),
      })),
    [displayTargetColumns],
  );

  const previewByMapping = useMemo(
    () =>
      new Map(
        (preview?.mappings || []).map((item) => [
          `${normalizeFieldName(item.sourceName)}::${normalizeFieldName(item.targetName || "")}`,
          item,
        ]),
      ),
    [preview?.mappings],
  );

  const mappingBySource = useMemo(
    () => new Map(mappings.map((item) => [normalizeFieldName(item.source), item])),
    [mappings],
  );
  const mappingByTarget = useMemo(
    () => new Map(mappings.map((item) => [normalizeFieldName(item.target), item])),
    [mappings],
  );

  const clearHoverTimer = useCallback(() => {
    if (hoverClearTimerRef.current !== undefined) {
      window.clearTimeout(hoverClearTimerRef.current);
      hoverClearTimerRef.current = undefined;
    }
  }, []);

  const showMappingActions = useCallback(
    (key: string) => {
      clearHoverTimer();
      setHoveredMapping(key);
    },
    [clearHoverTimer],
  );

  const hideMappingActions = useCallback(() => {
    clearHoverTimer();
    hoverClearTimerRef.current = window.setTimeout(() => {
      setHoveredMapping(undefined);
      hoverClearTimerRef.current = undefined;
    }, 80);
  }, [clearHoverTimer]);

  useEffect(() => clearHoverTimer, [clearHoverTimer]);

  const renderField = (column: DataSourceCatalogColumn, role: "source" | "target") => {
    const field = column.name;
    const fieldKey = normalizeFieldName(field);
    const selected = role === "source" && selectedSource === field;

    return (
      <button
        key={field}
        ref={(element) => {
          const refs = role === "source" ? sourceRefs.current : targetRefs.current;
          if (element) refs.set(fieldKey, element);
          else refs.delete(fieldKey);
        }}
        type="button"
        data-target-field={role === "target" ? field : undefined}
        className={[
          "relative grid h-8 w-full touch-none cursor-pointer grid-cols-[minmax(0,1fr)_140px] items-center border-b border-[#eef0f3] bg-white text-left transition-colors last:border-b-0",
          selected
            ? "bg-[#f5f8ff]"
            : selectedSource && role === "target"
              ? "hover:bg-[#f5f8ff]"
              : "hover:bg-[#f8f9fb]",
        ].join(" ")}
        onClick={() => {
          if (role === "source") setSelectedSource(field);
          else if (selectedSource) connectFields(selectedSource, field);
        }}
        onPointerDown={role === "source" ? (event) => startDrag(event, field) : undefined}
      >
        <span className="flex min-w-0 items-center gap-1.5 px-3">
          <span className="truncate text-xs text-[#344054]">{field}</span>
          {column.primaryKey ? (
            <span className="shrink-0 rounded bg-[#f2f4f7] px-1 py-0.5 text-[9px] font-medium text-[#667085]">
              PK
            </span>
          ) : null}
        </span>
        <span className="truncate border-l border-[#eef0f3] px-3 text-xs text-[#475467]">
          {columnType(column)}
        </span>
      </button>
    );
  };

  const closeMappingEditor = () => {
    setAddOpen(false);
    setAddSource(null);
    setAddTarget(null);
    setEditingMappingKey(undefined);
  };

  const openMappingEditor = (key?: string) => {
    const mapping = key ? mappings.find((item) => mappingKey(item) === key) : undefined;
    setEditingMappingKey(key);
    setAddSource(mapping?.source || null);
    setAddTarget(mapping?.target || null);
    setAddOpen(true);
  };

  const saveMapping = () => {
    if (!addSource || !addTarget?.trim()) return;
    connectFields(addSource, addTarget, editingMappingKey);
    closeMappingEditor();
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Button
          size="small"
          variant="primary"
          disabled={!mappingReady}
          onClick={() => commitMappings(buildSameNameMappings(sourceColumns, baseTargetColumns))}
        >
          同名映射
        </Button>
        <Button
          size="small"
          disabled={!mappingReady}
          onClick={() => commitMappings(buildPositionMappings(sourceColumns, baseTargetColumns))}
        >
          同行映射
        </Button>
        <Button size="small" disabled={mappings.length === 0} onClick={() => commitMappings([])}>
          清空映射
        </Button>

        <Popover
          open={addOpen}
          onOpenChange={(open) => {
            if (open) openMappingEditor();
            else closeMappingEditor();
          }}
        >
          <PopoverTrigger
            disabled={!mappingReady}
            className="inline-flex h-7 cursor-pointer items-center justify-center rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-button-secondary-border)] bg-[var(--yak-components-button-secondary-bg)] px-2.5 text-[length:var(--yak-font-size-control-small)] font-medium text-[var(--yak-components-button-secondary-text)] outline-none transition-colors hover:bg-[var(--yak-components-button-secondary-bg-hover)] focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-button-focus-ring)] disabled:cursor-not-allowed disabled:opacity-45"
          >
            手动编辑映射关系
          </PopoverTrigger>
          <PopoverContent align="start" className="w-[320px] space-y-3">
            <DataSyncSearchableSelect
              value={addSource}
              options={sourceOptions}
              placeholder="选择来源字段"
              searchPlaceholder="搜索来源字段"
              emptyText="暂无来源字段"
              onValueChange={setAddSource}
            />
            {targetDerived ? (
              <Input
                size="small"
                variant="outlined"
                value={addTarget || ""}
                placeholder="输入目标字段名"
                onChange={(event) => setAddTarget(event.target.value)}
              />
            ) : (
              <DataSyncSearchableSelect
                value={addTarget}
                options={targetOptions}
                placeholder="选择目标字段"
                searchPlaceholder="搜索目标字段"
                emptyText="暂无目标字段"
                onValueChange={setAddTarget}
              />
            )}
            <div className="flex justify-end gap-2">
              <Button size="small" onClick={closeMappingEditor}>
                取消
              </Button>
              <Button
                size="small"
                variant="primary"
                disabled={!addSource || !addTarget?.trim()}
                onClick={saveMapping}
              >
                {editingMappingKey ? "修改" : "添加"}
              </Button>
            </div>
          </PopoverContent>
        </Popover>
      </div>

      {!sourceReady || !targetReady ? (
        <div className="rounded-lg border border-[#e6e8eb] bg-white">
          <Empty description="请先完成来源表和目标表配置" />
        </div>
      ) : loading ? (
        <div className="flex min-h-64 items-center justify-center rounded-lg border border-[#e6e8eb] bg-white text-xs text-[#98a2b3]">
          正在加载字段...
        </div>
      ) : !mappingReady ? (
        <div className="rounded-lg border border-[#e6e8eb] bg-white">
          <Empty description="当前表暂无可映射字段" />
        </div>
      ) : (
        <div className="max-h-[400px] overflow-y-auto overflow-x-hidden [scrollbar-gutter:stable]">
          <div
            ref={canvasRef}
            className="relative grid min-h-[320px] grid-cols-[minmax(280px,1fr)_minmax(160px,.65fr)_minmax(280px,1fr)] bg-white max-xl:grid-cols-[minmax(240px,1fr)_140px_minmax(240px,1fr)]"
            onPointerMove={moveDrag}
            onPointerUp={finishDrag}
            onPointerCancel={() => setDrag(undefined)}
          >
            <svg className="pointer-events-none absolute inset-0 z-30 h-full w-full overflow-visible">
              {geometries.map((geometry) => {
                const active = hoveredMapping === geometry.key;
                const detail = previewByMapping.get(geometry.key);
                const stroke =
                  detail && !detail.compatible
                    ? "#d92d20"
                    : active
                      ? "var(--yak-color-primary)"
                      : "#cfd4dc";

                return (
                  <g key={geometry.key}>
                    <line
                      x1={geometry.startX}
                      y1={geometry.startY}
                      x2={geometry.endX}
                      y2={geometry.endY}
                      stroke="transparent"
                      strokeWidth={14}
                      className="pointer-events-auto cursor-pointer"
                      onMouseEnter={() => showMappingActions(geometry.key)}
                      onMouseLeave={hideMappingActions}
                    />
                    <line
                      x1={geometry.startX}
                      y1={geometry.startY}
                      x2={geometry.endX}
                      y2={geometry.endY}
                      stroke={stroke}
                      strokeWidth={active ? 2 : 1.2}
                    />
                  </g>
                );
              })}

              {fieldNodes.map((node) => {
                const mapping =
                  node.role === "source"
                    ? mappingBySource.get(node.fieldKey)
                    : mappingByTarget.get(node.fieldKey);
                const key = mapping ? mappingKey(mapping) : undefined;
                const detail = key ? previewByMapping.get(key) : undefined;
                const nodeHovered = hoveredNode === node.key;
                const selected =
                  node.role === "source" &&
                  selectedSource &&
                  normalizeFieldName(selectedSource) === node.fieldKey;
                const active = key === hoveredMapping || selected;
                const fill =
                  detail && !detail.compatible
                    ? "#d92d20"
                    : active
                      ? "var(--yak-color-primary)"
                      : "#cfd4dc";

                return (
                  <g key={node.key}>
                    <rect
                      x={node.x - 4}
                      y={node.y - 4}
                      width={8}
                      height={8}
                      rx={0.75}
                      fill={fill}
                      stroke="#ffffff"
                      strokeWidth={1}
                      transform={`rotate(45 ${node.x} ${node.y})`}
                    />

                    {nodeHovered ? (
                      <g pointerEvents="none">
                        <circle
                          cx={node.x}
                          cy={node.y}
                          r={9}
                          fill="#ffffff"
                          stroke="#dfe3e8"
                          strokeWidth={1}
                        />
                        <line
                          x1={node.x - 3.5}
                          y1={node.y}
                          x2={node.x + 3.5}
                          y2={node.y}
                          stroke="var(--yak-color-primary)"
                          strokeWidth={1.5}
                          strokeLinecap="round"
                        />
                        <line
                          x1={node.x}
                          y1={node.y - 3.5}
                          x2={node.x}
                          y2={node.y + 3.5}
                          stroke="var(--yak-color-primary)"
                          strokeWidth={1.5}
                          strokeLinecap="round"
                        />
                      </g>
                    ) : null}

                    <circle
                      cx={node.x}
                      cy={node.y}
                      r={10}
                      fill="transparent"
                      className="pointer-events-auto cursor-pointer"
                      data-target-field={node.role === "target" ? node.field : undefined}
                      onMouseEnter={() => setHoveredNode(node.key)}
                      onMouseLeave={() => setHoveredNode(undefined)}
                      onPointerDown={
                        node.role === "source" ? (event) => startNodeDrag(event, node) : undefined
                      }
                      onClick={(event) => {
                        event.stopPropagation();
                        if (node.role === "source") {
                          setSelectedSource(node.field);
                        } else if (selectedSource) {
                          connectFields(selectedSource, node.field);
                        }
                      }}
                    />
                  </g>
                );
              })}

              {drag ? (
                <line
                  x1={drag.startX}
                  y1={drag.startY}
                  x2={drag.currentX}
                  y2={drag.currentY}
                  stroke="var(--yak-color-primary)"
                  strokeWidth={2}
                  strokeDasharray="5 4"
                />
              ) : null}
            </svg>

            {geometries.map((geometry) =>
              hoveredMapping === geometry.key ? (
                <div
                  key={geometry.key}
                  className="absolute z-40 flex -translate-x-1/2 -translate-y-1/2 items-center gap-1 rounded-md bg-white p-0.5 shadow-sm"
                  style={{ left: geometry.middleX, top: geometry.middleY }}
                  onMouseEnter={() => showMappingActions(geometry.key)}
                  onMouseLeave={hideMappingActions}
                >
                  <Button
                    size="small"
                    variant="danger"
                    className="!h-6 !px-2 !text-[11px]"
                    onClick={() => removeMapping(geometry.key)}
                  >
                    删除
                  </Button>
                  <Button
                    size="small"
                    className="!h-6 !px-2 !text-[11px]"
                    onClick={() => openMappingEditor(geometry.key)}
                  >
                    修改
                  </Button>
                </div>
              ) : null,
            )}

            <div className="relative z-20 self-start border-x border-b border-[#dfe3e8] bg-white">
              <div className="sticky top-0 z-30 bg-white">
                <div className="grid h-8 grid-cols-[minmax(0,1fr)_140px] items-center border-y border-[#dfe3e8] bg-[#f4f5f7] text-xs font-semibold text-[#242731]">
                  <div className="px-3">来源字段</div>
                  <div className="border-l border-[#dfe3e8] px-3">类型</div>
                </div>
                {showSearch ? (
                  <div className="border-b border-[#eef0f3] bg-white p-2">
                    <Input
                      size="small"
                      variant="outlined"
                      value={sourceKeyword}
                      placeholder="搜索来源字段"
                      onChange={(event) => setSourceKeyword(event.target.value)}
                    />
                  </div>
                ) : null}
              </div>
              <div>
                {visibleSources.length > 0 ? (
                  visibleSources.map((column) => renderField(column, "source"))
                ) : (
                  <Empty className="min-h-32" description="没有匹配字段" />
                )}
              </div>
            </div>

            <div className="relative z-0 min-h-[320px] bg-white" />

            <div className="relative z-20 self-start border-x border-b border-[#dfe3e8] bg-white">
              <div className="sticky top-0 z-30 bg-white">
                <div className="grid h-8 grid-cols-[minmax(0,1fr)_140px] items-center border-y border-[#dfe3e8] bg-[#f4f5f7] text-xs font-semibold text-[#242731]">
                  <div className="px-3">目标字段</div>
                  <div className="border-l border-[#dfe3e8] px-3">类型</div>
                </div>
                {showSearch ? (
                  <div className="border-b border-[#eef0f3] bg-white p-2">
                    <Input
                      size="small"
                      variant="outlined"
                      value={targetKeyword}
                      placeholder="搜索目标字段"
                      onChange={(event) => setTargetKeyword(event.target.value)}
                    />
                  </div>
                ) : null}
              </div>
              <div>
                {visibleTargets.length > 0 ? (
                  visibleTargets.map((column) => renderField(column, "target"))
                ) : (
                  <Empty className="min-h-32" description="没有匹配字段" />
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default SchemaMappingEditor;
