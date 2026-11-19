import { useEffect, useRef } from "react";

export type ParticleTransitionOrigin = {
  /** Horizontal position, normalized to the canvas width (0–1). */
  x: number;
  /** Vertical position, normalized to the canvas height (0–1). */
  y: number;
};

export type ParticleTransitionProps = {
  /** Starts one animation when changed from false to true. Set false to stop it. */
  active: boolean;
  /** Normalized particle emission point. */
  origin?: ParticleTransitionOrigin;
  /** Horizontal direction of the particle stream. */
  direction?: "left" | "right" | "both";
  /** Number of particles emitted for each run. */
  particleCount?: number;
  /** Animation duration in milliseconds. */
  duration?: number;
  /** Called once after the animation finishes. */
  onComplete?: () => void;
  /** Optional classes for the absolute overlay wrapper. */
  className?: string;
};

type Particle = {
  x: number;
  y: number;
  previousX: number;
  previousY: number;
  vx: number;
  vy: number;
  radius: number;
  opacity: number;
  phase: number;
  frequency: number;
  delay: number;
  life: number;
  color: string;
  direction: number;
};

const DEFAULT_ORIGIN: ParticleTransitionOrigin = { x: 0.42, y: 0.5 };
const DEFAULT_PARTICLE_COUNT = 620;
const DEFAULT_DURATION = 1500;
const MAX_PARTICLE_COUNT = 1800;
const MAX_DEVICE_PIXEL_RATIO = 2;
const TAU = Math.PI * 2;

const PARTICLE_COLORS = [
  "#b9cef7",
  "#c9b8ef",
  "#e9bfd6",
  "#a9d8d1",
  "#e8d5aa",
  "#a9c5e9",
  "#d6c5ed",
  "#b8d7c4",
  "#edc4b8",
];

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function randomBetween(min: number, max: number) {
  return min + Math.random() * (max - min);
}

function randomGaussian() {
  const first = Math.max(Math.random(), 0.0001);
  const second = Math.random();
  return Math.sqrt(-2 * Math.log(first)) * Math.cos(TAU * second);
}

function createParticles(
  width: number,
  height: number,
  originX: number,
  originY: number,
  count: number,
  direction: "left" | "right" | "both",
  durationSeconds: number,
): Particle[] {
  const centerX = width * originX;
  const centerY = height * originY;
  const verticalSpread = Math.max(height * 0.24, 42);

  return Array.from({ length: count }, () => {
    const particleDirection =
      direction === "both"
        ? Math.random() > 0.5 ? 1 : -1
        : direction === "left" ? -1 : 1;

    return {
    x: centerX + randomGaussian() * 2.6,
    y: centerY + randomGaussian() * 3.4,
    previousX: centerX,
    previousY: centerY,
    vx: (particleDirection * width * randomBetween(0.48, 0.82)) / durationSeconds,
    vy: (randomGaussian() * verticalSpread * 0.92) / durationSeconds,
    radius: Math.random() > 0.9 ? randomBetween(1.5, 2.2) : randomBetween(0.62, 1.55),
    opacity: randomBetween(0.34, 0.82),
    phase: randomBetween(0, TAU),
    frequency: randomBetween(1.1, 3.4),
    delay: randomBetween(0, 0.16),
    life: randomBetween(0.68, 1),
    color: PARTICLE_COLORS[Math.floor(Math.random() * PARTICLE_COLORS.length)],
    direction: particleDirection,
    };
  });
}

/**
 * A one-shot canvas particle stream. Render it inside a positioned container;
 * toggle active from false to true to play it again.
 */
