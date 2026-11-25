import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useRef,
  type RefObject,
} from "react";

import "./yak-aurora.css";

const PULL_DURATION_MS = 920;

const VERTEX_SHADER = [
  "#version 300 es",
  "void main() {",
  "  vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));",
  "  gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);",
  "}",
].join("\n");

const FRAGMENT_SHADER = [
  "#version 300 es",
  "precision highp float;",
  "uniform sampler2D uSource;",
  "uniform vec2 uSize;",
  "uniform vec2 uCenter;",
  "uniform float uReach;",
  "uniform float uTime;",
  "uniform float uDot;",
  "out vec4 color;",
  "",
  "const vec3 SEED = vec3(28.0, 78.0, 190.0) / 255.0;",
  "const float FIRST = 0.3;",
  "const float FALL = 2.2;",
  "const float SWIRL = 0.9;",
  "const float SMEAR = 0.09;",
  "const float BLUR = 14.0;",
  "const float BLEACH = 0.35;",
  "",
  "float landing(float distanceFromCenter) {",
  "  return min(1.0, FIRST + (1.0 - FIRST) * distanceFromCenter / uReach);",
  "}",
  "",
  "float fallen(float distanceFromCenter) {",
  "  return pow(min(1.0, uTime / landing(distanceFromCenter)), FALL);",
  "}",
  "",
  "float radiusAt(float distanceFromCenter) {",
  "  return distanceFromCenter * (1.0 - fallen(distanceFromCenter));",
  "}",
  "",
  "vec4 tap(vec2 point, vec2 dx, vec2 dy) {",
  "  vec2 uv = point / uSize;",
  "  vec2 feather = vec2(0.08, 0.22) * sqrt(uTime) + 0.0001;",
  "  vec2 edge = smoothstep(vec2(0.0), feather, min(uv, 1.0 - uv));",
  "  return textureGrad(uSource, clamp(uv, 0.0, 1.0), dx, dy) * edge.x * edge.y;",
  "}",
  "",
  "void main() {",
  "  vec2 point = vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y);",
  "  vec2 offset = point - uCenter;",
  "  float distanceFromCenter = length(offset);",
  "  float low = 0.0;",
  "  float high = 3.0 * uReach;",
  "  for (int index = 0; index < 18; index++) {",
  "    float middle = 0.5 * (low + high);",
  "    if (radiusAt(middle) < distanceFromCenter) low = middle;",
  "    else high = middle;",
  "  }",
  "",
  "  float sourceDistance = 0.5 * (low + high);",
  "  float stretch = max(0.02, radiusAt(sourceDistance + 0.5) - radiusAt(max(0.0, sourceDistance - 0.5)));",
  "  float squeeze = sourceDistance / max(distanceFromCenter, 0.0001);",
  "  float fall = fallen(sourceDistance);",
  "  vec2 direction = distanceFromCenter > 0.0001 ? offset / distanceFromCenter : vec2(0.0, -1.0);",
  "  float twist = SWIRL * fall;",
  "  direction = mat2(cos(twist), -sin(twist), sin(twist), cos(twist)) * direction;",
  "  vec2 from = uCenter + direction * sourceDistance;",
  "  float soften = 1.0 + 3.0 * uTime + BLUR * fall * fall;",
  "  vec2 dx = dFdx(from / uSize) * soften;",
  "  vec2 dy = dFdy(from / uSize) * soften;",
  "",
  "  float land = landing(sourceDistance);",
  "  float speed = uTime < land",
  "    ? sourceDistance * FALL * pow(uTime / land, FALL - 1.0) / land",
  "    : 0.0;",
  "  float trail = speed * SMEAR / stretch;",
  "  vec4 sum = vec4(0.0);",
  "  for (int index = 0; index < 8; index++) {",
  "    sum += tap(from + direction * trail * float(index) / 7.0, dx, dy);",
  "  }",
  "",
  "  vec4 light = sum / 8.0;",
  "  vec4 pulled = vec4(0.0);",
  "  if (light.a > 0.0001) {",
  "    float layers = sqrt(clamp(squeeze / stretch, 0.25, 4.0));",
  "    float alpha = 1.0 - pow(1.0 - min(light.a, 0.999), layers);",
  "    vec3 hue = light.rgb / light.a;",
  "    hue = mix(hue, vec3(dot(hue, vec3(0.299, 0.587, 0.114))), BLEACH * fall);",
  "    pulled = vec4(hue * alpha, alpha);",
  "  }",
  "",
  "  pulled *= smoothstep(1.0, 3.0, distanceFromCenter);",
  "  float spread = uDot * (1.0 + (1.0 - uTime));",
  "  float core = 0.85 * uTime * exp(-distanceFromCenter * distanceFromCenter / (spread * spread));",
  "  color = vec4(SEED * core, core) + pulled * (1.0 - core);",
  "}",
].join("\n");

