import type { ReactNode } from "react";
import {
  ArrowRight,
  BookOpen,
  Boxes,
  ChartNoAxesCombined,
  Database,
  Layers,
  RefreshCw,
  Table2,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Button, Card, CardContent, PageHeader } from "@yak-ops/yak-ui";
import { Link } from "react-router-dom";

import { useWorkspace } from "@/hooks/use-workspace";

import { getModelingBootstrap } from "@/service/data-modeling/bootstrap";
import { PlanningIcon } from "../planning/components/icons";

const MODELING_SECTIONS: Array<{
  title: string;
  description: string;
  path: string;
  icon: ReactNode;
  capabilities: string[];
}> = [
  {
    title: "数仓规划",
    description: "建立统一的业务分类、数仓分层和主题组织体系。",
    path: "/data-modeling/planning/business-type",
    icon: <PlanningIcon name="dw-layer" size={18} />,
    capabilities: ["业务分类", "数仓分层", "数据域", "业务过程", "数据集市", "主题域"],
  },
  {
    title: "维度建模",
    description: "管理维度建模与逆向建模，逐步完善模型设计能力。",
    path: "/data-modeling/models/positive",
    icon: <Table2 size={18} strokeWidth={1.8} />,
    capabilities: ["维度建模", "逆向建模", "字段设计", "分区配置", "版本管理"],
  },
  {
    title: "数据标准",
    description: "统一字段标准、标准代码、度量单位和命名词典。",
    path: "/data-modeling/standards/fields",
    icon: <BookOpen size={18} strokeWidth={1.8} />,
    capabilities: ["字段标准", "标准代码", "度量单位", "命名词典"],
  },
  {
    title: "数据指标",
    description: "沉淀可复用的原子指标、派生指标和复合指标。",
    path: "/data-modeling/indicators/compose",
    icon: <ChartNoAxesCombined size={18} strokeWidth={1.8} />,
    capabilities: ["原子指标", "派生指标", "复合指标", "修饰词", "时间周期"],
  },
];

const OVERVIEW_METRICS: Array<{
  title: string;
  unit: string;
  icon: ReactNode;
}> = [
  { title: "维度表模型", unit: "模型数量", icon: <Table2 size={19} /> },
  { title: "明细表模型", unit: "模型数量", icon: <Database size={19} /> },
  { title: "汇总表模型", unit: "模型数量", icon: <Layers size={19} /> },
  { title: "应用表模型", unit: "模型数量", icon: <Boxes size={19} /> },
  { title: "原子指标", unit: "指标数量", icon: <ChartNoAxesCombined size={19} /> },
  { title: "派生指标 & 复合指标", unit: "指标数量", icon: <ChartNoAxesCombined size={19} /> },
];

function ModelingModuleCard({
  section,
}: {
  section: (typeof MODELING_SECTIONS)[number];
}) {
  return (
    <Link
      to={section.path}
      className="group flex min-h-[108px] items-start gap-3 rounded-lg border border-[var(--yak-components-panel-border)] bg-white/90 p-4 outline-none transition-all hover:-translate-y-0.5 hover:border-[var(--yak-color-primary)] hover:shadow-sm focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary)]"
    >
      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-[var(--yak-color-hover)] text-[var(--yak-color-primary)]">
        {section.icon}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <span className="text-sm font-semibold text-[var(--yak-components-panel-text)]">
            {section.title}
          </span>
          <ArrowRight
            size={15}
            aria-hidden="true"
            className="shrink-0 text-[var(--yak-components-muted-text)] transition-transform group-hover:translate-x-0.5"
          />
        </div>
        <p className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
          {section.description}
        </p>
        <p className="mt-2 truncate text-[10px] leading-4 text-[var(--yak-components-muted-text)]">
          {section.capabilities.slice(0, 3).join(" · ")}
        </p>
      </div>
    </Link>
  );
}

