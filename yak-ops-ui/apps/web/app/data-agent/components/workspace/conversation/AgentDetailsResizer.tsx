import { useEffect, useRef, useState, type PointerEvent } from "react";

const DETAILS_MIN_WIDTH = 320;
const KEYBOARD_STEP = 16;

const clampWidth = (width: number, maxWidth: number) =>
  Math.min(maxWidth, Math.max(DETAILS_MIN_WIDTH, width));

type AgentDetailsResizerProps = {
  width: number;
  maxWidth: number;
  onResize: (width: number) => void;
  onDraggingChange: (dragging: boolean) => void;
};

export function AgentDetailsResizer({
  width,
  maxWidth,
  onResize,
  onDraggingChange,
}: AgentDetailsResizerProps) {
  const [dragging, setDragging] = useState(false);
  const startXRef = useRef(0);
  const startWidthRef = useRef(width);
  const activePointerIdRef = useRef<number | null>(null);
  const previousBodyStylesRef = useRef<{
    cursor: string;
    userSelect: string;
  } | null>(null);

  useEffect(
    () => () => {
      const previousStyles = previousBodyStylesRef.current;
      if (previousStyles) {
        document.body.style.cursor = previousStyles.cursor;
        document.body.style.userSelect = previousStyles.userSelect;
      }
      previousBodyStylesRef.current = null;
      activePointerIdRef.current = null;
      onDraggingChange(false);
    },
    [onDraggingChange],
  );

  const restoreBodyStyles = () => {
    const previousStyles = previousBodyStylesRef.current;
    if (!previousStyles) return;

    document.body.style.cursor = previousStyles.cursor;
    document.body.style.userSelect = previousStyles.userSelect;
    previousBodyStylesRef.current = null;
  };

  const handlePointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0 || activePointerIdRef.current !== null) return;

    event.preventDefault();
    startXRef.current = event.clientX;
    startWidthRef.current = width;
    event.currentTarget.setPointerCapture(event.pointerId);
    activePointerIdRef.current = event.pointerId;
    previousBodyStylesRef.current = {
      cursor: document.body.style.cursor,
      userSelect: document.body.style.userSelect,
    };
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    setDragging(true);
    onDraggingChange(true);
  };

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (
      activePointerIdRef.current !== event.pointerId ||
      !event.currentTarget.hasPointerCapture(event.pointerId)
    ) {
      return;
    }

    // The handle sits on the panel's left edge: moving left makes the panel wider.
    onResize(
      clampWidth(
        startWidthRef.current - (event.clientX - startXRef.current),
        maxWidth,
      ),
    );
  };

  const handlePointerEnd = (event: PointerEvent<HTMLDivElement>) => {
    if (activePointerIdRef.current !== event.pointerId) return;

    activePointerIdRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    restoreBodyStyles();
    setDragging(false);
    onDraggingChange(false);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      onResize(clampWidth(width + KEYBOARD_STEP, maxWidth));
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      onResize(clampWidth(width - KEYBOARD_STEP, maxWidth));
    } else if (event.key === "Home") {
      event.preventDefault();
      onResize(DETAILS_MIN_WIDTH);
    } else if (event.key === "End") {
      event.preventDefault();
      onResize(maxWidth);
    }
  };

  return (
    <div
      className="group relative z-[3] w-0 shrink-0 self-stretch max-md:hidden"
      data-dragging={dragging}
    >
      <div className="relative h-full w-0 shrink-0">
        <span
          aria-hidden="true"
          className="pointer-events-none absolute left-0 top-8 bottom-8 z-[1] w-px -translate-x-full bg-[linear-gradient(to_bottom,transparent_0%,#1b3030_20%,#1b3030_80%,transparent_100%)] opacity-0 transition-opacity duration-100 ease-out group-hover:opacity-100 group-focus-within:opacity-100 group-data-[dragging=true]:opacity-100"
        />
        <div
          role="separator"
          aria-label="调整 Agent 详情面板宽度"
          aria-orientation="vertical"
          aria-valuemin={DETAILS_MIN_WIDTH}
          aria-valuemax={maxWidth}
          aria-valuenow={Math.round(width)}
          tabIndex={0}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerEnd}
          onPointerCancel={handlePointerEnd}
          onLostPointerCapture={handlePointerEnd}
          onKeyDown={handleKeyDown}
          className="absolute left-[-6px] top-0 z-[2] h-full w-3 cursor-col-resize touch-none select-none outline-none"
        >
          <span
            aria-hidden="true"
            className="pointer-events-none absolute left-1/2 top-1/2 h-6 w-2 -translate-x-1/2 -translate-y-1/2 rounded-[4px] border border-[var(--slate-6)] bg-white shadow-[0_1px_3px_rgba(0,0,0,0.08)] opacity-0 transition-[opacity,background-color,border-color,box-shadow] duration-100 ease-out group-hover:opacity-100 group-focus-visible:opacity-100 group-data-[dragging=true]:opacity-100"
          />
        </div>
      </div>
    </div>
  );
}

export default AgentDetailsResizer;
