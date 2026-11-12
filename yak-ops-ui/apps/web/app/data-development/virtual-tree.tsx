import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type DragEvent,
  type KeyboardEvent,
  type MouseEvent,
  type ReactNode,
} from "react";
import { ChevronDown, ChevronRight } from "lucide-react";

export type VirtualTreeDropPosition = "before" | "inside" | "after";

export interface VirtualTreeNode {
  id: string;
  parentId?: string;
  level: number;
  ariaLabel: string;
  expandable?: boolean;
  expanded?: boolean;
  selected?: boolean;
  draggable?: boolean;
}

interface VirtualTreeRenderState {
  active: boolean;
}

interface VirtualTreeProps<T extends VirtualTreeNode> {
  ariaLabel: string;
  items: T[];
  rowHeight?: number;
  overscan?: number;
  className?: string;
  onToggle?: (item: T) => void;
  onActivate?: (item: T) => void;
  onItemDoubleClick?: (item: T) => void;
  isItemInteractive?: (item: T) => boolean;
  onContextMenu?: (item: T, event: MouseEvent<HTMLDivElement>) => void;
  onMove?: (
    source: T,
    target: T,
    position: VirtualTreeDropPosition,
  ) => void;
  renderItem: (item: T, state: VirtualTreeRenderState) => ReactNode;
}

const DEFAULT_ROW_HEIGHT = 26;
const DEFAULT_OVERSCAN = 6;
const INDENT_SIZE = 16;