type PullRenderer = {
  draw: (progress: number) => void;
  dispose: () => void;
};

type ActiveMotion = {
  frame: number;
  cancel: () => void;
};

export interface YakAuroraHandle {
  play: () => Promise<void>;
  cancel: () => void;
}

interface YakAuroraProps {
  targetRef: RefObject<HTMLElement>;
}

interface AuroraLayer {
  amplitude: number;
  offset: number;
  phase: number;
  speed: number;
  width: number;
  blur: number;
  alpha: number;
}

const AURORA_LAYERS: AuroraLayer[] = [
  { amplitude: 0.047, offset: -0.018, phase: 0.2, speed: 0.82, width: 28, blur: 34, alpha: 0.25 },
  { amplitude: 0.033, offset: 0.012, phase: 2.4, speed: -0.61, width: 15, blur: 22, alpha: 0.34 },
  { amplitude: 0.021, offset: 0.038, phase: 4.1, speed: 1.04, width: 5, blur: 12, alpha: 0.48 },
];

function drawAurora(ctx: CanvasRenderingContext2D, width: number, height: number, time: number) {
  ctx.clearRect(0, 0, width, height);
  ctx.save();
  ctx.globalCompositeOperation = "lighter";
  ctx.lineCap = "round";
  ctx.lineJoin = "round";

  const startX = width * 0.035;
  const endX = width * 0.965;

  for (const layer of AURORA_LAYERS) {
    const points: Array<{ x: number; y: number }> = [];
    for (let index = 0; index <= 96; index += 1) {
      const progress = index / 96;
      const envelope = Math.pow(Math.sin(Math.PI * progress), 0.72);
      const x = startX + (endX - startX) * progress;
      const baseY = height * (0.54 + layer.offset + progress * 0.055);
      const broadWave =
        Math.sin(progress * 7.4 + time * layer.speed + layer.phase) *
        height *
        layer.amplitude *
        envelope;
      const fineWave =
        Math.sin(progress * 17.5 - time * layer.speed * 0.58 + layer.phase) *
        height *
        0.009 *
        envelope;
      points.push({ x, y: baseY + broadWave + fineWave });
    }

    const gradient = ctx.createLinearGradient(startX, 0, endX, 0);
    gradient.addColorStop(0, "rgba(43, 100, 255, 0)");
    gradient.addColorStop(0.16, "rgba(44, 155, 255, " + layer.alpha + ")");
    gradient.addColorStop(0.42, "rgba(45, 218, 242, " + layer.alpha * 0.9 + ")");
    gradient.addColorStop(0.7, "rgba(108, 116, 255, " + layer.alpha * 0.86 + ")");
    gradient.addColorStop(0.92, "rgba(174, 105, 255, " + layer.alpha * 0.68 + ")");
    gradient.addColorStop(1, "rgba(43, 100, 255, 0)");

    ctx.beginPath();
    points.forEach((point, index) => {
      if (index === 0) ctx.moveTo(point.x, point.y);
      else ctx.lineTo(point.x, point.y);
    });
    ctx.strokeStyle = gradient;
    ctx.lineWidth = layer.width;
    ctx.shadowColor = "rgba(75, 155, 255, 0.76)";
    ctx.shadowBlur = layer.blur;
    ctx.globalAlpha = layer.alpha;
    ctx.stroke();

    ctx.beginPath();
    points.forEach((point, index) => {
      if (index === 0) ctx.moveTo(point.x, point.y);
      else ctx.lineTo(point.x, point.y);
    });
    ctx.strokeStyle = "rgba(224, 249, 255, 0.92)";
    ctx.lineWidth = Math.max(1, layer.width * 0.12);
    ctx.shadowBlur = Math.max(2, layer.blur * 0.35);
    ctx.globalAlpha = layer.alpha * 0.72;
    ctx.stroke();
  }

  ctx.restore();
}