export function DataModelingOverviewPage() {
  const { currentWorkspace } = useWorkspace();
  const bootstrapQuery = useQuery({
    queryKey: ["data-modeling", "bootstrap", currentWorkspace?.id],
    queryFn: () => getModelingBootstrap(currentWorkspace!.id),
    enabled: Boolean(currentWorkspace?.id),
  });

  return (
    <div className="min-h-full bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader
        title="建模总览"
        bordered
        className="bg-[var(--yak-components-panel-bg)] px-6 max-md:px-4"
        extra={
          <Button
            size="sm"
            variant="secondary"
            isDisabled={bootstrapQuery.isFetching}
            onClick={() => void bootstrapQuery.refetch()}
          >
            <RefreshCw
              size={14}
              aria-hidden="true"
              className={bootstrapQuery.isFetching ? "animate-spin" : ""}
            />
            刷新
          </Button>
        }
      />

      <div className="space-y-4 px-6 pb-8 pt-5 max-md:px-4">
        {bootstrapQuery.isError ? (
          <div
            role="alert"
            className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-800"
          >
            <span>建模模块初始化信息加载失败，请检查后端服务或当前工作空间权限。</span>
            <Button size="sm" variant="secondary" onClick={() => void bootstrapQuery.refetch()}>
              重试
            </Button>
          </div>
        ) : null}

        <section className="relative isolate overflow-hidden rounded-xl border border-[var(--yak-components-panel-border)] bg-gradient-to-br from-[#f4f7ff] via-white to-[#fff7f8]">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 opacity-40 [background-image:radial-gradient(#b8c7e8_0.7px,transparent_0.7px)] [background-size:18px_18px]"
          />
          <div
            aria-hidden="true"
            className="pointer-events-none absolute -right-24 -top-32 h-80 w-80 rounded-full bg-[var(--yak-color-primary)] opacity-[0.045] blur-3xl"
          />
          <div className="relative grid grid-cols-1 gap-7 p-5 md:p-7 xl:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)] xl:items-center xl:gap-10 xl:px-9 xl:py-8">
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {MODELING_SECTIONS.map((section) => (
                <ModelingModuleCard key={section.path} section={section} />
              ))}
            </div>

            <div className="max-w-xl xl:pl-2">
              <div className="inline-flex items-center gap-2 rounded-full border border-[var(--yak-components-panel-border)] bg-white/80 px-3 py-1 text-[10px] font-semibold tracking-[0.16em] text-[var(--yak-components-muted-text)]">
                <span className="h-1.5 w-1.5 rounded-full bg-[var(--yak-color-primary)]" />
                DATA MODELING WORKSPACE
              </div>
              <h1 className="mt-4 text-3xl font-semibold tracking-tight text-[var(--yak-components-panel-text)] md:text-4xl">
                智能数据建模
              </h1>
              <p className="mt-4 max-w-lg text-sm leading-7 text-[var(--yak-components-muted-text)]">
                全链路数据治理的第一步。围绕数仓规划、维度建模、数据标准和数据指标，
                以统一的业务视角组织数据资产，让数据定义更清晰、模型设计更规范、指标沉淀更高效。
              </p>
              <Link
                to="/data-modeling/planning/business-type"
                className="mt-5 inline-flex items-center gap-2 rounded-md bg-[var(--yak-color-primary)] px-4 py-2.5 text-xs font-medium text-white transition-opacity hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary)] focus-visible:ring-offset-2"
              >
                开始建模
                <ArrowRight size={14} aria-hidden="true" />
              </Link>
            </div>
          </div>
        </section>

        <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(280px,0.92fr)]">
          <Card className="h-full">
            <CardContent className="p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="text-base font-semibold">数据概览</div>
                  <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
                    当前工作空间内的模型与指标资产
                  </p>
                </div>
                <span className="rounded-full border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] px-2.5 py-1 text-[10px] text-[var(--yak-components-muted-text)]">
                  统计接口待接入
                </span>
              </div>

              <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 2xl:grid-cols-3">
                {OVERVIEW_METRICS.map((metric) => (
                  <div
                    key={metric.title}
                    className="flex min-h-[94px] items-center gap-3 rounded-md border border-[var(--yak-components-panel-border)] bg-white p-3 transition-shadow hover:shadow-sm"
                  >
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-hover)] text-[var(--yak-color-primary)]">
                      {metric.icon}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-xs font-semibold text-[var(--yak-components-panel-text)]">
                        {metric.title}
                      </div>
                      <div className="mt-1 flex items-baseline gap-2">
                        <span className="text-2xl font-semibold tabular-nums text-[var(--yak-components-panel-text)]">
                          —
                        </span>
                        <span className="text-[11px] text-[var(--yak-components-muted-text)]">
                          {metric.unit}
                        </span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          <Card className="h-full">
            <CardContent className="flex h-full min-h-[300px] flex-col p-5">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="text-base font-semibold">最新发布</div>
                  <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
                    最近发布的模型与指标
                  </p>
                </div>
                <BookOpen
                  size={18}
                  aria-hidden="true"
                  className="text-[var(--yak-components-muted-text)]"
                />
              </div>
              <div className="flex flex-1 flex-col items-center justify-center py-8 text-center">
                <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[var(--yak-color-hover)] text-[var(--yak-components-muted-text)]">
                  <BookOpen size={22} strokeWidth={1.6} />
                </div>
                <div className="mt-3 text-sm font-medium">发布记录暂未接入</div>
                <p className="mt-1 max-w-[240px] text-xs leading-5 text-[var(--yak-components-muted-text)]">
                  后续接入模型与指标发布流程后，将在这里展示最新发布内容。
                </p>
              </div>
            </CardContent>
          </Card>
        </div>

        {bootstrapQuery.data ? (
          <div className="flex items-center gap-2 text-[11px] text-[var(--yak-components-muted-text)]">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            服务连接正常 · {bootstrapQuery.data.moduleName} · 项目 {bootstrapQuery.data.projectCode}
          </div>
        ) : bootstrapQuery.isLoading ? (
          <div className="text-[11px] text-[var(--yak-components-muted-text)]">
            正在检查建模服务连接…
          </div>
        ) : null}
      </div>
    </div>
  );
}

export default DataModelingOverviewPage;