export function VirtualTree<T extends VirtualTreeNode>({
  ariaLabel,
  items,
  rowHeight = DEFAULT_ROW_HEIGHT,
  overscan = DEFAULT_OVERSCAN,
  className,
  onToggle,
  onActivate,
  onItemDoubleClick,
  isItemInteractive,
  onContextMenu,
  onMove,
  renderItem,
}: VirtualTreeProps<T>) {
  const viewportRef = useRef<HTMLDivElement | null>(null);
  const [viewportHeight, setViewportHeight] = useState(0);
  const [scrollTop, setScrollTop] = useState(0);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [dropTarget, setDropTarget] = useState<{
    id: string;
    position: VirtualTreeDropPosition;
  } | null>(null);

  const itemIndex = useMemo(
    () => new Map(items.map((item, index) => [item.id, index])),
    [items],
  );

  useEffect(() => {
    const viewport = viewportRef.current;
    if (!viewport) return;

    const updateHeight = () => setViewportHeight(viewport.clientHeight);
    updateHeight();

    const observer = new ResizeObserver(updateHeight);
    observer.observe(viewport);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const selected = items.find((item) => item.selected);
    if (selected) {
      setActiveId(selected.id);
      return;
    }

    setActiveId((current) =>
      current && itemIndex.has(current) ? current : (items[0]?.id ?? null),
    );
  }, [itemIndex, items]);

  const visibleRange = useMemo(() => {
    if (items.length === 0) return { start: 0, end: 0 };

    const start = Math.max(0, Math.floor(scrollTop / rowHeight) - overscan);
    const visibleCount = Math.ceil(viewportHeight / rowHeight);
    const end = Math.min(
      items.length,
      start + visibleCount + overscan * 2 + 1,
    );

    return { start, end };
  }, [items.length, overscan, rowHeight, scrollTop, viewportHeight]);

  const ensureVisible = (index: number) => {
    const viewport = viewportRef.current;
    if (!viewport) return;

    const rowTop = index * rowHeight;
    const rowBottom = rowTop + rowHeight;

    if (rowTop < viewport.scrollTop) {
      viewport.scrollTop = rowTop;
    } else if (rowBottom > viewport.scrollTop + viewport.clientHeight) {
      viewport.scrollTop = rowBottom - viewport.clientHeight;
    }
  };

  const setActiveIndex = (index: number) => {
    if (items.length === 0) return;
    const nextIndex = Math.max(0, Math.min(items.length - 1, index));
    setActiveId(items[nextIndex].id);
    ensureVisible(nextIndex);
  };

  const findParentIndex = (index: number) => {
    const current = items[index];
    if (!current || current.level <= 1) return -1;

    for (let cursor = index - 1; cursor >= 0; cursor -= 1) {
      if (items[cursor].level < current.level) return cursor;
    }

    return -1;
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (items.length === 0) return;

    const currentIndex =
      activeId === null ? 0 : (itemIndex.get(activeId) ?? 0);
    const current = items[currentIndex];

    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        setActiveIndex(currentIndex + 1);
        break;
      case "ArrowUp":
        event.preventDefault();
        setActiveIndex(currentIndex - 1);
        break;
      case "ArrowRight":
        if (!current.expandable) break;
        event.preventDefault();
        if (!current.expanded) {
          onToggle?.(current);
        } else if (items[currentIndex + 1]?.level > current.level) {
          setActiveIndex(currentIndex + 1);
        }
        break;
      case "ArrowLeft":
        event.preventDefault();
        if (current.expandable && current.expanded) {
          onToggle?.(current);
        } else {
          const parentIndex = findParentIndex(currentIndex);
          if (parentIndex >= 0) setActiveIndex(parentIndex);
        }
        break;
      case "Enter":
        event.preventDefault();
        onActivate?.(current);
        break;
      case " ":
        if (!current.expandable) break;
        event.preventDefault();
        onToggle?.(current);
        break;
      case "Home":
        event.preventDefault();
        setActiveIndex(0);
        break;
      case "End":
        event.preventDefault();
        setActiveIndex(items.length - 1);
        break;
      default:
        break;
    }
  };

  const getDropPosition = (
    event: DragEvent<HTMLDivElement>,
    item: T,
  ): VirtualTreeDropPosition => {
    const bounds = event.currentTarget.getBoundingClientRect();
    const offset = event.clientY - bounds.top;
    const ratio = offset / Math.max(bounds.height, 1);

    if (item.expandable && ratio >= 0.25 && ratio <= 0.75) return "inside";
    return ratio < 0.5 ? "before" : "after";
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>, target: T) => {
    event.preventDefault();
    const sourceIndex =
      draggingId === null ? undefined : itemIndex.get(draggingId);
    if (sourceIndex === undefined || draggingId === target.id) {
      setDraggingId(null);
      setDropTarget(null);
      return;
    }

    const position = getDropPosition(event, target);
    onMove?.(items[sourceIndex], target, position);
    setDraggingId(null);
    setDropTarget(null);
  };

  const visibleItems = items.slice(visibleRange.start, visibleRange.end);

  return (
    <div
      ref={viewportRef}
      role="tree"
      tabIndex={0}
      aria-label={ariaLabel}
      aria-activedescendant={activeId ?? undefined}
      onKeyDown={handleKeyDown}
      onScroll={(event) => setScrollTop(event.currentTarget.scrollTop)}
      onFocus={() => {
        if (!activeId && items[0]) setActiveId(items[0].id);
      }}
      className={[
        "relative min-h-0 overflow-auto outline-none",
        className ?? "",
      ].join(" ")}
    >
      <div
        className="relative min-w-full"
        style={{ height: items.length * rowHeight }}
      >
        {visibleItems.map((item, offset) => {
          const index = visibleRange.start + offset;
          const active = item.id === activeId;
          const drop =
            dropTarget?.id === item.id ? dropTarget.position : null;

          return (
            <div
              key={item.id}
              id={item.id}
              role="treeitem"
              aria-level={item.level}
              aria-label={item.ariaLabel}
              aria-selected={item.selected ?? false}
              aria-expanded={item.expandable ? Boolean(item.expanded) : undefined}
              draggable={Boolean(onMove && item.draggable)}
              onMouseDown={() => setActiveId(item.id)}
              onClick={() => onActivate?.(item)}
              onContextMenu={(event) => {
                if (!onContextMenu) return;
                event.preventDefault();
                event.stopPropagation();
                setActiveId(item.id);
                onContextMenu(item, event);
              }}
              onDoubleClick={() => {
                if (onItemDoubleClick) {
                  onItemDoubleClick(item);
                } else if (item.expandable) {
                  onToggle?.(item);
                }
              }}
              onDragStart={(event) => {
                if (!onMove || !item.draggable) return;
                setDraggingId(item.id);
                event.dataTransfer.effectAllowed = "move";
                event.dataTransfer.setData("text/plain", item.id);
              }}
              onDragEnd={() => {
                setDraggingId(null);
                setDropTarget(null);
              }}
              onDragOver={(event) => {
                if (!onMove || !draggingId || draggingId === item.id) return;
                event.preventDefault();
                event.dataTransfer.dropEffect = "move";
                setDropTarget({
                  id: item.id,
                  position: getDropPosition(event, item),
                });
              }}
              onDragLeave={(event) => {
                if (event.currentTarget.contains(event.relatedTarget as Node)) {
                  return;
                }
                if (dropTarget?.id === item.id) setDropTarget(null);
              }}
              onDrop={(event) => handleDrop(event, item)}
              className={[
                "group absolute left-0 right-0 flex select-none items-center text-[12px] leading-none outline-none",
                isItemInteractive?.(item) ? "cursor-pointer" : "cursor-default",
                item.selected
                  ? "bg-[var(--yak-color-primary-subtle)]"
                  : active
                    ? "bg-[var(--yak-color-hover)]"
                    : "hover:bg-[var(--yak-color-hover)]",
                draggingId === item.id ? "opacity-50" : "",
                drop === "inside"
                  ? "bg-[var(--yak-color-primary-subtle)]"
                  : "",
              ].join(" ")}
              style={{
                top: index * rowHeight,
                height: rowHeight,
              }}
            >
              {drop === "before" ? (
                <span className="pointer-events-none absolute left-0 right-0 top-0 h-px bg-[var(--yak-color-primary)]" />
              ) : null}
              {drop === "after" ? (
                <span className="pointer-events-none absolute bottom-0 left-0 right-0 h-px bg-[var(--yak-color-primary)]" />
              ) : null}

              <div
                className="flex h-full min-w-0 flex-1 items-center"
                style={{ paddingLeft: 4 + (item.level - 1) * INDENT_SIZE }}
              >
                {item.expandable ? (
                  <button
                    type="button"
                    tabIndex={-1}
                    aria-label={item.expanded ? "折叠" : "展开"}
                    onClick={(event) => {
                      event.stopPropagation();
                      setActiveId(item.id);
                      onToggle?.(item);
                    }}
                    className="flex size-4 shrink-0 cursor-pointer items-center justify-center rounded-sm text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]"
                  >
                    {item.expanded ? (
                      <ChevronDown size={12} aria-hidden="true" />
                    ) : (
                      <ChevronRight size={12} aria-hidden="true" />
                    )}
                  </button>
                ) : (
                  <span className="size-4 shrink-0" aria-hidden="true" />
                )}

                {renderItem(item, { active })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
