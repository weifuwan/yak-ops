import { useEffect, useRef, type PointerEvent } from "react";

const PANEL_MIN_WIDTH = 220;
const PANEL_MAX_WIDTH = 520;
const KEYBOARD_STEP = 16;

const clampWidth = (width: number) =>
  Math.min(PANEL_MAX_WIDTH, Math.max(PANEL_MIN_WIDTH, width));

type PanelResizerProps = {
  width: number;
  onResize: (width: number) => void;
};

export function PanelResizer({ width, onResize }: PanelResizerProps) {
  const startXRef = useRef(0);
  const startWidthRef = useRef(width);

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
    onResize(clampWidth(startWidthRef.current + delta));
  };

  const handlePointerUp = (event: PointerEvent<HTMLDivElement>) => {
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
    document.body.style.cursor = "";
    document.body.style.userSelect = "";
  };

  return (
    <div
      role="separator"
      aria-label="调整项目面板宽度"
      aria-orientation="vertical"
      aria-valuemin={PANEL_MIN_WIDTH}
      aria-valuemax={PANEL_MAX_WIDTH}
      aria-valuenow={Math.round(width)}
      tabIndex={0}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      onKeyDown={(event) => {
        if (event.key === "ArrowLeft") {
          event.preventDefault();
          onResize(clampWidth(width - KEYBOARD_STEP));
        }
        if (event.key === "ArrowRight") {
          event.preventDefault();
          onResize(clampWidth(width + KEYBOARD_STEP));
        }
      }}
      className="group relative z-10 w-1 shrink-0 cursor-col-resize touch-none outline-none"
    >
      <span className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[var(--yak-components-border)] transition-colors group-hover:bg-[var(--yak-color-primary)] group-focus-visible:bg-[var(--yak-color-primary)]" />
    </div>
  );
}