export function ParticleTransition({
  active,
  origin = DEFAULT_ORIGIN,
  direction = "right",
  particleCount = DEFAULT_PARTICLE_COUNT,
  duration = DEFAULT_DURATION,
  onComplete,
  className = "",
}: ParticleTransitionProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const onCompleteRef = useRef(onComplete);
  onCompleteRef.current = onComplete;

  const originX = Number.isFinite(origin.x) ? clamp(origin.x, 0, 1) : DEFAULT_ORIGIN.x;
  const originY = Number.isFinite(origin.y) ? clamp(origin.y, 0, 1) : DEFAULT_ORIGIN.y;

  useEffect(() => {
    if (!active) return;

    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context) {
      onCompleteRef.current?.();
      return;
    }

    if (
      document.visibilityState === "hidden" ||
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ) {
      onCompleteRef.current?.();
      return;
    }

    const safeDuration = Number.isFinite(duration)
      ? clamp(duration, 600, 5000)
      : DEFAULT_DURATION;
    const durationSeconds = safeDuration / 1000;
    const safeParticleCount = Number.isFinite(particleCount)
      ? clamp(Math.round(particleCount), 80, MAX_PARTICLE_COUNT)
      : DEFAULT_PARTICLE_COUNT;

    let width = 0;
    let height = 0;
    let pixelRatio = 1;
    let frameId = 0;
    let disposed = false;
    let lastFrameTime = 0;
    let particles: Particle[] = [];

    const resizeCanvas = () => {
      const bounds = canvas.getBoundingClientRect();
      const nextWidth = Math.max(bounds.width, 1);
      const nextHeight = Math.max(bounds.height, 1);
      const nextPixelRatio = Math.min(
        window.devicePixelRatio || 1,
        MAX_DEVICE_PIXEL_RATIO,
      );

      if (width > 0 && height > 0 && particles.length > 0) {
        const scaleX = nextWidth / width;
        const scaleY = nextHeight / height;

        particles = particles.map((particle) => ({
          ...particle,
          x: particle.x * scaleX,
          y: particle.y * scaleY,
          previousX: particle.previousX * scaleX,
          previousY: particle.previousY * scaleY,
          vx: particle.vx * scaleX,
          vy: particle.vy * scaleY,
        }));
      }

      width = nextWidth;
      height = nextHeight;
      pixelRatio = nextPixelRatio;
      canvas.width = Math.round(width * pixelRatio);
      canvas.height = Math.round(height * pixelRatio);
      context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
      context.lineCap = "round";
    };

    resizeCanvas();
    particles = createParticles(
      width,
      height,
      originX,
      originY,
      safeParticleCount,
      direction,
      durationSeconds,
    );

    const resizeObserver =
      typeof ResizeObserver === "undefined" ? null : new ResizeObserver(resizeCanvas);

    if (resizeObserver) {
      resizeObserver.observe(canvas);
    } else {
      window.addEventListener("resize", resizeCanvas);
    }

    let completed = false;

    const finish = () => {
      if (completed) return;

      completed = true;
      window.cancelAnimationFrame(frameId);
      resizeObserver?.disconnect();

      if (!resizeObserver) {
        window.removeEventListener("resize", resizeCanvas);
      }

      document.removeEventListener("visibilitychange", handleVisibilityChange);
      context.clearRect(0, 0, width, height);
      context.globalAlpha = 1;
      context.globalCompositeOperation = "source-over";
      onCompleteRef.current?.();
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "hidden") {
        finish();
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);

    const startTime = performance.now();
    lastFrameTime = startTime;

    const renderFrame = (now: number) => {
      if (disposed || completed) return;

      const elapsed = now - startTime;
      if (elapsed >= safeDuration) {
        finish();
        return;
      }

      const deltaSeconds = Math.min(now - lastFrameTime, 40) / 1000;
      const elapsedSeconds = elapsed / 1000;
      const progress = elapsed / safeDuration;
      lastFrameTime = now;

      context.clearRect(0, 0, width, height);
      context.globalCompositeOperation = "lighter";

      particles.forEach((particle) => {
        const particleProgress =
          (progress - particle.delay) / Math.max(0.01, particle.life - particle.delay);

        if (particleProgress < 0 || particleProgress > 1) return;

        const previousX = particle.x;
        const previousY = particle.y;
        const field =
          Math.sin((particle.x / width) * Math.PI * 3.2 + elapsedSeconds * 1.25 + particle.phase) *
            0.62 +
          Math.cos((particle.y / height) * Math.PI * 2.4 - elapsedSeconds * 0.9 + particle.phase * 0.6) *
            0.38;
        const fieldAcceleration = (height * 0.18) / (durationSeconds * durationSeconds);

        particle.vy += field * fieldAcceleration * deltaSeconds;
        particle.vy +=
          (Math.sin(elapsedSeconds * particle.frequency + particle.phase) *
            height *
            0.025 *
            deltaSeconds) /
          (durationSeconds * durationSeconds);
        particle.vy *= Math.pow(0.992, deltaSeconds * 60);
        particle.vx +=
          (particle.direction * width * 0.02 * deltaSeconds) /
          (durationSeconds * durationSeconds);
        particle.x += particle.vx * deltaSeconds;
        particle.y += particle.vy * deltaSeconds;

        const fadeIn = clamp(particleProgress / 0.12, 0, 1);
        const fadeOut = clamp((1 - particleProgress) / 0.2, 0, 1);
        const opacity = fadeIn * fadeOut * particle.opacity;

        if (opacity <= 0.005) return;

        context.globalAlpha = opacity * 0.24;
        context.strokeStyle = particle.color;
        context.lineWidth = Math.max(0.45, particle.radius * 0.72);
        context.beginPath();
        context.moveTo(previousX, previousY);
        context.lineTo(particle.x, particle.y);
        context.stroke();

        context.globalAlpha = opacity;
        context.fillStyle = particle.color;
        context.beginPath();
        context.arc(particle.x, particle.y, particle.radius, 0, TAU);
        context.fill();
      });

      context.globalAlpha = 1;
      context.globalCompositeOperation = "source-over";
      frameId = window.requestAnimationFrame(renderFrame);
    };

    frameId = window.requestAnimationFrame(renderFrame);

    return () => {
      disposed = true;
      window.cancelAnimationFrame(frameId);
      resizeObserver?.disconnect();

      if (!resizeObserver) {
        window.removeEventListener("resize", resizeCanvas);
      }

      document.removeEventListener("visibilitychange", handleVisibilityChange);
      context.clearRect(0, 0, width, height);
      context.globalAlpha = 1;
      context.globalCompositeOperation = "source-over";
    };
  }, [active, direction, duration, originX, originY, particleCount]);

  return (
    <div
      aria-hidden="true"
      className={`pointer-events-none absolute inset-0 z-40 overflow-hidden ${className}`}
    >
      <canvas ref={canvasRef} className="block size-full" />
    </div>
  );
}

export default ParticleTransition;
