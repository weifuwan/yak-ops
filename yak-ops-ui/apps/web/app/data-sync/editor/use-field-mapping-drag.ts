import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type RefObject,
} from "react";

interface DragConnection {
  pointerId: number;
  source: string;
  startX: number;
  startY: number;
  x: number;
  y: number;
  clientX: number;
  clientY: number;
  moved: boolean;
  target?: string;
  occupied: boolean;
}

interface DragOptions {
  canvas: RefObject<HTMLDivElement>;
  sources: RefObject<Map<string, HTMLDivElement>>;
  targets: RefObject<Map<string, HTMLDivElement>>;
  occupiedTargets: ReadonlySet<string>;
  onConnect: (source: string, target: string) => void;
}

const DRAG_THRESHOLD = 4;

export function useFieldMappingDrag({
  canvas,
  sources,
  targets,
  occupiedTargets,
  onConnect,
}: DragOptions) {
  const [connection, setConnection] = useState<DragConnection | null>(null);
  const connectionRef = useRef<DragConnection | null>(null);
  const suppressClick = useRef(false);

  const updateConnection = useCallback((value: DragConnection | null) => {
    connectionRef.current = value;
    setConnection(value);
  }, []);

  const targetAt = useCallback(
    (clientX: number, clientY: number) => {
      const node = document.elementFromPoint(clientX, clientY);
      const field = node?.closest<HTMLElement>("[data-mapping-target]")?.dataset.mappingTarget;
      if (field && targets.current.has(field)) return field;
      for (const [name, element] of targets.current) {
        const rect = element.getBoundingClientRect();
        if (
          clientY >= rect.top &&
          clientY <= rect.bottom &&
          clientX >= rect.left - 28 &&
          clientX <= rect.left + 56
        ) {
          return name;
        }
      }
      return undefined;
    },
    [targets],
  );

  const start = useCallback(
    (event: ReactPointerEvent<HTMLButtonElement>, source: string) => {
      if (event.button !== 0 || connectionRef.current) return;
      const root = canvas.current;
      const row = sources.current.get(source);
      if (!root || !row) return;
      const rootRect = root.getBoundingClientRect();
      const rect = row.getBoundingClientRect();
      const x = rect.right - rootRect.left;
      const y = rect.top - rootRect.top + rect.height / 2;
      updateConnection({
        pointerId: event.pointerId,
        source,
        startX: x,
        startY: y,
        x,
        y,
        clientX: event.clientX,
        clientY: event.clientY,
        moved: false,
        occupied: false,
      });
    },
    [canvas, sources, updateConnection],
  );

  const pointerId = connection?.pointerId;
  useEffect(() => {
    if (pointerId === undefined) return;
    const move = (event: PointerEvent) => {
      const active = connectionRef.current;
      const root = canvas.current;
      if (!active || active.pointerId !== event.pointerId || !root) return;
      const rect = root.getBoundingClientRect();
      const moved =
        active.moved ||
        Math.hypot(event.clientX - active.clientX, event.clientY - active.clientY) >=
          DRAG_THRESHOLD;
      const target = targetAt(event.clientX, event.clientY);
      const targetRect = target ? targets.current.get(target)?.getBoundingClientRect() : undefined;
      updateConnection({
        ...active,
        moved,
        target,
        occupied: Boolean(target && occupiedTargets.has(target)),
        x: targetRect ? targetRect.left - rect.left : event.clientX - rect.left,
        y: targetRect
          ? targetRect.top - rect.top + targetRect.height / 2
          : event.clientY - rect.top,
      });
      if (moved) event.preventDefault();
    };
    const stop = (event: PointerEvent) => {
      const active = connectionRef.current;
      if (!active || active.pointerId !== event.pointerId) return;
      const target = targetAt(event.clientX, event.clientY) || active.target;
      if (active.moved) {
        suppressClick.current = true;
        window.setTimeout(() => {
          suppressClick.current = false;
        }, 0);
        if (target && !occupiedTargets.has(target)) onConnect(active.source, target);
      }
      updateConnection(null);
    };
    const cancel = (event: PointerEvent) => {
      if (connectionRef.current?.pointerId === event.pointerId) updateConnection(null);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        suppressClick.current = true;
        updateConnection(null);
        window.setTimeout(() => {
          suppressClick.current = false;
        }, 0);
      }
    };
    window.addEventListener("pointermove", move, { passive: false });
    window.addEventListener("pointerup", stop);
    window.addEventListener("pointercancel", cancel);
    window.addEventListener("keydown", escape);
    return () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", stop);
      window.removeEventListener("pointercancel", cancel);
      window.removeEventListener("keydown", escape);
    };
  }, [pointerId, canvas, targets, occupiedTargets, onConnect, targetAt, updateConnection]);

  return { connection, start, suppressClick };
}
