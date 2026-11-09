import { useEffect, useRef } from "react";

const CHARACTERS = " .:-=+";
const FRAME_INTERVAL = 50;
const CELL_SIZE = 16;
const POINTER_RADIUS = 112;

const clamp = (value: number, min = 0, max = 1) => Math.min(max, Math.max(min, value));

function radialField(
  x: number,
  y: number,
  centerX: number,
  centerY: number,
  radiusX: number,
  radiusY: number,
) {
  const dx = (x - centerX) / radiusX;
  const dy = (y - centerY) / radiusY;
  return Math.exp(-(dx * dx + dy * dy) * 1.65);
}

function ambientField(x: number, y: number, time: number) {
  const first = radialField(
    x,
    y,
    0.78 + Math.sin(time * 0.11) * 0.035,
    0.2 + Math.cos(time * 0.09) * 0.025,
    0.48,
    0.34,
  );
  const second = radialField(
    x,
    y,
    0.96 + Math.cos(time * 0.08) * 0.025,
    0.62 + Math.sin(time * 0.1) * 0.035,
    0.42,
    0.4,
  );
  const third = radialField(
    x,
    y,
    0.58 + Math.sin(time * 0.07) * 0.03,
    0.04,
    0.34,
    0.24,
  );
  const horizontalFade = clamp((x - 0.04) / 0.72);
  const verticalFade = clamp((1.04 - y) / 0.82);
  const ripple = (Math.sin(x * 8.5 - y * 5.5 + time * 0.32) + 1) * 0.5;

  return clamp((first * 0.62 + second * 0.42 + third * 0.22 + ripple * 0.045) * horizontalFade * verticalFade);
}

