import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  type RefObject,
} from "react";

import "./magic-patterns-particle-transition.css";

// Port of Magic Patterns' original Canvas particle transition captured in HAR module 83473.
const PARTICLE_BURST_DURATION_MS = 1300;
const PARTICLE_FLIGHT_MIN_MS = 600;
const PARTICLE_FLIGHT_MAX_MS = 950;
const PARTICLE_COUNT = 220;
const PARTICLE_COLORS = ["#7cd27f", "#51b6f8", "#a390cb", "#f3a6d8", "#ffd27a"];
const PARTICLE_COLOR_WEIGHTS = [4, 4, 4, 1, 1];
const TOTAL_ANIMATION_MS =
  PARTICLE_BURST_DURATION_MS + PARTICLE_FLIGHT_MAX_MS + 100;

const MASK_IMAGE =
  "linear-gradient(90deg, transparent 0%, transparent 42%, #000 52%, #000 100%)";
const MASK_SIZE = "240% 100%";
const MASK_PROPERTIES = ["image", "size", "repeat", "position"] as const;

interface Point {
  x: number;
  y: number;
}

interface Bounds {
  left: number;
  right: number;
  top: number;
  bottom: number;
}

interface Particle {
  born: number;
  flight: number;
  across: number;
  down: number;
  lift: Point;
  neckX: number;
  neckDrift: number;
  destination: Point;
  color: string;
  size: number;
  wobble: number;
  wobbleTurns: number;
  wobblePhase: number;
  flips: number;
  flipPhase: number;
}

export interface MagicPatternsParticleTransitionHandle {
  play: () => Promise<void>;
  cancel: () => void;
}