function compileShader(
  gl: WebGL2RenderingContext,
  type: number,
  source: string,
): WebGLShader | null {
  const shader = gl.createShader(type);
  if (!shader) return null;

  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    gl.deleteShader(shader);
    return null;
  }
  return shader;
}

function createPullRenderer(
  canvas: HTMLCanvasElement,
  source: HTMLCanvasElement,
  width: number,
  height: number,
  dpr: number,
  centerX: number,
  centerY: number,
): PullRenderer | null {
  const gl = canvas.getContext("webgl2", {
    alpha: true,
    antialias: false,
    premultipliedAlpha: true,
  });
  if (!gl) return null;

  const vertexShader = compileShader(gl, gl.VERTEX_SHADER, VERTEX_SHADER);
  const fragmentShader = compileShader(gl, gl.FRAGMENT_SHADER, FRAGMENT_SHADER);
  if (!vertexShader || !fragmentShader) {
    if (vertexShader) gl.deleteShader(vertexShader);
    if (fragmentShader) gl.deleteShader(fragmentShader);
    return null;
  }

  const program = gl.createProgram();
  if (!program) {
    gl.deleteShader(vertexShader);
    gl.deleteShader(fragmentShader);
    return null;
  }

  gl.attachShader(program, vertexShader);
  gl.attachShader(program, fragmentShader);
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    gl.deleteProgram(program);
    gl.deleteShader(vertexShader);
    gl.deleteShader(fragmentShader);
    return null;
  }

  const texture = gl.createTexture();
  if (!texture) {
    gl.deleteProgram(program);
    gl.deleteShader(vertexShader);
    gl.deleteShader(fragmentShader);
    return null;
  }

  gl.useProgram(program);
  gl.activeTexture(gl.TEXTURE0);
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, 1);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, source);

  gl.uniform1i(gl.getUniformLocation(program, "uSource"), 0);
  gl.uniform2f(gl.getUniformLocation(program, "uSize"), width * dpr, height * dpr);
  gl.uniform2f(gl.getUniformLocation(program, "uCenter"), centerX * dpr, centerY * dpr);
  gl.uniform1f(
    gl.getUniformLocation(program, "uReach"),
    Math.max(1, Math.hypot(Math.max(centerX, width - centerX), Math.max(centerY, height - centerY)) * dpr),
  );
  gl.uniform1f(gl.getUniformLocation(program, "uDot"), 3.5 * dpr);
  gl.enable(gl.BLEND);
  gl.blendFunc(gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
  gl.clearColor(0, 0, 0, 0);

  return {
    draw(progress) {
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.uniform1f(gl.getUniformLocation(program, "uTime"), progress);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
    },
    dispose() {
      gl.deleteTexture(texture);
      gl.deleteProgram(program);
      gl.deleteShader(vertexShader);
      gl.deleteShader(fragmentShader);
    },
  };
}

