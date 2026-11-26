import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
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

import { useWorkspace } from "@/hooks/use-workspace";
import {
  modelingOverviewApi,
  type ModelingLatestSubmission,
  type ModelingOverview,
} from "@/service/data-modeling/overview";

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

function countText(value: number | undefined, loading: boolean) {
  if (loading) return "…";
  return typeof value === "number" ? String(value) : "—";
}

function buildStatistics(
  overview: ModelingOverview | undefined,
  loading: boolean,
  error: boolean,
): StatisticItem[] {
  const models = overview?.modelStatistics;
  const indicators = overview?.indicatorStatistics;

  return [
    {
      title: "维度表模型",
      icon: Boxes,
      values: [{ num: countText(models?.dimension, loading), unit: "模型数量" }],
    },
    {
      title: "明细表模型",
      icon: Table2,
      values: [{ num: countText(models?.detail, loading), unit: "模型数量" }],
    },
    {
      title: "汇总表模型",
      icon: Layers3,
      values: [{ num: countText(models?.summary, loading), unit: "模型数量" }],
    },
    {
      title: "应用表模型",
      icon: Database,
      values: [{ num: countText(models?.application, loading), unit: "模型数量" }],
    },
    {
      title: "派生指标&复合指标",
      icon: ChartNoAxesCombined,
      values: [
        { num: countText(indicators?.derived, loading), unit: "派生指标" },
        { num: countText(indicators?.composite, loading), unit: "复合指标" },
      ],
    },
    {
      title: "合计",
      total: true,
      values: [{ num: countText(overview?.totalAssets, loading), unit: "" }],
      formula: error
        ? "统计加载失败"
        : overview
          ? `模型 ${models?.total ?? 0} · 指标 ${indicators?.total ?? 0}`
          : "正在加载统计",
    },
  ];
}

function formatSubmissionTime(value?: string | number) {
  if (value === undefined || value === null || value === "") return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  return date.toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}


function PixelArrowIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 18 18"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className="size-3 -rotate-90 sm:size-4"
      aria-hidden="true"
    >
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M1.5 5.30469L4.5 5.30469V8.30469H1.5L1.5 5.30469ZM7.5 11.3047V8.30469H4.5L4.5 11.3047H7.5ZM10.5 11.3047V14.3047H7.5L7.5 11.3047L10.5 11.3047ZM13.5 8.30469L13.5 11.3047H10.5L10.5 8.30469H13.5ZM13.5 8.30469V5.30469L16.5 5.30469V8.30469H13.5Z"
        fill="currentColor"
      />
    </svg>
  );
}

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
function DataOverviewCard({
  overview,
  loading,
  error,
}: {
  overview?: ModelingOverview;
  loading: boolean;
  error: boolean;
}) {
  const [workspaceFilter, setWorkspaceFilter] = useState("all");
  const statistics = buildStatistics(overview, loading, error);

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
          {statistics.map((item) => (
            <StatisticCard key={item.title} item={item} />
          ))}
        </div>
      </div>
    </div>
  );
}

/**
 * 最新提交
 */
