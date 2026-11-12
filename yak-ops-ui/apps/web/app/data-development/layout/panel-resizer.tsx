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
  const direction = resizeDirection === "left" ? -1 : 1;

  useEffect(
    () => () => {
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
    },
    [],
  );

  const handlePointerDown = (event: PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    startXRef.current = event.clientX;
    startWidthRef.current = width;
    event.currentTarget.setPointerCapture(event.pointerId);
    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
  };

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!event.currentTarget.hasPointerCapture(event.pointerId)) return;

    const delta = event.clientX - startXRef.current;
    onResize(clampWidth(startWidthRef.current + delta * direction, minWidth, maxWidth));
  };

  const handlePointerUp = (event: PointerEvent<HTMLDivElement>) => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    document.body.style.cursor = "";
    document.body.style.userSelect = "";
  };

  return (
    <div className="relative z-20 w-0 shrink-0 self-stretch">
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
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
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
        className="group absolute inset-y-0 left-1/2 z-10 w-2 -translate-x-1/2 cursor-col-resize touch-none outline-none"
      >
        <span className="pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#d9d9d9] transition-colors group-hover:bg-[var(--yak-color-primary)] group-focus-visible:bg-[var(--yak-color-primary)] group-active:bg-[var(--yak-color-primary)]" />
      </div>
    </div>
  );
}
