import type { ReactNode } from "react";
import {
  ArrowRight,
  BookOpen,
  Boxes,
  ChartNoAxesCombined,
  Layers,
  RefreshCw,
  Table2,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { Button, Card, CardContent, PageHeader } from "@yak-ops/yak-ui";
import { Link } from "react-router-dom";

import { useWorkspace } from "@/hooks/use-workspace";

import { getModelingBootstrap } from "../service";
import { PlanningIcon } from "../planning/icons";

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

      <div className="space-y-5 px-6 pb-8 pt-5 max-md:px-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex items-center gap-2 text-sm font-medium">
              <Boxes size={17} className="text-[var(--yak-color-primary)]" />
              数据建模工作台
            </div>
            <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
              在当前工作空间内规划数仓、设计模型、管理标准与指标。
            </p>
          </div>
        </div>

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

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {MODELING_SECTIONS.map((section) => (
            <Link
                key={section.path}
                to={section.path}
                className="group block rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary)]"
              >
                <Card className="h-full transition-colors group-hover:border-[var(--yak-color-primary)]">
                  <CardContent className="p-5">
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-hover)] text-[var(--yak-color-primary)]">
                          {section.icon}
                        </div>
                        <div className="min-w-0">
                          <div className="text-sm font-semibold">{section.title}</div>
                          <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
                            {section.description}
                          </p>
                        </div>
                      </div>
                      <ArrowRight
                        size={16}
                        className="mt-1 shrink-0 text-[var(--yak-components-muted-text)] transition-transform group-hover:translate-x-0.5"
                      />
                    </div>
                    <div className="mt-4 flex flex-wrap gap-1.5">
                      {section.capabilities.map((capability) => (
                        <span
                          key={capability}
                          className="rounded border border-[var(--yak-components-panel-border)] bg-white px-2 py-1 text-[11px] text-[var(--yak-components-muted-text)]"
                        >
                          {capability}
                        </span>
                      ))}
                    </div>
                  </CardContent>
                </Card>
            </Link>
          ))}
        </div>

        <Card>
          <CardContent className="flex items-start gap-3 p-5">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-hover)] text-[var(--yak-color-primary)]">
              <Layers size={18} />
            </div>
            <div className="min-w-0">
              <div className="text-sm font-semibold">基础框架已接入</div>
              <p className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
                数仓规划、数据标准和数据指标已接入当前工作空间，覆盖业务分类、字段标准、标准代码、度量单位、原子指标、派生指标、复合指标及版本管理。
              </p>
            </div>
          </CardContent>
        </Card>

        {bootstrapQuery.data ? (
          <div className="text-[11px] text-[var(--yak-components-muted-text)]">
            服务连接正常 · {bootstrapQuery.data.moduleName} · 项目 {bootstrapQuery.data.projectCode}
          </div>
        ) : null}
      </div>
    </div>
  );
}

export default DataModelingOverviewPage;
