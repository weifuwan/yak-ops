import React, { useState } from "react";
import {
  Boxes,
  ChartNoAxesCombined,
  Database,
  Layers3,
  Table2,
  type LucideIcon,
} from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import PixelBlast from "./components/PixelBlast";

const EMPTY_IMAGE =
  "https://img.alicdn.com/imgextra/i4/O1CN017NgExC1MdM1GSlL3J_!!6000000001457-2-tps-720-720.png";

type StatisticValue = {
  num: string;
  unit: string;
};

type StatisticItem = {
  title: string;
  icon?: LucideIcon;
  values?: StatisticValue[];
  total?: boolean;
  formula?: string;
};

const STATISTICS: StatisticItem[] = [
  {
    title: "维度表模型",
    icon: Boxes,
    values: [{ num: "—", unit: "模型数量" }],
  },
  {
    title: "明细表模型",
    icon: Table2,
    values: [{ num: "—", unit: "模型数量" }],
  },
  {
    title: "汇总表模型",
    icon: Layers3,
    values: [{ num: "—", unit: "模型数量" }],
  },
  {
    title: "应用表模型",
    icon: Database,
    values: [{ num: "—", unit: "模型数量" }],
  },
  {
    title: "派生指标&复合指标",
    icon: ChartNoAxesCombined,
    values: [
      { num: "—", unit: "指标数量" },
      { num: "—", unit: "向上取整" },
    ],
  },
  {
    title: "合计",
    total: true,
    values: [{ num: "—", unit: "" }],
    formula: "统计接口待接入",
  },
];

function InfoIcon() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5" />
      <path d="M12 8h.01" />
    </svg>
  );
}

/**
 * 数据统计卡片
 */
