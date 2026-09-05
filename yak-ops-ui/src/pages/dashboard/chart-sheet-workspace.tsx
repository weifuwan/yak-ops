import {
  AnalysisPreview,
  buildDatasetQueryPayload,
  canQueryAnalysis,
} from '@/components/analysis/AnalysisPreview';
import { analysisQueryCacheKey } from '@/components/analysis/query-runtime';
import { useIntl } from '@umijs/max';
import { Empty } from 'antd';
import { BarChart3 } from 'lucide-react';
import { useEffect, useMemo } from 'react';
import { ChartAppearanceConfigPanel } from './chart-editor';
import { ChartDataColumn } from './chart-data-column';
import { ChartEncodingShelf } from './chart-encoding-shelf';
import { FineBiChartBuilderPanel } from './finebi-chart-builder';
import { CHART_META } from './helpers';
import type {
  AnalysisAsset,
  DashboardFilter,
  DashboardGlobalFilter,
  DashboardInlineAnalysisSpec,
  DashboardInteraction,
  DashboardWidget,
  PublishedDataset,
} from './model';
import { registerDashboardPerformanceQuery } from './performance-runtime';

export function DashboardChartSheetWorkspace({
  currentDashboardId,
  widget,
  widgets,
  datasets,
  analyses,
  globalFilters,
  interactions,
  runtimeFilters,
  updateInlineAnalysis,
  updateInteractions,
  changeDataset,
  detachAnalysis,
  onDone,
}: {
  currentDashboardId: string;
  widget: DashboardWidget;
  widgets: DashboardWidget[];
  datasets: PublishedDataset[];
  analyses: AnalysisAsset[];
  globalFilters: DashboardGlobalFilter[];
  interactions: DashboardInteraction[];
  runtimeFilters: DashboardFilter[];
  updateWidget: (patch: Partial<DashboardWidget>) => void;
  updateInlineAnalysis: (patch: Partial<DashboardInlineAnalysisSpec>) => void;
  updateInteractions: (interactions: DashboardInteraction[]) => void;
  changeDataset: (datasetId: string) => void;
  detachAnalysis: () => void;
  onDone: () => void;
}) {
  const intl = useIntl();
  const analysis = widget.analysisId
    ? analyses.find((item) => item.id === widget.analysisId)
    : undefined;
  const spec = widget.analysisId ? analysis : widget.inlineAnalysis;
  const dataset = spec
    ? datasets.find((item) => item.id === spec.datasetId)
    : undefined;
  const title = widget.analysisId
    ? analysis?.name ?? intl.formatMessage({ id: 'pages.dashboard.editor.historicalChart' })
    : widget.title?.trim() || intl.formatMessage({ id: 'pages.dashboard.editor.unnamedChart' });
  const chartTypeLabel = spec
    ? intl.formatMessage({ id: CHART_META[spec.type].labelId })
    : undefined;
  const editable = !widget.analysisId && Boolean(widget.inlineAnalysis);
  const performanceQueryKey = useMemo(() => {
    if (!spec || !dataset || !canQueryAnalysis(spec)) return undefined;
    return analysisQueryCacheKey(
      dataset,
      buildDatasetQueryPayload(spec, dataset, runtimeFilters),
    );
  }, [dataset, runtimeFilters, spec]);

  useEffect(() => {
    if (!dataset || !performanceQueryKey) return;
    registerDashboardPerformanceQuery({
      widgetId: widget.id,
      widgetName: title,
      datasetId: dataset.id,
      queryKey: performanceQueryKey,
    });
  }, [dataset, performanceQueryKey, title, widget.id]);

  return (
    <div className="chart-sheet-workspace flex min-h-0 flex-1 overflow-hidden bg-[#f3f4f6]">
      <div className="flex min-h-0 shrink-0 bg-white shadow-[1px_0_0_#e3e6ea]">
        <ChartDataColumn
          dataset={dataset}
          datasets={datasets}
          spec={spec}
          editable={editable}
          onDatasetChange={!widget.analysisId ? changeDataset : undefined}
          onSpecPatch={!widget.analysisId ? updateInlineAnalysis : undefined}
        />
        <FineBiChartBuilderPanel
          widget={widget}
          datasets={datasets}
          analyses={analyses}
          updateInlineAnalysis={updateInlineAnalysis}
          detachAnalysis={detachAnalysis}
        />
      </div>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#f3f4f6]">
        <ChartEncodingShelf
          spec={spec}
          dataset={dataset}
          editable={editable && Boolean(dataset)}
          onSpecPatch={!widget.analysisId ? updateInlineAnalysis : undefined}
        />

        <div className="min-h-0 flex-1 overflow-auto">
          <div className="flex min-h-full p-4 2xl:p-5">
            <div className="mx-auto flex min-h-[560px] w-full max-w-[1320px] flex-1 flex-col">
              <div className="flex h-9 shrink-0 items-center gap-2 px-1">
                <BarChart3 size={13} className="shrink-0 text-[#667085]" />
                <span className="truncate text-[12px] font-semibold text-[#344054]">
                  {title}
                </span>
                {chartTypeLabel ? (
                  <span className="shrink-0 rounded-[5px] border border-[#e1e4e8] bg-[#f8f9fa] px-1.5 py-0.5 text-[8px] text-[#7a818c]">
                    {chartTypeLabel}
                  </span>
                ) : null}
              </div>

              <div className="mt-1 min-h-0 flex-1 overflow-hidden rounded-[8px] border border-[#e1e4e8] bg-white">
                {spec && dataset ? (
                  <AnalysisPreview
                    spec={spec}
                    dataset={dataset}
                    runtimeFilters={runtimeFilters}
                    className="h-full min-h-[500px] p-4"
                  />
                ) : (
                  <div className="flex min-h-[500px] items-center justify-center">
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description={intl.formatMessage({
                        id: spec
                          ? 'pages.dashboard.editor.chartWorkspace.dataUnavailable'
                          : 'pages.dashboard.editor.chartWorkspace.configUnavailable',
                      })}
                    />
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </main>

      <ChartAppearanceConfigPanel
        currentDashboardId={currentDashboardId}
        widget={widget}
        widgets={widgets}
        datasets={datasets}
        analyses={analyses}
        globalFilters={globalFilters}
        interactions={interactions}
        updateInlineAnalysis={updateInlineAnalysis}
        updateInteractions={updateInteractions}
        onDone={onDone}
      />
    </div>
  );
}
