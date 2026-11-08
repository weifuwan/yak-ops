import {
  Button,
  Empty,
  Input,
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@yak-ops/yak-ui";
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from "react";

import type { DataSourceCatalogColumn } from "@/service/datasource";
import type { DirectFieldMapping } from "@/service/data-sync/field-mapping";
import { DataSyncSearchableSelect } from "../components/searchable-select";

interface Props {
  fields: DirectFieldMapping[];
  sourceColumns: DataSourceCatalogColumn[];
  targetColumns?: DataSourceCatalogColumn[];
  disabled?: boolean;
  onChange: (fields: DirectFieldMapping[]) => void;
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

const mappingKey = (mapping: DirectFieldMapping) =>
  normalizeFieldName(mapping.source) + "::" + normalizeFieldName(mapping.target);

const columnType = (column?: DataSourceCatalogColumn) => {
  if (!column?.typeName) return "-";
  if (
    column.size &&
    column.size > 0 &&
    ["VARCHAR", "CHAR", "NVARCHAR", "NCHAR", "VARBINARY", "BINARY"].some(
      (type) => column.typeName?.toLocaleUpperCase().includes(type),
    )
  ) {
    return column.typeName + "(" + column.size + ")";
  }
  return column.typeName;
};

const filterColumns = (
  columns: DataSourceCatalogColumn[],
  keyword: string,
) => {
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
): DirectFieldMapping[] => {
  const targets = new Map(
    targetColumns.map((column) => [
      normalizeFieldName(column.name),
      column.name,
    ]),
  );

  return sourceColumns.flatMap((column) => {
    const target = targets.get(normalizeFieldName(column.name));
    return target ? [{ source: column.name, target }] : [];
  });
};

const buildPositionMappings = (
  sourceColumns: DataSourceCatalogColumn[],
  targetColumns: DataSourceCatalogColumn[],
): DirectFieldMapping[] =>
  Array.from(
    { length: Math.min(sourceColumns.length, targetColumns.length) },
    (_, index) => ({
      source: sourceColumns[index].name,
      target: targetColumns[index].name,
    }),
  );

export function FieldMappingCanvas({
  fields,
  sourceColumns,
  targetColumns,
  disabled = false,
  onChange,
}: Props) {
  const canvasRef = useRef<HTMLDivElement>(null);
  const sourceRefs = useRef(new Map<string, HTMLButtonElement>());
  const targetRefs = useRef(new Map<string, HTMLButtonElement>());
  const hoverClearTimerRef = useRef<number | undefined>(undefined);

  const [sourceKeyword, setSourceKeyword] = useState("");
  const [targetKeyword, setTargetKeyword] = useState("");
  const [selectedSource, setSelectedSource] = useState<string>();
  const [hoveredMapping, setHoveredMapping] = useState<string>();
  const [hoveredNode, setHoveredNode] = useState<string>();
  const [geometries, setGeometries] = useState<MappingGeometry[]>([]);
  const [fieldNodes, setFieldNodes] = useState<FieldNodeGeometry[]>([]);
  const [drag, setDrag] = useState<DragState>();
  const [editOpen, setEditOpen] = useState(false);
  const [editSource, setEditSource] = useState<string | null>(null);
  const [editTarget, setEditTarget] = useState<string | null>(null);
  const [editingMappingKey, setEditingMappingKey] = useState<string>();

  const targetDerived = targetColumns === undefined;

  const sourceMap = useMemo(
    () =>
      new Map(
        sourceColumns.map((column) => [
          normalizeFieldName(column.name),
          column,
        ]),
      ),
    [sourceColumns],
  );

  const baseTargetColumns = useMemo(
    () => (targetDerived ? sourceColumns : targetColumns),
    [sourceColumns, targetColumns, targetDerived],
  );

  const displayTargetColumns = useMemo(() => {
    const result = [...baseTargetColumns];
    const existing = new Set(
      result.map((column) => normalizeFieldName(column.name)),
    );

    fields.forEach((mapping) => {
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
  }, [baseTargetColumns, fields, sourceMap]);

  const selectableTargetMap = useMemo(
    () =>
      new Map(
        baseTargetColumns.map((column) => [
          normalizeFieldName(column.name),
          column,
        ]),
      ),
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
    sourceColumns.length > 0 &&
    (targetDerived || displayTargetColumns.length > 0);
  const showSearch =
    sourceColumns.length > 12 || displayTargetColumns.length > 12;

  const commitMappings = useCallback(
    (next: DirectFieldMapping[]) => {
      if (next.length) onChange(next);
    },
    [onChange],
  );

  const connectFields = useCallback(
    (source: string, target: string, replacingKey?: string) => {
      const sourceColumn = sourceMap.get(normalizeFieldName(source));
      const normalizedTarget = target.trim();

      if (
        !sourceColumn ||
        !normalizedTarget ||
        (!targetDerived &&
          !selectableTargetMap.has(normalizeFieldName(normalizedTarget)))
      ) {
        return;
      }

      const sourceKey = normalizeFieldName(sourceColumn.name);
      const targetKey = normalizeFieldName(normalizedTarget);
      const next = fields.filter(
        (item) =>
          mappingKey(item) !== replacingKey &&
          normalizeFieldName(item.source) !== sourceKey &&
          normalizeFieldName(item.target) !== targetKey,
      );

      next.push({ source: sourceColumn.name, target: normalizedTarget });
      commitMappings(next);
      setSelectedSource(undefined);
    },
    [
      commitMappings,
      fields,
      selectableTargetMap,
      sourceMap,
      targetDerived,
    ],
  );

  const removeMapping = useCallback(
    (key: string) => {
      const next = fields.filter((item) => mappingKey(item) !== key);
      if (!next.length) return;
      commitMappings(next);
      setHoveredMapping(undefined);
    },
    [commitMappings, fields],
  );

  const calculateGeometry = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      setGeometries([]);
      setFieldNodes([]);
      return;
    }

    const canvasRect = canvas.getBoundingClientRect();
    const next = fields.flatMap((mapping) => {
      const sourceElement = sourceRefs.current.get(
        normalizeFieldName(mapping.source),
      );
      const targetElement = targetRefs.current.get(
        normalizeFieldName(mapping.target),
      );
      if (!sourceElement || !targetElement) return [];

      const sourceRect = sourceElement.getBoundingClientRect();
      const targetRect = targetElement.getBoundingClientRect();
      const startX = sourceRect.right - canvasRect.left;
      const startY =
        sourceRect.top - canvasRect.top + sourceRect.height / 2;
      const endX = targetRect.left - canvasRect.left;
      const endY =
        targetRect.top - canvasRect.top + targetRect.height / 2;

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
        key: "source::" + fieldKey,
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
        key: "target::" + fieldKey,
        field: column.name,
        fieldKey,
        role: "target",
        x: rect.left - canvasRect.left,
        y: rect.top - canvasRect.top + rect.height / 2,
      });
    });

    setGeometries(next);
    setFieldNodes(nodes);
  }, [fields, visibleSources, visibleTargets]);

  useLayoutEffect(() => {
    calculateGeometry();
  }, [calculateGeometry]);

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
    if (!canvas || disabled) return;

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

  const startDrag = (
    event: ReactPointerEvent<HTMLButtonElement>,
    source: string,
  ) => {
    const canvas = canvasRef.current;
    if (!canvas || disabled) return;

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

  const startNodeDrag = (
    event: ReactPointerEvent<SVGCircleElement>,
    node: FieldNodeGeometry,
  ) => {
    const canvas = canvasRef.current;
    if (!canvas || node.role !== "source" || disabled) return;

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
      document.elementFromPoint(
        event.clientX,
        event.clientY,
      ) as HTMLElement | null
    )
      ?.closest<HTMLElement>("[data-target-field]")
      ?.dataset.targetField;

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
        searchText: [column.typeName, column.remarks]
          .filter(Boolean)
          .join(" "),
      })),
    [sourceColumns],
  );

  const targetOptions = useMemo(
    () =>
      displayTargetColumns.map((column) => ({
        value: column.name,
        label: column.name,
        searchText: [column.typeName, column.remarks]
          .filter(Boolean)
          .join(" "),
      })),
    [displayTargetColumns],
  );

  const mappingBySource = useMemo(
    () =>
      new Map(
        fields.map((item) => [
          normalizeFieldName(item.source),
          item,
        ]),
      ),
    [fields],
  );

  const mappingByTarget = useMemo(
    () =>
      new Map(
        fields.map((item) => [
          normalizeFieldName(item.target),
          item,
        ]),
      ),
    [fields],
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

  const renderField = (
    column: DataSourceCatalogColumn,
    role: "source" | "target",
  ) => {
    const field = column.name;
    const fieldKey = normalizeFieldName(field);
    const selected = role === "source" && selectedSource === field;

    return (
      <button
        key={field}
        ref={(element) => {
          const refs =
            role === "source" ? sourceRefs.current : targetRefs.current;
          if (element) refs.set(fieldKey, element);
          else refs.delete(fieldKey);
        }}
        type="button"
        disabled={disabled}
        data-target-field={role === "target" ? field : undefined}
        className={[
          "relative grid h-8 w-full touch-none grid-cols-[minmax(0,1fr)_140px] items-center border-b border-[var(--yak-components-panel-border)] bg-white text-left transition-colors last:border-b-0 disabled:cursor-default",
          selected
            ? "bg-[var(--yak-color-hover)]"
            : selectedSource && role === "target"
              ? "cursor-pointer hover:bg-[var(--yak-color-hover)]"
              : "cursor-pointer hover:bg-[var(--yak-color-hover)]",
        ].join(" ")}
        onClick={() => {
          if (disabled) return;
          if (role === "source") setSelectedSource(field);
          else if (selectedSource) connectFields(selectedSource, field);
        }}
        onPointerDown={
          role === "source"
            ? (event) => startDrag(event, field)
            : undefined
        }
      >
        <span className="flex min-w-0 items-center gap-1.5 px-3">
          <span className="truncate text-xs text-[var(--yak-components-panel-text)]">
            {field}
          </span>
          {column.primaryKey ? (
            <span className="shrink-0 rounded-sm bg-[var(--yak-color-hover)] px-1 py-0.5 text-[9px] font-medium text-[var(--yak-components-muted-text)]">
              PK
            </span>
          ) : null}
        </span>
        <span className="truncate border-l border-[var(--yak-components-panel-border)] px-3 text-xs text-[var(--yak-components-muted-text)]">
          {columnType(column)}
        </span>
      </button>
    );
  };

  const closeMappingEditor = () => {
    setEditOpen(false);
    setEditSource(null);
    setEditTarget(null);
    setEditingMappingKey(undefined);
  };

  const openMappingEditor = (key?: string) => {
    const mapping = key
      ? fields.find((item) => mappingKey(item) === key)
      : undefined;
    setEditingMappingKey(key);
    setEditSource(mapping?.source || null);
    setEditTarget(mapping?.target || null);
    setEditOpen(true);
  };

  const saveMapping = () => {
    if (!editSource || !editTarget?.trim()) return;
    connectFields(editSource, editTarget, editingMappingKey);
    closeMappingEditor();
  };

  const sameNameMappings = () =>
    buildSameNameMappings(sourceColumns, baseTargetColumns);

  const positionMappings = () =>
    buildPositionMappings(sourceColumns, baseTargetColumns);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Button
          size="sm"
          variant="primary"
          isDisabled={disabled || !mappingReady}
          onClick={() => commitMappings(sameNameMappings())}
        >
          同名映射
        </Button>
        <Button
          size="sm"
          variant="secondary"
          isDisabled={disabled || !mappingReady}
          onClick={() => commitMappings(positionMappings())}
        >
          同行映射
        </Button>
        <Popover
          open={editOpen}
          onOpenChange={(open) => {
            if (open) openMappingEditor();
            else closeMappingEditor();
          }}
        >
          <PopoverTrigger
            type="button"
            disabled={disabled || !mappingReady}
            className="inline-flex h-8 cursor-pointer items-center justify-center rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-button-secondary-border)] bg-[var(--yak-components-button-secondary-bg)] px-3 text-xs font-medium text-[var(--yak-components-button-secondary-text)] outline-none transition-colors hover:bg-[var(--yak-components-button-secondary-bg-hover)] disabled:cursor-not-allowed disabled:opacity-45"
          >
            手动编辑映射关系
          </PopoverTrigger>
          <PopoverContent align="start" className="w-[320px] space-y-3">
            <DataSyncSearchableSelect
              value={editSource}
              options={sourceOptions}
              placeholder="选择来源字段"
              searchPlaceholder="搜索来源字段"
              emptyText="暂无来源字段"
              onValueChange={setEditSource}
            />
            {targetDerived ? (
              <Input
                size="small"
                variant="outlined"
                value={editTarget || ""}
                placeholder="输入去向字段名"
                onChange={(event) => setEditTarget(event.target.value)}
              />
            ) : (
              <DataSyncSearchableSelect
                value={editTarget}
                options={targetOptions}
                placeholder="选择去向字段"
                searchPlaceholder="搜索去向字段"
                emptyText="暂无去向字段"
                onValueChange={setEditTarget}
              />
            )}
            <div className="flex justify-end gap-2">
              <Button
                size="sm"
                variant="secondary"
                onClick={closeMappingEditor}
              >
                取消
              </Button>
              <Button
                size="sm"
                variant="primary"
                isDisabled={!editSource || !editTarget?.trim()}
                onClick={saveMapping}
              >
                {editingMappingKey ? "修改" : "添加"}
              </Button>
            </div>
          </PopoverContent>
        </Popover>
      </div>

      {!mappingReady ? (
        <div className="rounded-sm border border-[var(--yak-components-panel-border)] bg-white">
          <Empty description="暂无可映射字段" />
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
                      stroke={
                        active
                          ? "var(--yak-color-primary)"
                          : "var(--yak-components-panel-border)"
                      }
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
                const nodeHovered = hoveredNode === node.key;
                const selected =
                  node.role === "source" &&
                  selectedSource &&
                  normalizeFieldName(selectedSource) === node.fieldKey;
                const active = key === hoveredMapping || selected;

                return (
                  <g key={node.key}>
                    <rect
                      x={node.x - 4}
                      y={node.y - 4}
                      width={8}
                      height={8}
                      rx={0.75}
                      fill={
                        active
                          ? "var(--yak-color-primary)"
                          : "var(--yak-components-panel-border)"
                      }
                      stroke="#ffffff"
                      strokeWidth={1}
                      transform={
                        "rotate(45 " + node.x + " " + node.y + ")"
                      }
                    />

                    {nodeHovered ? (
                      <g pointerEvents="none">
                        <circle
                          cx={node.x}
                          cy={node.y}
                          r={9}
                          fill="#ffffff"
                          stroke="var(--yak-components-panel-border)"
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
                      data-target-field={
                        node.role === "target" ? node.field : undefined
                      }
                      onMouseEnter={() => setHoveredNode(node.key)}
                      onMouseLeave={() => setHoveredNode(undefined)}
                      onPointerDown={
                        node.role === "source"
                          ? (event) => startNodeDrag(event, node)
                          : undefined
                      }
                      onClick={(event) => {
                        event.stopPropagation();
                        if (disabled) return;
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
                  className="absolute z-40 flex -translate-x-1/2 -translate-y-1/2 items-center gap-1 rounded-sm bg-white p-0.5 shadow-sm"
                  style={{
                    left: geometry.middleX,
                    top: geometry.middleY,
                  }}
                  onMouseEnter={() => showMappingActions(geometry.key)}
                  onMouseLeave={hideMappingActions}
                >
                  <Button
                    size="sm"
                    variant="danger"
                    className="!h-6 !px-2 !text-[11px]"
                    isDisabled={disabled || fields.length <= 1}
                    onClick={() => removeMapping(geometry.key)}
                  >
                    删除
                  </Button>
                  <Button
                    size="sm"
                    variant="secondary"
                    className="!h-6 !px-2 !text-[11px]"
                    isDisabled={disabled}
                    onClick={() => openMappingEditor(geometry.key)}
                  >
                    修改
                  </Button>
                </div>
              ) : null,
            )}

            <div className="relative z-20 self-start border-x border-b border-[var(--yak-components-panel-border)] bg-white">
              <div className="sticky top-0 z-30 bg-white">
                <div className="grid h-8 grid-cols-[minmax(0,1fr)_140px] items-center border-y border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] text-xs font-semibold">
                  <div className="px-3">来源字段</div>
                  <div className="border-l border-[var(--yak-components-panel-border)] px-3">
                    类型
                  </div>
                </div>
                {showSearch ? (
                  <div className="border-b border-[var(--yak-components-panel-border)] bg-white p-2">
                    <Input
                      size="small"
                      variant="outlined"
                      value={sourceKeyword}
                      placeholder="搜索来源字段"
                      onChange={(event) =>
                        setSourceKeyword(event.target.value)
                      }
                    />
                  </div>
                ) : null}
              </div>
              <div>
                {visibleSources.length ? (
                  visibleSources.map((column) =>
                    renderField(column, "source"),
                  )
                ) : (
                  <Empty
                    className="min-h-32"
                    description="没有匹配字段"
                  />
                )}
              </div>
            </div>

            <div className="relative z-0 min-h-[320px] bg-white" />

            <div className="relative z-20 self-start border-x border-b border-[var(--yak-components-panel-border)] bg-white">
              <div className="sticky top-0 z-30 bg-white">
                <div className="grid h-8 grid-cols-[minmax(0,1fr)_140px] items-center border-y border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] text-xs font-semibold">
                  <div className="px-3">去向字段</div>
                  <div className="border-l border-[var(--yak-components-panel-border)] px-3">
                    类型
                  </div>
                </div>
                {showSearch ? (
                  <div className="border-b border-[var(--yak-components-panel-border)] bg-white p-2">
                    <Input
                      size="small"
                      variant="outlined"
                      value={targetKeyword}
                      placeholder="搜索去向字段"
                      onChange={(event) =>
                        setTargetKeyword(event.target.value)
                      }
                    />
                  </div>
                ) : null}
              </div>
              <div>
                {visibleTargets.length ? (
                  visibleTargets.map((column) =>
                    renderField(column, "target"),
                  )
                ) : (
                  <Empty
                    className="min-h-32"
                    description="没有匹配字段"
                  />
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