export function OverviewAmbientAsciiBackground() {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const root = rootRef.current;
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!root || !canvas || !context) return;

    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)");

    let width = 0;
    let height = 0;
    let frame = 0;
    let lastPaint = 0;
    let primaryColor = "";
    let pointerX = -POINTER_RADIUS * 2;
    let pointerY = -POINTER_RADIUS * 2;
    let pointerActive = false;
    let trackingPointer = false;

    const drawGradient = (time: number) => {
      const firstX = width * (0.8 + Math.sin(time * 0.11) * 0.035);
      const firstY = height * (0.18 + Math.cos(time * 0.09) * 0.025);
      const first = context.createRadialGradient(
        firstX,
        firstY,
        0,
        firstX,
        firstY,
        Math.max(width * 0.62, height * 0.9),
      );
      first.addColorStop(0, primaryColor);
      first.addColorStop(0.38, primaryColor);
      first.addColorStop(1, "transparent");
      context.globalAlpha = 0.045;
      context.fillStyle = first;
      context.fillRect(0, 0, width, height);

      const secondX = width * (0.98 + Math.cos(time * 0.08) * 0.015);
      const secondY = height * (0.68 + Math.sin(time * 0.1) * 0.025);
      const second = context.createRadialGradient(
        secondX,
        secondY,
        0,
        secondX,
        secondY,
        Math.max(width * 0.48, height * 0.76),
      );
      second.addColorStop(0, primaryColor);
      second.addColorStop(0.42, primaryColor);
      second.addColorStop(1, "transparent");
      context.globalAlpha = 0.026;
      context.fillStyle = second;
      context.fillRect(0, 0, width, height);
    };

    const draw = (now: number, staticFrame = false) => {
      if (!width || !height) return;

      const time = staticFrame ? 0 : now / 1000;
      context.clearRect(0, 0, width, height);
      drawGradient(time);

      context.fillStyle = primaryColor;
      context.font = '500 9px ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace';
      context.textAlign = "center";
      context.textBaseline = "middle";

      const columns = Math.ceil(width / CELL_SIZE);
      const rows = Math.ceil(height / CELL_SIZE);

      for (let row = 0; row < rows; row += 1) {
        const y = row * CELL_SIZE + CELL_SIZE / 2;
        const normalizedY = y / height;
        const drift = staticFrame ? 0 : Math.sin(time * 0.18 + row * 0.31) * 1.8;

        for (let column = 0; column < columns; column += 1) {
          const x = column * CELL_SIZE + CELL_SIZE / 2 + drift;
          const normalizedX = x / width;
          const field = ambientField(normalizedX, normalizedY, time);
          if (field < 0.11) continue;

          const texture = (Math.sin(column * 1.73 + row * 0.91 + time * 0.42) + 1) * 0.5;
          const distance = pointerActive ? Math.hypot(x - pointerX, y - pointerY) : POINTER_RADIUS * 2;
          const pointerInfluence = clamp(1 - distance / POINTER_RADIUS);
          const value = clamp(field * 0.82 + texture * 0.14 + pointerInfluence * 0.12);
          if (value < 0.17) continue;

          const characterIndex = Math.min(
            CHARACTERS.length - 1,
            Math.floor(value * CHARACTERS.length + pointerInfluence * texture),
          );
          const character = CHARACTERS[characterIndex];
          if (character === " ") continue;

          context.globalAlpha = 0.014 + value * 0.074 + pointerInfluence * 0.022;
          context.fillText(character, x, y);
        }
      }

      context.globalAlpha = 1;
    };

    const resize = () => {
      const bounds = root.getBoundingClientRect();
      width = Math.max(1, Math.round(bounds.width));
      height = Math.max(1, Math.round(bounds.height));
      const pixelRatio = Math.min(window.devicePixelRatio || 1, 1.5);

      canvas.width = Math.round(width * pixelRatio);
      canvas.height = Math.round(height * pixelRatio);
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      primaryColor = window.getComputedStyle(canvas).color;
      draw(performance.now(), reducedMotion.matches);
    };

    const animate = (now: number) => {
      if (now - lastPaint >= FRAME_INTERVAL) {
        draw(now);
        lastPaint = now;
      }
      frame = window.requestAnimationFrame(animate);
    };

    const stopAnimation = () => {
      window.cancelAnimationFrame(frame);
      frame = 0;
    };

    const syncAnimation = () => {
      stopAnimation();
      if (reducedMotion.matches || document.hidden) {
        draw(performance.now(), true);
        return;
      }
      lastPaint = 0;
      frame = window.requestAnimationFrame(animate);
    };

    const resetPointer = () => {
      pointerActive = false;
      pointerX = -POINTER_RADIUS * 2;
      pointerY = -POINTER_RADIUS * 2;
    };

    const onPointerMove = (event: PointerEvent) => {
      if (event.pointerType !== "mouse") {
        resetPointer();
        return;
      }

      const bounds = root.getBoundingClientRect();
      const localX = event.clientX - bounds.left;
      const localY = event.clientY - bounds.top;
      pointerActive = localX >= 0 && localX <= bounds.width && localY >= 0 && localY <= bounds.height;

      if (pointerActive) {
        pointerX = localX;
        pointerY = localY;
      }
    };

    const syncPointerTracking = () => {
      if (trackingPointer) {
        window.removeEventListener("pointermove", onPointerMove);
        trackingPointer = false;
      }

      resetPointer();
      if (!reducedMotion.matches && finePointer.matches && !document.hidden) {
        window.addEventListener("pointermove", onPointerMove, { passive: true });
        trackingPointer = true;
      }
    };

    const syncPreferences = () => {
      syncPointerTracking();
      syncAnimation();
    };

    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(root);
    resize();
    syncPreferences();

    reducedMotion.addEventListener("change", syncPreferences);
    finePointer.addEventListener("change", syncPointerTracking);
    document.addEventListener("visibilitychange", syncPreferences);
    window.addEventListener("blur", resetPointer);

    return () => {
      stopAnimation();
      resizeObserver.disconnect();
      if (trackingPointer) window.removeEventListener("pointermove", onPointerMove);
      reducedMotion.removeEventListener("change", syncPreferences);
      finePointer.removeEventListener("change", syncPointerTracking);
      document.removeEventListener("visibilitychange", syncPreferences);
      window.removeEventListener("blur", resetPointer);
    };
  }, []);

  return (
    <div
      ref={rootRef}
      aria-hidden="true"
      className="pointer-events-none absolute right-0 top-14 z-0 hidden h-[440px] w-[72%] min-w-[640px] overflow-hidden md:block"
    >
      <canvas
        ref={canvasRef}
        className="block h-full w-full text-[var(--yak-color-primary)]"
      />
    </div>
  );
}