interface MagicPatternsParticleTransitionProps {
  fromRef: RefObject<HTMLElement>;
  toRef: RefObject<HTMLElement>;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function randomBetween(min: number, max: number) {
  return min + Math.random() * (max - min);
}

function pickParticleColor() {
  const totalWeight = PARTICLE_COLOR_WEIGHTS.reduce((sum, weight) => sum + weight, 0);
  let selection = Math.random() * totalWeight;

  for (let index = 0; index < PARTICLE_COLORS.length; index += 1) {
    selection -= PARTICLE_COLOR_WEIGHTS[index];
    if (selection <= 0) return PARTICLE_COLORS[index];
  }

  return PARTICLE_COLORS[0];
}

function getDestinationBounds(
  target: HTMLElement | null,
  viewportWidth: number,
  viewportHeight: number,
): Bounds {
  const rect = target?.getBoundingClientRect();

  if (!target || !rect || rect.width === 0 || rect.height === 0) {
    return {
      left: viewportWidth - 380,
      right: viewportWidth,
      top: 0,
      bottom: viewportHeight,
    };
  }

  const firstChild = target.firstElementChild;
  const railWidth =
    (firstChild instanceof HTMLElement ? firstChild.offsetWidth : 0) || 380;

  return {
    left: rect.right - railWidth,
    right: rect.right,
    top: rect.top,
    bottom: rect.bottom,
  };
}

function getSourceBounds(
  source: HTMLElement | null,
  viewportWidth: number,
  viewportHeight: number,
): Bounds {
  const rect = source?.getBoundingClientRect();

  if (!rect || rect.width === 0 || rect.height === 0) {
    return {
      left: viewportWidth * 0.3,
      right: viewportWidth * 0.6,
      top: viewportHeight * 0.6 - 28,
      bottom: viewportHeight * 0.6 + 28,
    };
  }

  return {
    left: rect.left,
    right: rect.right,
    top: rect.top,
    bottom: rect.bottom,
  };
}

function createParticle(destination: Bounds): Particle {
  const across = Math.random();
  const destinationHeight = destination.bottom - destination.top;
  const birthPosition = clamp((across + 0.12) / 1.24, 0, 1);
  const born =
    (Math.acos(1 - 2 * birthPosition) / Math.PI) * PARTICLE_BURST_DURATION_MS +
    randomBetween(-40, 60);

  return {
    born,
    flight: randomBetween(PARTICLE_FLIGHT_MIN_MS, PARTICLE_FLIGHT_MAX_MS),
    across,
    down: Math.random(),
    lift: {
      x: randomBetween(20, 110),
      y: randomBetween(-110, 110),
    },
    neckX: destination.left + randomBetween(-24, 12),
    neckDrift: randomBetween(-16, 16),
    destination: {
      x: randomBetween(destination.left + 28, destination.right - 28),
      y: destination.top + randomBetween(0.12 * destinationHeight, 0.8 * destinationHeight),
    },
    color: pickParticleColor(),
    size: randomBetween(1.6, 4.2),
    wobble: randomBetween(4, 14),
    wobbleTurns: randomBetween(2, 4),
    wobblePhase: randomBetween(0, 2 * Math.PI),
    flips: randomBetween(3, 7),
    flipPhase: randomBetween(0, 2 * Math.PI),
  };
}

function cubicBezierPoint(
  start: Point,
  controlOne: Point,
  controlTwo: Point,
  end: Point,
  progress: number,
): Point {
  const inverse = 1 - progress;
  const inverseSquared = inverse * inverse;
  const progressSquared = progress * progress;
  const startWeight = inverseSquared * inverse;
  const controlOneWeight = 3 * inverseSquared * progress;
  const controlTwoWeight = 3 * inverse * progressSquared;
  const endWeight = progressSquared * progress;

  return {
    x:
      startWeight * start.x +
      controlOneWeight * controlOne.x +
      controlTwoWeight * controlTwo.x +
      endWeight * end.x,
    y:
      startWeight * start.y +
      controlOneWeight * controlOne.y +
      controlTwoWeight * controlTwo.y +
      endWeight * end.y,
  };
}

function drawParticle(
  context: CanvasRenderingContext2D,
  particle: Particle,
  elapsed: number,
  source: Bounds,
  destination: Bounds,
) {
  const progress = (elapsed - particle.born) / particle.flight;
  if (progress <= 0 || progress >= 1) return;

  const eased =
    progress < 0.5
      ? 4 * progress * progress * progress
      : 1 - Math.pow(-2 * progress + 2, 3) / 2;

  const start = {
    x: source.left + (source.right - source.left) * particle.across,
    y: source.top + 4 + (source.bottom - source.top - 8) * particle.down,
  };
  const destinationCenterY =
    destination.top + (destination.bottom - destination.top) * 0.45;
  const controlOne = {
    x: start.x + particle.lift.x,
    y: start.y + particle.lift.y,
  };
  const controlTwo = {
    x: particle.neckX,
    y:
      start.y +
      (destinationCenterY - start.y) * 0.6 +
      particle.neckDrift,
  };

  const point = cubicBezierPoint(
    start,
    controlOne,
    controlTwo,
    particle.destination,
    eased,
  );
  const nextPoint = cubicBezierPoint(
    start,
    controlOne,
    controlTwo,
    particle.destination,
    Math.min(1, eased + 0.01),
  );
  const angle = Math.atan2(nextPoint.y - point.y, nextPoint.x - point.x);
  const wobble =
    Math.sin(
      progress * Math.PI * 2 * particle.wobbleTurns + particle.wobblePhase,
    ) *
    particle.wobble *
    Math.sin(progress * Math.PI);
  const x = point.x - Math.sin(angle) * wobble;
  const y = point.y + Math.cos(angle) * wobble;
  const opacity = Math.min(1, progress / 0.1) * Math.min(1, (1 - progress) / 0.22);
  const sizeScale =
    progress > 0.78 ? 1 + ((progress - 0.78) / 0.22) * 0.9 : 1;
  const flip = Math.abs(
    Math.cos(progress * Math.PI * particle.flips + particle.flipPhase),
  );

  context.save();
  context.globalAlpha = opacity;
  context.translate(x, y);
  context.rotate(angle + particle.flipPhase);
  context.shadowColor = particle.color;
  context.shadowBlur = 8;
  context.fillStyle = particle.color;
  context.beginPath();
  context.ellipse(
    0,
    0,
    particle.size * sizeScale,
    particle.size * sizeScale * (0.3 + 0.7 * flip),
    0,
    0,
    2 * Math.PI,
  );
  context.fill();
  context.restore();
}

const MagicPatternsParticleTransition = forwardRef<
  MagicPatternsParticleTransitionHandle,
  MagicPatternsParticleTransitionProps
>(function MagicPatternsParticleTransition({ fromRef, toRef }, forwardedRef) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const cleanupRef = useRef<() => void>(() => undefined);

  const cancel = useCallback(() => {
    cleanupRef.current();
  }, []);

  const play = useCallback(() => {
    cancel();

    const canvas = canvasRef.current;
    if (
      !canvas ||
      document.hidden ||
      window.matchMedia("(prefers-reduced-motion: reduce)").matches
    ) {
      return Promise.resolve();
    }

    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
    const context = canvas.getContext("2d");
    if (!context) return Promise.resolve();

    canvas.width = viewportWidth * pixelRatio;
    canvas.height = viewportHeight * pixelRatio;
    context.scale(pixelRatio, pixelRatio);

    const sourceElement = fromRef.current;
    const targetElement = toRef.current;
    const destination = getDestinationBounds(
      targetElement,
      viewportWidth,
      viewportHeight,
    );
    const particles = Array.from({ length: PARTICLE_COUNT }, () =>
      createParticle(destination),
    );

    if (sourceElement) {
      sourceElement.style.setProperty("-webkit-mask-image", MASK_IMAGE);
      sourceElement.style.setProperty("mask-image", MASK_IMAGE);
      sourceElement.style.setProperty("-webkit-mask-size", MASK_SIZE);
      sourceElement.style.setProperty("mask-size", MASK_SIZE);
      sourceElement.style.setProperty("-webkit-mask-repeat", "no-repeat");
      sourceElement.style.setProperty("mask-repeat", "no-repeat");
    }

    return new Promise<void>((resolve) => {
      let frameId = 0;
      let settled = false;
      const startedAt = performance.now();

      const cleanup = () => {
        if (settled) return;
        settled = true;
        if (frameId) window.cancelAnimationFrame(frameId);
        context.clearRect(0, 0, viewportWidth, viewportHeight);

        for (const property of MASK_PROPERTIES) {
          sourceElement?.style.removeProperty("-webkit-mask-" + property);
          sourceElement?.style.removeProperty("mask-" + property);
        }

        if (cleanupRef.current === cleanup) {
          cleanupRef.current = () => undefined;
        }
        resolve();
      };

      cleanupRef.current = cleanup;

      const tick = (now: number) => {
        if (settled) return;

        const elapsed = now - startedAt;
        const burstProgress = clamp(elapsed / PARTICLE_BURST_DURATION_MS, 0, 1);
        const maskProgress =
          -0.12 + 1.24 * ((1 - Math.cos(Math.PI * burstProgress)) / 2);

        if (sourceElement) {
          const maskPosition = ((1.128 - maskProgress) / 1.4) * 100 + "% 0";
          sourceElement.style.setProperty("-webkit-mask-position", maskPosition);
          sourceElement.style.setProperty("mask-position", maskPosition);
        }

        context.globalCompositeOperation = "destination-out";
        context.fillStyle = "rgba(0, 0, 0, 0.24)";
        context.fillRect(0, 0, viewportWidth, viewportHeight);
        context.globalCompositeOperation = "source-over";

        const source = getSourceBounds(
          sourceElement,
          viewportWidth,
          viewportHeight,
        );

        for (const particle of particles) {
          drawParticle(context, particle, elapsed, source, destination);
        }

        if (elapsed < TOTAL_ANIMATION_MS) {
          frameId = window.requestAnimationFrame(tick);
        } else {
          cleanup();
        }
      };

      frameId = window.requestAnimationFrame(tick);
    });
  }, [cancel, fromRef, toRef]);

  useImperativeHandle(forwardedRef, () => ({ play, cancel }), [play, cancel]);
  useEffect(() => () => cancel(), [cancel]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      className="magic-patterns-particle-transition"
    />
  );
});

MagicPatternsParticleTransition.displayName = "MagicPatternsParticleTransition";

export default MagicPatternsParticleTransition;