function StatisticCard({ item }: { item: StatisticItem }) {
  if (item.total) {
    return (
      <div className="block w-full mb-4 rounded-[2px] border border-[#EDEFF7] bg-white p-1.5 transition-shadow hover:shadow-[0_1px_8px_-1px_#0000001f]">
        <div className="flex h-6 flex-row">
          <div className="flex-1 min-w-0 overflow-hidden pl-1.5 text-xs font-black leading-6 text-[#333946] text-ellipsis">
            {item.title}
          </div>

          <div className="flex-1 min-w-[80px] max-w-[100px] overflow-hidden text-xs font-black leading-6 text-[#303642]">
            {item.values?.[0]?.num}
          </div>
        </div>

        <div className="overflow-hidden pl-1.5 text-xs leading-6 text-[#9299A5] whitespace-nowrap text-ellipsis">
          {item.formula}
        </div>
      </div>
    );
  }

  return (
    <div className="flex w-full flex-row items-center mb-4 rounded-[2px] border border-[#EDEFF7] bg-white p-1.5 transition-shadow hover:shadow-[0_1px_8px_-1px_#0000001f]">
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-[#F3F4F8] text-[#3A3D51]">
        {item.icon && <item.icon size={22} strokeWidth={1.75} aria-hidden="true" />}
      </div>

      <div
        className="h-12 min-w-0 flex-1 overflow-hidden px-1.5 text-xs font-black leading-[48px] text-[#333946] whitespace-nowrap text-ellipsis"
        title={item.title}
      >
        {item.title}
      </div>

      {item.values?.map((value, index) => (
        <div
          key={index}
          className="h-12 min-w-[80px] max-w-[100px] flex-1 overflow-hidden"
        >
          <div className="h-6 overflow-hidden text-xs font-black leading-6 text-[#303642] whitespace-nowrap text-ellipsis">
            {value.num}
          </div>

          <div className="h-6 overflow-hidden text-xs leading-6 text-[#9299A5] whitespace-nowrap text-ellipsis">
            {value.unit}
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * 数据概览
 */
function DataOverviewCard() {
  const [workspaceFilter, setWorkspaceFilter] = useState("all");

  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-[4px] border border-[#EDEFF7] bg-white p-4">
      {/* Header */}
      <div className="flex min-h-5 w-full flex-row items-center">
        <div className="flex h-5 flex-1 items-center gap-1 text-base leading-5 text-[#10121A]">
          数据概览
          <span className="cursor-help text-[var(--yak-color-primary)]">
            <InfoIcon />
          </span>
        </div>

        <div className="flex h-5 shrink-0 items-center gap-1 leading-5">
          <button
            type="button"
            className="cursor-pointer border-0 bg-transparent p-0 text-xs text-[var(--yak-color-primary)] hover:opacity-80"
          >
            查看数据详情
          </button>

          <span className="cursor-help text-[var(--yak-color-primary)]">
            <InfoIcon />
          </span>
        </div>
      </div>

      {/* Content */}
      <div className="relative mt-2 w-full">
        {/* 工作空间筛选 */}
        <div className="mb-2 flex h-8 w-[300px] max-w-full items-center text-xs">
          <label
            htmlFor="overview-workspace"
            className="w-1/4 shrink-0 whitespace-nowrap text-[#555B68]"
          >
            工作空间：
          </label>

          <div className="h-8 w-3/4">
            <Select
              size="small"
              value={workspaceFilter}
              onValueChange={(value) => setWorkspaceFilter(String(value ?? "all"))}
            >
              <SelectTrigger
                id="overview-workspace"
                aria-label="工作空间"
                variant="outlined"
                className="!h-8 w-full !rounded-[4px] !px-2.5"
              >
                <SelectValue placeholder="全部">
                  {(value) => (value === "all" ? "全部" : String(value ?? "全部"))}
                </SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">
                  <SelectItemText>全部</SelectItemText>
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        {/* 统计卡片 */}
        <div className="grid grid-cols-2 gap-x-4 min-[1500px]:grid-cols-3">
          {STATISTICS.map((item) => (
            <StatisticCard key={item.title} item={item} />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * 最新发布
 */
function LatestPublishCard() {
  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-[4px] border border-[#EDEFF7] bg-white p-4">
      {/* Header */}
      <div className="flex min-h-5 w-full items-center">
        <div className="flex h-5 flex-1 items-center gap-1 text-base leading-5 text-[#10121A]">
          最新发布
          <span className="cursor-help text-[var(--yak-color-primary)]">
            <InfoIcon />
          </span>
        </div>
      </div>

      {/* Empty State */}
      <div className="relative mt-2 w-full">
        <div className="flex h-[250px] w-full flex-col items-center justify-center overflow-hidden">
          <div
            className="h-[240px] w-[320px] max-w-full shrink rounded-none bg-contain bg-center bg-no-repeat"
            style={{
              backgroundImage: `url("${EMPTY_IMAGE}")`,
            }}
          />

          <div className="mt-2 shrink-0 px-6 text-center text-xs leading-6 text-[#777E8B]">
            暂无数据
          </div>
        </div>
      </div>
    </div>
  );
}

/**
 * 数据建模首页
 */
export function DataModelingOverviewPage() {
  return (
    <main className="flex min-h-[calc(100vh-38px)] w-full flex-col overflow-x-auto bg-[#F6F6F6] text-[#10121A]">
      {/* 上半部分：主视觉 */}
      <section className="relative h-[46vh] min-h-[360px] max-h-[468px] w-full shrink-0 overflow-hidden bg-[#F6F6F6]">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0">
          <PixelBlast
            variant="square"
            pixelSize={3}
            color="#FE2C55"
            accentColor="#FE2C55"
            patternScale={2.8}
            patternDensity={0.7}
            pixelSizeJitter={0.27}
            softness={0}
            smoothing={0.16}
            glow={0}
            twinkle={0}
            speed={0.9}
            drift={0.35}
            hoverEffect="none"
            enableRipples
            rippleSpeed={0.3}
            rippleThickness={0.1}
            rippleIntensityScale={0.95}
            autoRipple={0}
            edgeFade={0.25}
            intro
            lightMode
          />
        </div>

        <div className="relative z-10 mx-auto flex h-full w-[calc(100%_-_160px)] min-w-[1200px] max-w-[1600px] items-center">
          <div className="max-w-[620px] -translate-y-3">
            <h1 className="m-0 text-[30px] font-semibold leading-[1.3] tracking-[-0.02em] text-[#10121A]">
              让数据模型更清晰、更统一
            </h1>

            <p className="mt-3 max-w-[520px] text-sm leading-6 text-[#777E8B]">
              从数仓规划、数据标准到维度建模与指标管理，统一沉淀数据资产。
            </p>
          </div>
        </div>

      </section>

      {/* 下半部分：数据概览 */}
      <section className="min-h-[420px] w-full flex-1 bg-[#F6F6F6] pb-4 pt-6">
        <div className="mx-auto w-[calc(100%_-_160px)] min-w-[1200px] max-w-[1600px]">
          {/* 24 栏响应式布局 */}
          <div className="-mx-2 grid grid-cols-[repeat(24,minmax(0,1fr))]">
            {/* 左侧：数据概览 */}
            <div className="col-span-[24] flex px-2 min-[1200px]:col-span-[17] min-[1500px]:col-span-[16]">
              <DataOverviewCard />
            </div>

            {/* 右侧：最新发布 */}
            <div className="col-span-[24] flex px-2 min-[1200px]:col-span-[7] min-[1500px]:col-span-[8]">
              <LatestPublishCard />
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}

export default DataModelingOverviewPage;
