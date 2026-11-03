import { Button, Input, Popover, PopoverContent, PopoverTrigger } from "@yak-ops/yak-ui";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { DataSourceCatalogColumn } from "@/service/datasource";
import type { DirectFieldMapping } from "@/service/data-sync/transforms";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { useFieldMappingDrag } from "./use-field-mapping-drag";

interface Props {
  fields: DirectFieldMapping[];
  sourceColumns: DataSourceCatalogColumn[];
  targetColumns?: DataSourceCatalogColumn[];
  disabled: boolean;
  onChange: (fields: DirectFieldMapping[]) => void;
}

interface ConnectionGeometry {
  source: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

const BORDER = "var(--yak-components-panel-border)";
const ACCENT = "var(--yak-color-primary)";
const ROW =
  "relative grid h-8 grid-cols-[minmax(0,1fr)_140px] items-center border-x border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] text-xs text-[var(--yak-components-panel-text)]";
const HEADER =
  "grid h-8 grid-cols-[minmax(0,1fr)_140px] items-center border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-muted)] text-xs font-medium";

export function FieldMappingCanvas({
  fields,
  sourceColumns,
  targetColumns,
  disabled,
  onChange,
}: Props) {
  const canvas = useRef<HTMLDivElement>(null);
  const sources = useRef(new Map<string, HTMLDivElement>());
  const targets = useRef(new Map<string, HTMLDivElement>());
  const [geometries, setGeometries] = useState<ConnectionGeometry[]>([]);
  const [pickerSource, setPickerSource] = useState<string | null>(null);
  const [editingSource, setEditingSource] = useState<string | null>(null);
  const [customTarget, setCustomTarget] = useState("");
  const [hoveredSource, setHoveredSource] = useState<string | null>(null);
  const hoverTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mappedSources = useMemo(() => new Set(fields.map((field) => field.source)), [fields]);
  const mappedTargets = useMemo(
    () => new Set(fields.map((field) => field.target.toLocaleLowerCase())),
    [fields],
  );
  const targetNames = useMemo(() => {
    if (targetColumns) return targetColumns;
    const names = new Set(sourceColumns.map((column) => column.name));
    const extra = fields
      .filter((field) => !names.has(field.target))
      .map((field) => {
        names.add(field.target);
        return {
          name: field.target,
          typeName: sourceColumns.find((column) => column.name === field.source)?.typeName,
        };
      });
    return [...sourceColumns, ...extra];
  }, [targetColumns, sourceColumns, fields]);
  const occupiedTargets = useMemo(() => new Set(fields.map((field) => field.target)), [fields]);

  const connect = useCallback(
    (source: string, target: string) => {
      const trimmed = target.trim();
      if (
        disabled ||
        !trimmed ||
        mappedSources.has(source) ||
        mappedTargets.has(trimmed.toLocaleLowerCase())
      )
        return;
      onChange([...fields, { source, target: trimmed }]);
      setPickerSource(null);
      setCustomTarget("");
    },
    [disabled, mappedSources, mappedTargets, fields, onChange],
  );

  const relink = (source: string, target: string) => {
    const trimmed = target.trim();
    if (
      disabled ||
      !trimmed ||
      fields.some(
        (field) =>
          field.source !== source &&
          field.target.toLocaleLowerCase() === trimmed.toLocaleLowerCase(),
      )
    )
      return;
    onChange(
      fields.map((field) => (field.source === source ? { ...field, target: trimmed } : field)),
    );
    setEditingSource(null);
    setCustomTarget("");
  };

  const { connection, start, suppressClick } = useFieldMappingDrag({
    canvas,
    sources,
    targets,
    occupiedTargets,
    onConnect: connect,
  });

  const measure = useCallback(() => {
    const root = canvas.current;
    if (!root) return;
    const bounds = root.getBoundingClientRect();
    setGeometries(
      fields.flatMap((field) => {
        const left = sources.current.get(field.source)?.getBoundingClientRect();
        const right = targets.current.get(field.target)?.getBoundingClientRect();
        if (!left || !right) return [];
        return [
          {
            source: field.source,
            x1: left.right - bounds.left,
            y1: left.top - bounds.top + left.height / 2,
            x2: right.left - bounds.left,
            y2: right.top - bounds.top + right.height / 2,
          },
        ];
      }),
    );
  }, [fields]);

  useLayoutEffect(() => {
    measure();
  }, [measure, targetNames, sourceColumns]);
  useEffect(() => {
    const root = canvas.current;
    if (!root) return;
    const observer = new ResizeObserver(measure);
    observer.observe(root);
    window.addEventListener("resize", measure);
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, [measure]);
  useEffect(
    () => () => {
      if (hoverTimer.current) clearTimeout(hoverTimer.current);
    },
    [],
  );

  const showActions = (source: string) => {
    if (hoverTimer.current) clearTimeout(hoverTimer.current);
    setHoveredSource(source);
  };
  const hideActions = (source: string) => {
    if (hoverTimer.current) clearTimeout(hoverTimer.current);
    hoverTimer.current = setTimeout(() => {
      setHoveredSource((current) => (current === source ? null : current));
    }, 150);
  };
  const targetOptions = (source: string) =>
    (targetColumns ?? [])
      .filter(
        (column) =>
          !fields.some(
            (field) =>
              field.source !== source &&
              field.target.toLocaleLowerCase() === column.name.toLocaleLowerCase(),
          ),
      )
      .map((column) => ({ label: column.name, value: column.name }));

  const targetPicker = (source: string, existing?: string) => (
    <div className="w-56 space-y-2">
      <span className="text-xs text-[var(--yak-components-muted-text)]">
        {targetColumns ? "选择目标字段" : "输入目标字段"}
      </span>
      {targetColumns ? (
        <DataSyncSearchableSelect
          ariaLabel={"映射 " + source + " 到"}
          placeholder="选择目标字段"
          value={existing || null}
          options={targetOptions(source)}
          onValueChange={(target) => (existing ? relink(source, target) : connect(source, target))}
        />
      ) : (
        <>
          <Input
            size="small"
            aria-label={"目标字段 " + source}
            placeholder="请输入目标字段名称"
            value={customTarget}
            onChange={(event) => setCustomTarget(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                if (existing) relink(source, customTarget);
                else connect(source, customTarget);
              }
            }}
          />
          <Button
            size="small"
            disabled={!customTarget.trim()}
            onClick={() =>
              existing ? relink(source, customTarget) : connect(source, customTarget)
            }
          >
            确认
          </Button>
        </>
      )}
    </div>
  );

  const header = (name: string) => (
    <div className={HEADER}>
      <span className="truncate px-3">{name}</span>
      <span className="border-l border-[var(--yak-components-panel-border)] px-3">类型</span>
    </div>
  );
  const rowContents = (column: { name: string; typeName?: string; primaryKey?: boolean }) => (
    <>
      <span className="truncate px-3" title={column.name}>
        {column.name}
        {column.primaryKey ? (
          <span className="ml-2 text-[var(--yak-components-muted-text)]">主键</span>
        ) : null}
      </span>
      <span
        className="truncate border-l border-[var(--yak-components-panel-border)] px-3"
        title={column.typeName}
      >
        {column.typeName || "—"}
      </span>
    </>
  );

  return (
    <div className="overflow-x-auto rounded-md border-[var(--yak-components-panel-border)]">
      <div
        ref={canvas}
        className="relative grid min-w-[980px] items-start"
        style={{
          gridTemplateColumns: "360px minmax(260px, 1fr) 360px",
          minHeight: 32 + Math.max(sourceColumns.length, targetNames.length) * 32,
        }}
      >
        <div className="relative z-20">
          {header("来源字段")}
          {sourceColumns.map((column) => {
            const mapped = mappedSources.has(column.name);
            return (
              <div
                key={column.name}
                ref={(element) => {
                  if (element) sources.current.set(column.name, element);
                  else sources.current.delete(column.name);
                }}
                className={ROW}
              >
                {rowContents(column)}
                {mapped ? (
                  <span
                    className="absolute -right-1 top-1/2 z-30 h-2 w-2 -translate-y-1/2 rotate-45"
                    style={{ backgroundColor: hoveredSource === column.name ? ACCENT : BORDER }}
                  />
                ) : (
                  <Popover
                    open={pickerSource === column.name}
                    onOpenChange={(open) => {
                      if (!suppressClick.current) {
                        setPickerSource(open ? column.name : null);
                        setCustomTarget("");
                      }
                    }}
                  >
                    <PopoverTrigger
                      type="button"
                      disabled={disabled}
                      aria-label={"连接来源字段 " + column.name}
                      className="absolute -right-2.5 top-1/2 z-40 flex h-5 w-5 -translate-y-1/2 touch-none items-center justify-center rounded-full text-[var(--yak-color-primary)] hover:bg-[var(--yak-components-panel-muted)]"
                      onPointerDown={(event) => {
                        if (!disabled) start(event, column.name);
                      }}
                      onClick={(event) => {
                        if (suppressClick.current) {
                          event.preventDefault();
                          event.stopPropagation();
                        }
                      }}
                    >
                      <span className="h-2 w-2 rotate-45 bg-[var(--yak-color-primary)]" />
                    </PopoverTrigger>
                    <PopoverContent side="top" align="start">
                      {targetPicker(column.name)}
                    </PopoverContent>
                  </Popover>
                )}
              </div>
            );
          })}
        </div>
        <div aria-hidden="true" />
        <div className="relative z-20">
          {header("目标字段")}
          {targetNames.map((column) => {
            const occupied = mappedTargets.has(column.name.toLocaleLowerCase());
            const over = connection?.target === column.name;
            return (
              <div
                key={column.name}
                data-mapping-target={column.name}
                ref={(element) => {
                  if (element) targets.current.set(column.name, element);
                  else targets.current.delete(column.name);
                }}
                className={
                  ROW + (over ? " outline outline-1 outline-[var(--yak-color-primary)]" : "")
                }
              >
                <span
                  className="pointer-events-none absolute -left-1 top-1/2 z-30 h-2 w-2 -translate-y-1/2 rotate-45"
                  style={{
                    backgroundColor:
                      over && occupied
                        ? "var(--yak-components-field-error)"
                        : occupied
                          ? ACCENT
                          : BORDER,
                  }}
                />
                {rowContents(column)}
              </div>
            );
          })}
        </div>
        <svg
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 z-10 h-full w-full overflow-visible"
        >
          {geometries.map((line) => (
            <g key={line.source}>
              <line
                x1={line.x1}
                y1={line.y1}
                x2={line.x2}
                y2={line.y2}
                stroke="transparent"
                strokeWidth={18}
                pointerEvents="stroke"
                onMouseEnter={() => showActions(line.source)}
                onMouseLeave={() => hideActions(line.source)}
              />
              <line
                x1={line.x1}
                y1={line.y1}
                x2={line.x2}
                y2={line.y2}
                stroke={
                  hoveredSource === line.source || editingSource === line.source ? ACCENT : BORDER
                }
                strokeWidth={hoveredSource === line.source ? 2 : 1}
              />
            </g>
          ))}
          {connection?.moved ? (
            <line
              x1={connection.startX}
              y1={connection.startY}
              x2={connection.x}
              y2={connection.y}
              stroke={connection.occupied ? "var(--yak-components-field-error)" : ACCENT}
              strokeWidth={1.5}
              strokeDasharray="6 5"
            />
          ) : null}
        </svg>
        {geometries.map((line) =>
          hoveredSource === line.source || editingSource === line.source ? (
            <div
              key={"action-" + line.source}
              className="absolute z-30 flex -translate-x-1/2 -translate-y-1/2 items-center gap-1 rounded-md bg-[var(--yak-components-panel-bg)] p-1 shadow-sm"
              style={{ left: (line.x1 + line.x2) / 2, top: (line.y1 + line.y2) / 2 }}
              onMouseEnter={() => showActions(line.source)}
              onMouseLeave={() => hideActions(line.source)}
            >
              <Popover
                open={editingSource === line.source}
                onOpenChange={(open) => {
                  setEditingSource(open ? line.source : null);
                  setCustomTarget(
                    open ? fields.find((field) => field.source === line.source)?.target || "" : "",
                  );
                }}
              >
                <PopoverTrigger
                  className="rounded px-2 py-1 text-xs hover:bg-[var(--yak-components-panel-muted)]"
                  disabled={disabled}
                >
                  修改
                </PopoverTrigger>
                <PopoverContent side="bottom">
                  {targetPicker(
                    line.source,
                    fields.find((field) => field.source === line.source)?.target,
                  )}
                </PopoverContent>
              </Popover>
              <Button
                variant="ghost"
                size="small"
                disabled={disabled || fields.length <= 1}
                onClick={() => {
                  onChange(fields.filter((field) => field.source !== line.source));
                  setHoveredSource(null);
                }}
              >
                删除
              </Button>
            </div>
          ) : null,
        )}
      </div>
    </div>
  );
}
