import { useEffect, useRef, type PointerEvent } from "react";

const KEYBOARD_STEP = 16;

const clampHeight = (height: number, minHeight: number, maxHeight: number) =>
  Math.min(maxHeight, Math.max(minHeight, height));

type PanelHeightResizerProps = {
  height: number;
  minHeight: number;
  maxHeight: number;
  onResize: (height: number) => void;
  ariaLabel?: string;
};

export function PanelHeightResizer({
  height,
  minHeight,
  maxHeight,
  onResize,
  ariaLabel = "调整上下区域高度",
}: PanelHeightResizerProps) {
  const startYRef = useRef(0);
  const startHeightRef = useRef(height);
  const activePointerIdRef = useRef<number | null>(null);
  const previousBodyStylesRef = useRef<{ cursor: string; userSelect: string } | null>(null);

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
    startYRef.current = event.clientY;
    startHeightRef.current = height;
    event.currentTarget.setPointerCapture(event.pointerId);
    activePointerIdRef.current = event.pointerId;
    previousBodyStylesRef.current = {
      cursor: document.body.style.cursor,
      userSelect: document.body.style.userSelect,
    };
    document.body.style.cursor = "row-resize";
    document.body.style.userSelect = "none";
  };

  const handlePointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (
      activePointerIdRef.current !== event.pointerId ||
      !event.currentTarget.hasPointerCapture(event.pointerId)
    ) {
      return;
    }

    const delta = event.clientY - startYRef.current;
    onResize(clampHeight(startHeightRef.current - delta, minHeight, maxHeight));
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
    <div
      role="separator"
      aria-label={ariaLabel}
      aria-orientation="horizontal"
      aria-valuemin={minHeight}
      aria-valuemax={maxHeight}
      aria-valuenow={Math.round(height)}
      tabIndex={0}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerEnd}
      onPointerCancel={handlePointerEnd}
      onLostPointerCapture={handlePointerEnd}
      onKeyDown={(event) => {
        if (event.key === "ArrowUp") {
          event.preventDefault();
          onResize(clampHeight(height + KEYBOARD_STEP, minHeight, maxHeight));
        }
        if (event.key === "ArrowDown") {
          event.preventDefault();
          onResize(clampHeight(height - KEYBOARD_STEP, minHeight, maxHeight));
        }
      }}
      className="group relative z-10 flex h-2 w-full shrink-0 cursor-row-resize touch-none select-none items-center justify-center border-y border-[#e5e7eb] bg-[#fbfcfe] outline-none"
    >
      <span className="pointer-events-none h-[3px] w-8 rounded-full bg-[#cbd2dc] transition-colors group-hover:bg-[var(--yak-color-primary)] group-focus-visible:bg-[var(--yak-color-primary)] group-active:bg-[var(--yak-color-primary)]" />
    </div>
  );
}