function LatestSubmittedCard({
  items,
  loading,
  error,
}: {
  items: ModelingLatestSubmission[];
  loading: boolean;
  error: boolean;
}) {
  return (
    <div className="flex h-full w-full flex-col overflow-hidden rounded-[4px] border border-[#EDEFF7] bg-white p-4">
      <div className="flex min-h-5 w-full items-center">
        <div className="flex h-5 flex-1 items-center gap-1 text-base leading-5 text-[#10121A]">
          最新提交
          <span className="cursor-help text-[var(--yak-color-primary)]" title="仅展示已提交资产，不代表物理表已发布到 Doris">
            <InfoIcon />
          </span>
        </div>
      </div>

      <div className="relative mt-3 min-h-[250px] w-full">
        {loading ? (
          <div className="flex h-[250px] items-center justify-center text-xs text-[#9299A5]">
            正在加载最新提交...
          </div>
        ) : error ? (
          <div className="flex h-[250px] items-center justify-center text-xs text-[#9299A5]">
            最新提交加载失败
          </div>
        ) : items.length === 0 ? (
          <div className="flex h-[250px] w-full flex-col items-center justify-center overflow-hidden">
            <div
              className="h-[210px] w-[280px] max-w-full shrink rounded-none bg-contain bg-center bg-no-repeat"
              style={{
                backgroundImage: `url("${EMPTY_IMAGE}")`,
              }}
            />
            <div className="mt-1 shrink-0 px-6 text-center text-xs leading-6 text-[#777E8B]">
              暂无数据
            </div>
          </div>
        ) : (
          <div className="divide-y divide-[#EEF0F5]">
            {items.map((item) => (
              <div key={item.assetType + "-" + item.id} className="py-3 first:pt-1">
                <div className="flex min-w-0 items-center gap-2">
                  <span className="shrink-0 rounded bg-[#F3F4F8] px-1.5 py-0.5 text-[11px] text-[#555B68]">
                    {item.assetTypeLabel}
                  </span>
                  <span className="min-w-0 truncate text-sm font-medium text-[#303642]" title={item.name}>
                    {item.name}
                  </span>
                </div>
                <div className="mt-1.5 flex items-center gap-2 text-xs text-[#9299A5]">
                  <span>{item.updateBy || "—"}</span>
                  <span>·</span>
                  <span>{formatSubmissionTime(item.updateTime)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * 数据建模首页
 */
export function DataModelingOverviewPage() {
  const { currentWorkspace } = useWorkspace();
  const projectCode = currentWorkspace?.id ? String(currentWorkspace.id) : "";
  const overviewQuery = useQuery({
    queryKey: ["modeling", "overview", projectCode],
    queryFn: () => modelingOverviewApi.get(projectCode),
    enabled: Boolean(projectCode),
  });
  const overview = overviewQuery.data;

  return (
    <main className="flex min-h-[calc(100vh-38px)] w-full flex-col overflow-x-auto bg-[#F6F6F6] text-[#10121A]">
      {/* 上半部分：主视觉 */}
      <section className="relative h-[46vh] min-h-[360px] max-h-[468px] w-full shrink-0 overflow-hidden bg-[#F6F6F6]">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0">
          <PixelBlast
            variant="square"
            pixelSize={3}
            color="#A9ABB5"
            accentColor="#A9ABB5"
            patternScale={1.5}
            patternDensity={0.62}
            pixelSizeJitter={0.06}
            softness={0}
            smoothing={0}
            glow={0.08}
            twinkle={0}
            speed={0.25}
            drift={0}
            hoverEffect="none"
            enableRipples
            rippleSpeed={0.3}
            rippleThickness={0.1}
            rippleIntensityScale={0.95}
            autoRipple={0}
            edgeFade={0.25}
            intro={false}
            lightMode
          />
        </div>

        <div className="relative z-10 mx-auto flex h-full w-[calc(100%_-_160px)] min-w-[1200px] max-w-[1600px] items-center">
          <div className="max-w-[720px] -translate-y-3">
            <h1 className="m-0 inline-block bg-[#F6F6F6] text-[36px] font-medium leading-[1.35] tracking-[-0.025em] text-[#10121A]">
              <span>让数据模型</span>
              <span className="ml-1 bg-[#F6F6F6] border-b-[5px] border-dotted border-[color:color-mix(in_oklab,var(--yak-color-primary)_48%,transparent)] pb-1 font-semibold text-[var(--yak-color-primary)]">
                更清晰、更统一
              </span>
            </h1>

            <p className="mt-4 inline-block max-w-[620px] bg-[#F6F6F6] text-base leading-7 text-[#555B68]">
              从数仓规划、数据标准到维度建模与指标管理，统一沉淀数据资产。
            </p>

            <div className="mt-7 flex items-center gap-3">
              <Link
                to="/kimball/models/positive"
                className="inline-flex h-10 min-w-[132px] items-center justify-center gap-2 rounded-full bg-[var(--yak-color-primary)] px-5 text-sm font-medium text-white shadow-sm transition-all hover:opacity-90 hover:shadow-md"
              >
                开始建模
                <PixelArrowIcon />
              </Link>

              <Link
                to="/kimball/indicators/atom"
                className="inline-flex h-10 min-w-[132px] items-center justify-center gap-2 rounded-full bg-[#10121A] px-5 text-sm font-medium text-white shadow-sm transition-all hover:bg-[#20232D] hover:shadow-md"
              >
                指标管理
                <PixelArrowIcon />
              </Link>
            </div>
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
              <DataOverviewCard
                overview={overview}
                loading={overviewQuery.isPending && Boolean(projectCode)}
                error={overviewQuery.isError}
              />
            </div>

            {/* 右侧：最新提交 */}
            <div className="col-span-[24] flex px-2 min-[1200px]:col-span-[7] min-[1500px]:col-span-[8]">
              <LatestSubmittedCard
                items={overview?.latestSubmitted || []}
                loading={overviewQuery.isPending && Boolean(projectCode)}
                error={overviewQuery.isError}
              />
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}

export default DataModelingOverviewPage;