const YakAurora = forwardRef<YakAuroraHandle, YakAuroraProps>(function YakAurora(
  { targetRef },
  forwardedRef,
) {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const sourceCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const pullCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const activeMotionRef = useRef<ActiveMotion | null>(null);

  const reset = useCallback(() => {
    const host = hostRef.current;
    const source = sourceCanvasRef.current;
    const pull = pullCanvasRef.current;
    if (host) {
      host.dataset.active = "false";
      delete host.dataset.renderer;
    }
    if (source) {
      source.style.opacity = "";
      source.style.transform = "";
      source.style.filter = "";
    }
    if (pull) pull.style.opacity = "";
  }, []);

  const cancel = useCallback(() => {
    const active = activeMotionRef.current;
    activeMotionRef.current = null;
    if (active) active.cancel();
    else reset();
  }, [reset]);

  const play = useCallback(() => {
    cancel();

    const host = hostRef.current;
    const source = sourceCanvasRef.current;
    const pull = pullCanvasRef.current;
    const target = targetRef.current;
    if (!host || !source || !pull || !target || document.hidden) return Promise.resolve();
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return Promise.resolve();

    const width = Math.max(1, window.innerWidth);
    const height = Math.max(1, window.innerHeight);
    const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
    const targetBounds = target.getBoundingClientRect();
    if (!targetBounds.width || !targetBounds.height) return Promise.resolve();

    const centerX = Math.max(0, Math.min(width, targetBounds.left + targetBounds.width / 2));
    const centerY = Math.max(0, Math.min(height, targetBounds.top + targetBounds.height / 2));

    source.width = Math.round(width * dpr);
    source.height = Math.round(height * dpr);
    pull.width = Math.round(width * dpr);
    pull.height = Math.round(height * dpr);

    const sourceContext = source.getContext("2d");
    if (!sourceContext) return Promise.resolve();
    sourceContext.setTransform(dpr, 0, 0, dpr, 0, 0);
    drawAurora(sourceContext, width, height, performance.now() / 1000);

    const renderer = createPullRenderer(pull, source, width, height, dpr, centerX, centerY);
    host.dataset.active = "true";
    if (renderer) {
      host.dataset.renderer = "webgl2";
      source.style.opacity = "0";
      pull.style.opacity = "1";
    } else {
      host.dataset.renderer = "transform";
      source.style.opacity = "1";
      pull.style.opacity = "0";
    }

    return new Promise<void>((resolve) => {
      let settled = false;
      let motion: ActiveMotion;

      const cleanup = () => {
        renderer?.dispose();
        window.removeEventListener("resize", handleResize);
        document.removeEventListener("visibilitychange", handleVisibilityChange);
        reset();
      };

      const finish = () => {
        if (settled) return;
        settled = true;
        if (activeMotionRef.current === motion) activeMotionRef.current = null;
        cleanup();
        resolve();
      };

      const stop = () => {
        if (settled) return;
        settled = true;
        window.cancelAnimationFrame(motion.frame);
        cleanup();
        resolve();
      };

      const handleResize = () => cancel();
      const handleVisibilityChange = () => {
        if (document.hidden) cancel();
      };

      motion = { frame: 0, cancel: stop };
      activeMotionRef.current = motion;
      window.addEventListener("resize", handleResize, { once: true });
      document.addEventListener("visibilitychange", handleVisibilityChange);

      const startedAt = performance.now();
      const tick = (now: number) => {
        if (activeMotionRef.current !== motion) return;
        const progress = Math.min(1, Math.max(0, (now - startedAt) / PULL_DURATION_MS));

        if (renderer) {
          renderer.draw(progress);
        } else {
          const eased = 1 - Math.pow(1 - progress, 3);
          const scale = Math.max(0.015, 1 - eased);
          source.style.transform =
            "translate3d(" +
            (centerX - width / 2) +
            "px, " +
            (centerY - height / 2) +
            "px, 0) scale(" +
            scale +
            ") rotate(" +
            eased * 0.5 +
            "rad)";
          source.style.opacity = String(1 - eased * 0.72);
          source.style.filter = "blur(" + eased * 4 + "px)";
        }

        if (progress >= 1) {
          finish();
          return;
        }
        motion.frame = window.requestAnimationFrame(tick);
      };

      motion.frame = window.requestAnimationFrame(tick);
    });
  }, [cancel, reset, targetRef]);

  useImperativeHandle(forwardedRef, () => ({ play, cancel }), [play, cancel]);

  useEffect(() => () => cancel(), [cancel]);

  return (
    <div ref={hostRef} className="yak-aurora" aria-hidden="true" data-active="false">
      <canvas ref={sourceCanvasRef} className="yak-aurora__canvas yak-aurora__source" />
      <canvas ref={pullCanvasRef} className="yak-aurora__canvas yak-aurora__pull" />
    </div>
  );
});

YakAurora.displayName = "YakAurora";

export default YakAurora;
