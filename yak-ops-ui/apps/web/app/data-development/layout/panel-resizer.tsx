import { useEffect, useRef, type PointerEvent } from "react";

const PANEL_MIN_WIDTH = 220;
const PANEL_MAX_WIDTH = 520;
const KEYBOARD_STEP = 16;

const clampWidth = (width: number, minWidth: number, maxWidth: number) =>
  Math.min(maxWidth, Math.max(minWidth, width));

type PanelResizerProps = {
  width: number;
  onResize: (width: number) => void;
  minWidth?: number;
  maxWidth?: number;
  resizeDirection?: "left" | "right";
  ariaLabel?: string;
};

export function PanelResizer({
  width,
  onResize,
  minWidth = PANEL_MIN_WIDTH,
  maxWidth = PANEL_MAX_WIDTH,
  resizeDirection = "right",
  ariaLabel = "调整项目面板宽度",
}: PanelResizerProps) {
  const startXRef = useRef(0);
  const startWidthRef = useRef(width);
  const activePointerIdRef = useRef<number | null>(null);
  const previousBodyStylesRef = useRef<{ cursor: string; userSelect: string } | null>(null);
  const direction = resizeDirection === "left" ? -1 : 1;

  useEffect(
    () => () => {
      const previousStyles = previousBodyStylesRef.current;
      if (previousStyles) {
        document.body.style.cursor = previousStyles.cursor;
        document.body.style.userSelect = previousStyles.userSelect;
      }
      previousBodyStylesRef.current = null;
      activePointerIdRef.current = null;
    },
    [],
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
  };

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (
      activePointerIdRef.current !== event.pointerId ||
      !event.currentTarget.hasPointerCapture(event.pointerId)
    ) {
      return;
    }

    const delta = event.clientX - startXRef.current;
    onResize(clampWidth(startWidthRef.current + delta * direction, minWidth, maxWidth));
  };

  const handlePointerEnd = (event: PointerEvent<HTMLDivElement>) => {
    if (activePointerIdRef.current !== event.pointerId) return;

    activePointerIdRef.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    restoreBodyStyles();
  };

  return (
    <div className="relative z-30 w-0 shrink-0 self-stretch">
      <div
        role="separator"
        aria-label={ariaLabel}
        aria-orientation="vertical"
        aria-valuemin={minWidth}
        aria-valuemax={maxWidth}
        aria-valuenow={Math.round(width)}
        tabIndex={0}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerEnd}
        onPointerCancel={handlePointerEnd}
        onLostPointerCapture={handlePointerEnd}
        onKeyDown={(event) => {
          if (event.key === "ArrowLeft") {
            event.preventDefault();
            onResize(clampWidth(width - KEYBOARD_STEP * direction, minWidth, maxWidth));
          }
          if (event.key === "ArrowRight") {
            event.preventDefault();
            onResize(clampWidth(width + KEYBOARD_STEP * direction, minWidth, maxWidth));
          }
        }}
        className="group pointer-events-auto absolute inset-y-0 left-1/2 z-20 w-3 -translate-x-1/2 cursor-col-resize touch-none select-none outline-none"
      >
        <span className="pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#d9d9d9] transition-colors group-hover:bg-[var(--yak-color-primary)] group-focus-visible:bg-[var(--yak-color-primary)] group-active:bg-[var(--yak-color-primary)]" />
      </div>
    </div>
  );
}
