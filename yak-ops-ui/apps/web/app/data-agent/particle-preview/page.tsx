import { useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Pause,
  Play,
  RotateCcw,
  Sparkles,
} from "lucide-react";
import { Link } from "react-router-dom";

import AppShell from "../../layout/AppShell";
import { ParticleTransition } from "../components/ParticleTransition";

type ParticleDirection = "left" | "right";

type RangeControlProps = {
  id: string;
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  displayValue: string;
  onChange: (value: number) => void;
};

function RangeControl({
  id,
  label,
  value,
  min,
  max,
  step,
  displayValue,
  onChange,
}: RangeControlProps) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-3">
        <label htmlFor={id} className="text-[12px] font-medium text-[#53615f]">
          {label}
        </label>
        <output
          htmlFor={id}
          className="text-[11px] font-semibold tabular-nums text-[#293735]"
        >
          {displayValue}
        </output>
      </div>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        className="h-1.5 w-full cursor-pointer accent-[#fe2c55]"
      />
      <div className="flex justify-between text-[10px] tabular-nums text-[#a0aaa8]">
        <span>{min.toLocaleString()}</span>
        <span>{max.toLocaleString()}</span>
      </div>
    </div>
  );
}

export function ParticlePreviewPage() {
  const [isPlaying, setIsPlaying] = useState(true);
  const [playbackId, setPlaybackId] = useState(0);
  const [particleCount, setParticleCount] = useState(620);
  const [duration, setDuration] = useState(1500);
  const [direction, setDirection] = useState<ParticleDirection>("right");
  const [originX, setOriginX] = useState(42);
  const [originY, setOriginY] = useState(50);

  function replay() {
    setIsPlaying(true);
    setPlaybackId((current) => current + 1);
  }

  return (
    <AppShell productLabel="Particle Lab" productPath="/data-agent">
      <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-y-auto bg-[#f4f6f6] text-[#1c292b]">
        <div className="mx-auto flex w-full max-w-[1440px] flex-col gap-5 px-6 py-6 max-sm:px-4 max-sm:py-4">
          <header className="flex flex-wrap items-end justify-between gap-4">
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center gap-2 text-[10px] font-semibold tracking-[0.16em] text-[#8c9996]">
                <Sparkles size={13} strokeWidth={1.8} />
                MOTION PLAYGROUND
              </div>
              <h1 className="m-0 text-[24px] font-semibold tracking-[-0.035em] text-[#1c292b]">
                Particle Lab
              </h1>
              <p className="m-0 text-[12px] leading-5 text-[#85918f]">
                调整粒子流场参数，实时观察 Canvas 动画效果。
              </p>
            </div>
            <Link
              to="/data-agent"
              className="inline-flex h-9 items-center gap-2 rounded-lg border border-[#dfe5e3] bg-white px-3 text-[12px] font-medium text-[#53615f] shadow-[0_1px_2px_rgb(20_34_35_/_3%)] transition-[background-color,border-color,color] duration-150 hover:border-[#cbd4d1] hover:bg-[#fafbfa] hover:text-[#263431] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 motion-reduce:transition-none"
            >
              <ArrowLeft size={14} strokeWidth={1.8} />
              返回 Data Agent
            </Link>
          </header>

          <div className="grid min-w-0 grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_300px]">
            <section className="flex min-w-0 flex-col overflow-hidden rounded-2xl border border-[#dfe5e3] bg-white shadow-[0_2px_8px_rgb(20_34_35_/_3%)]">
              <div className="relative h-[min(70vh,680px)] min-h-[420px] overflow-hidden bg-[#101720] max-sm:min-h-[360px]">
                <div
                  aria-hidden="true"
                  className="absolute inset-0 opacity-90"
                  style={{
                    background:
                      "radial-gradient(ellipse at 42% 50%, rgba(82, 113, 151, 0.22), transparent 34%), radial-gradient(ellipse at 78% 48%, rgba(107, 79, 135, 0.12), transparent 42%), linear-gradient(135deg, #111a24 0%, #0d131b 58%, #111923 100%)",
                  }}
                />
                <div
                  aria-hidden="true"
                  className="absolute inset-0 opacity-[0.14]"
                  style={{
                    backgroundImage:
                      "radial-gradient(rgba(204, 220, 235, 0.5) 0.7px, transparent 0.7px)",
                    backgroundSize: "24px 24px",
                  }}
                />
                <div
                  aria-hidden="true"
                  className="absolute z-10 size-5 -translate-x-1/2 -translate-y-1/2 rounded-full border border-[#d7e4f4]/25 bg-[#d7e4f4]/[0.04] shadow-[0_0_30px_rgba(171,198,235,0.16)]"
                  style={{ left: `${originX}%`, top: `${originY}%` }}
                >
                  <span className="absolute left-1/2 top-1/2 size-1 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[#dce8f7]/70" />
                </div>

                <ParticleTransition
                  key={playbackId}
                  active={isPlaying}
                  origin={{ x: originX / 100, y: originY / 100 }}
                  direction={direction}
                  particleCount={particleCount}
                  duration={duration}
                  onComplete={() => setIsPlaying(false)}
                />

                <div className="pointer-events-none absolute inset-x-5 top-5 z-50 flex items-center justify-between gap-3">
                  <div className="flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.06] px-3 py-1.5 backdrop-blur-md">
                    <span className="size-1.5 rounded-full bg-[#8fd8b1] shadow-[0_0_8px_rgba(143,216,177,0.8)]" />
                    <span className="text-[9px] font-semibold tracking-[0.14em] text-[#d7e2ed]/80">
                      CANVAS PREVIEW
                    </span>
                  </div>
                  <span className="text-[10px] tabular-nums text-white/35">
                    {particleCount.toLocaleString()} PARTICLES
                  </span>
                </div>

                <div className="pointer-events-none absolute inset-x-5 bottom-5 z-50 flex items-end justify-between gap-3">
                  <div className="flex flex-col gap-1">
                    <span className="text-[11px] font-medium text-white/75">
                      {direction === "right" ? "向右扩散" : "向左扩散"}
                    </span>
                    <span className="text-[10px] text-white/35">
                      Emission point · {originX}% / {originY}%
                    </span>
                  </div>
                  <span className="text-[10px] tabular-nums text-white/35">
                    {(duration / 1000).toFixed(1)}s
                  </span>
                </div>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[#edf0ef] px-4 py-3">
                <div className="flex items-center gap-2 text-[11px] text-[#899491]">
                  <span className="rounded-md bg-[#f1f5f3] px-2 py-1 font-medium text-[#61716c]">
                    Canvas 2D
                  </span>
                  <span>Flow field</span>
                  <span className="text-[#c2cac7]">·</span>
                  <span>One-shot animation</span>
                </div>
                <div className="flex items-center gap-2">
                  {isPlaying ? (
                    <button
                      type="button"
                      onClick={() => setIsPlaying(false)}
                      className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-[#e2e7e5] bg-white px-3 text-[11px] font-medium text-[#687571] transition-colors hover:bg-[#f7f9f8] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 motion-reduce:transition-none"
                    >
                      <Pause size={13} />
                      停止
                    </button>
                  ) : null}
                  <button
                    type="button"
                    onClick={replay}
                    className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-[#fe2c55] bg-[#fe2c55] px-3 text-[11px] font-semibold text-white shadow-[0_2px_5px_rgba(254,44,85,0.18)] transition-[background-color,box-shadow] hover:bg-[#ed244c] hover:shadow-[0_3px_8px_rgba(254,44,85,0.22)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/30 focus-visible:ring-offset-2 motion-reduce:transition-none"
                  >
                    {isPlaying ? <RotateCcw size={13} /> : <Play size={13} />}
                    {isPlaying ? "重新播放" : "播放动画"}
                  </button>
                </div>
              </div>
            </section>

            <aside className="flex min-w-0 flex-col gap-3">
              <section className="rounded-2xl border border-[#dfe5e3] bg-white p-4 shadow-[0_2px_8px_rgb(20_34_35_/_3%)]">
                <div className="mb-4 flex items-center justify-between">
                  <h2 className="m-0 text-[13px] font-semibold text-[#263431]">
                    动画参数
                  </h2>
                  <span className="rounded-md bg-[#f5f6f6] px-2 py-1 text-[9px] font-medium tracking-wide text-[#8a9692]">
                    LIVE TUNING
                  </span>
                </div>

                <div className="flex flex-col gap-5">
                  <RangeControl
                    id="particle-count"
                    label="粒子数量"
                    value={particleCount}
                    min={80}
                    max={1800}
                    step={20}
                    displayValue={particleCount.toLocaleString()}
                    onChange={setParticleCount}
                  />
                  <RangeControl
                    id="particle-duration"
                    label="动画时长"
                    value={duration}
                    min={600}
                    max={5000}
                    step={100}
                    displayValue={`${(duration / 1000).toFixed(1)} s`}
                    onChange={setDuration}
                  />
                </div>
              </section>

              <section className="rounded-2xl border border-[#dfe5e3] bg-white p-4 shadow-[0_2px_8px_rgb(20_34_35_/_3%)]">
                <h2 className="mb-3 text-[13px] font-semibold text-[#263431]">
                  运动方向
                </h2>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    aria-pressed={direction === "left"}
                    onClick={() => setDirection("left")}
                    className={
                      "inline-flex h-9 items-center justify-center gap-2 rounded-lg border text-[11px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 motion-reduce:transition-none " +
                      (direction === "left"
                        ? "border-[#fe2c55]/35 bg-[#fff3f5] text-[#e52b50]"
                        : "border-[#e5e9e7] bg-white text-[#75817d] hover:bg-[#f8f9f8]")
                    }
                  >
                    <ArrowLeft size={14} />
                    向左
                  </button>
                  <button
                    type="button"
                    aria-pressed={direction === "right"}
                    onClick={() => setDirection("right")}
                    className={
                      "inline-flex h-9 items-center justify-center gap-2 rounded-lg border text-[11px] font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#fe2c55]/25 motion-reduce:transition-none " +
                      (direction === "right"
                        ? "border-[#fe2c55]/35 bg-[#fff3f5] text-[#e52b50]"
                        : "border-[#e5e9e7] bg-white text-[#75817d] hover:bg-[#f8f9f8]")
                    }
                  >
                    <ArrowRight size={14} />
                    向右
                  </button>
                </div>
              </section>

              <section className="rounded-2xl border border-[#dfe5e3] bg-white p-4 shadow-[0_2px_8px_rgb(20_34_35_/_3%)]">
                <h2 className="mb-4 text-[13px] font-semibold text-[#263431]">
                  发射位置
                </h2>
                <div className="flex flex-col gap-5">
                  <RangeControl
                    id="particle-origin-x"
                    label="水平位置 X"
                    value={originX}
                    min={10}
                    max={90}
                    step={1}
                    displayValue={`${originX}%`}
                    onChange={setOriginX}
                  />
                  <RangeControl
                    id="particle-origin-y"
                    label="垂直位置 Y"
                    value={originY}
                    min={10}
                    max={90}
                    step={1}
                    displayValue={`${originY}%`}
                    onChange={setOriginY}
                  />
                </div>
              </section>

              <p className="m-0 px-1 text-[10px] leading-[1.7] text-[#9aa4a1]">
                调整参数会同步更新下一次播放效果。动画使用独立 Canvas，不会触发 React 高频重渲染。
              </p>
            </aside>
          </div>
        </div>
      </div>
    </AppShell>
  );
}

export default ParticlePreviewPage;
