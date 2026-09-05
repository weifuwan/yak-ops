import { BRAND_CSS_VARIABLES, BRAND_THEME } from '@/styles/brand';
import { useIntl } from '@umijs/max';
import { ConfigProvider, Popover } from 'antd';
import { Info } from 'lucide-react';
import { useMemo } from 'react';
import QualityMetricSection from './components/QualityMetricSection';
import QualityRadarOverview from './components/QualityRadarOverview';
import { useQualityOverviewPage } from './hooks/useQualityOverviewPage';
import { formatPeriodText } from './utils';

const MetricExplanationPanel = () => {
  const intl = useIntl();
  const explanations = useMemo(
    () => [
      ['pages.dataQuality.overview.metric.passRate', 'pages.dataQuality.overview.metric.passRateDesc'],
      ['pages.dataQuality.overview.metric.issueRate', 'pages.dataQuality.overview.metric.issueRateDesc'],
      ['pages.dataQuality.overview.metric.activeMonitors', 'pages.dataQuality.overview.metric.activeMonitorsDesc'],
      ['pages.dataQuality.overview.metric.executedRules', 'pages.dataQuality.overview.metric.executedRulesDesc'],
      ['pages.dataQuality.overview.metric.issueExecutions', 'pages.dataQuality.overview.metric.issueExecutionsDesc'],
      ['pages.dataQuality.overview.metric.issueTables', 'pages.dataQuality.overview.metric.issueTablesDesc'],
      ['pages.dataQuality.overview.metric.affectedColumns', 'pages.dataQuality.overview.metric.affectedColumnsDesc'],
      ['pages.dataQuality.overview.metric.top3', 'pages.dataQuality.overview.metric.top3Desc'],
    ],
    [],
  );

  return (
    <div className="w-[420px] max-w-[calc(100vw-88px)] overflow-hidden rounded-xl bg-white">
      <div className="flex items-center gap-3 border-b border-solid border-[#eef0f2] px-4 py-3.5">
        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-[var(--yak-brand-color-soft)] text-[var(--yak-brand-color)]">
          <Info size={15} />
        </span>
        <div className="min-w-0">
          <div className="text-[14px] font-semibold text-[#161823]">
            {intl.formatMessage({ id: 'pages.dataQuality.overview.metricExplanation' })}
          </div>
          <div className="mt-0.5 text-[11px] text-[#98a2b3]">
            {intl.formatMessage({
              id: 'pages.dataQuality.overview.metricExplanationSubtitle',
            })}
          </div>
        </div>
      </div>

      <div className="max-h-[min(560px,68vh)] overflow-y-auto p-4">
        <div className="rounded-lg bg-[#f7f8fa] px-3.5 py-3 text-[11px] leading-5 text-[#667085]">
          {intl.formatMessage({
            id: 'pages.dataQuality.overview.metricExplanationIntro',
          })}
        </div>

        <div className="mt-3 grid grid-cols-2 gap-2.5">
          {explanations.map(([labelId, descriptionId]) => (
            <div
              key={labelId}
              className="rounded-lg border border-solid border-[#eceef2] bg-white px-3 py-2.5"
            >
              <div className="text-[12px] font-semibold text-[#30343b]">
                {intl.formatMessage({ id: labelId })}
              </div>
              <div className="mt-1 text-[11px] leading-[18px] text-[#8a9099]">
                {intl.formatMessage({ id: descriptionId })}
              </div>
            </div>
          ))}
        </div>

        <div className="mt-3 rounded-lg border border-solid border-[#eceef2] px-3.5 py-3 text-[11px] leading-5 text-[#98a2b3]">
          {intl.formatMessage({
            id: 'pages.dataQuality.overview.metricExplanationRadar',
          })}
        </div>
      </div>
    </div>
  );
};

export default function DataQualityOverviewPage() {
  const intl = useIntl();
  const overview = useQualityOverviewPage();
  const executionTabs = useMemo(
    () => [
      {
        key: 'execution',
        label: intl.formatMessage({ id: 'pages.dataQuality.overview.tab.execution' }),
      },
      {
        key: 'monitor',
        label: intl.formatMessage({ id: 'pages.dataQuality.overview.tab.monitor' }),
      },
      {
        key: 'rule',
        label: intl.formatMessage({ id: 'pages.dataQuality.overview.tab.rule' }),
      },
    ],
    [intl],
  );
  const issueTabs = useMemo(
    () => [
      {
        key: 'issue',
        label: intl.formatMessage({ id: 'pages.dataQuality.overview.tab.issue' }),
      },
      {
        key: 'dimension',
        label: intl.formatMessage({ id: 'pages.dataQuality.overview.tab.dimension' }),
      },
    ],
    [intl],
  );

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div
        className="min-h-[calc(100vh-64px)] bg-[#f6f7f9] text-[#161823]"
        style={BRAND_CSS_VARIABLES}
      >
        <div className="mx-auto w-full max-w-[1900px] space-y-5 px-4 py-4 lg:px-5">
          <QualityRadarOverview
            periodText={formatPeriodText(overview.radarRange, intl)}
            overview={overview.radarOverview}
            loading={overview.radarLoading}
          />

          <QualityMetricSection
            title={intl.formatMessage({ id: 'pages.dataQuality.overview.qualityData' })}
            tabs={executionTabs}
            defaultTab="execution"
            section="quality"
            range={overview.qualityRange}
            overview={overview.qualityOverview}
            loading={overview.qualityLoading}
            onRangeChange={overview.setQualityRange}
          />

          <QualityMetricSection
            title={intl.formatMessage({ id: 'pages.dataQuality.overview.issueData' })}
            tabs={issueTabs}
            defaultTab="issue"
            section="issue"
            range={overview.issueRange}
            overview={overview.issueOverview}
            loading={overview.issueLoading}
            onRangeChange={overview.setIssueRange}
          />
        </div>

        <Popover
          trigger="hover"
          placement="leftTop"
          arrow={false}
          mouseEnterDelay={0.12}
          mouseLeaveDelay={0.15}
          content={<MetricExplanationPanel />}
          overlayInnerStyle={{ padding: 0, borderRadius: 12 }}
        >
          <button
            type="button"
            aria-label={intl.formatMessage({
              id: 'pages.dataQuality.overview.aria.metricExplanation',
            })}
            className="fixed right-0 top-[42%] z-30 hidden -translate-y-1/2 flex-col items-center gap-1.5 rounded-l-lg border border-r-0 border-solid border-[#eceef2] bg-white px-2 py-3 text-[11px] font-medium text-[#667085] shadow-[0_4px_16px_rgba(16,24,40,0.05)] transition-[border-color,color,background-color] hover:border-[var(--yak-brand-color-border)] hover:bg-[#fffafb] hover:text-[var(--yak-brand-color)] 2xl:flex"
          >
            <Info size={13} />
            <span style={{ writingMode: 'vertical-rl' }} className="tracking-[0.12em]">
              {intl.formatMessage({ id: 'pages.dataQuality.overview.metricExplanation' })}
            </span>
          </button>
        </Popover>
      </div>
    </ConfigProvider>
  );
}
