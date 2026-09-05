import type { AnalysisSpec } from '@/components/analysis/model';
import { useIntl } from '@umijs/max';
import { Select } from 'antd';
import { Database } from 'lucide-react';
import { ChartFieldPanel } from './chart-field-panel';
import type { PublishedDataset } from './model';

export function ChartDataColumn({
  dataset,
  datasets,
  spec,
  editable,
  onDatasetChange,
  onSpecPatch,
}: {
  dataset?: PublishedDataset;
  datasets: PublishedDataset[];
  spec?: AnalysisSpec;
  editable: boolean;
  onDatasetChange?: (datasetId: string) => void;
  onSpecPatch?: (patch: Partial<AnalysisSpec>) => void;
}) {
  const intl = useIntl();

  return (
    <section className="chart-data-column flex w-[244px] shrink-0 flex-col border-r border-[#e3e6ea] bg-white">
      <div className="shrink-0 border-b border-[#eceef1] px-3 py-3.5">
        <Select
          showSearch
          size="middle"
          variant="filled"
          value={dataset?.id}
          disabled={!editable}
          optionFilterProp="label"
          className="chart-dataset-select w-full"
          placeholder={intl.formatMessage({ id: 'pages.dashboard.editor.fields.selectDataset' })}
          suffixIcon={<Database size={13} className="text-[#667085]" />}
          options={datasets.map((item) => ({ label: item.name, value: item.id }))}
          onChange={onDatasetChange}
        />
        <div className="mt-2 truncate px-0.5 text-[10px] font-medium text-[#7a818c]">
          {dataset
            ? intl.formatMessage(
              { id: 'pages.dashboard.editor.fields.catalog' },
              { name: dataset.name },
            )
            : intl.formatMessage({ id: 'pages.dashboard.editor.fields.sourceUnavailable' })}
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-hidden">
        <ChartFieldPanel
          dataset={dataset}
          spec={spec}
          editable={editable && Boolean(dataset)}
          onSpecPatch={onSpecPatch}
        />
      </div>

      <style>{`
        .chart-data-column > .min-h-0 > section {
          height: 100%;
          border-right: 0 !important;
        }
        .chart-data-column > .min-h-0 > section > div:first-child {
          display: none;
        }
        .chart-data-column .chart-dataset-select .ant-select-selector {
          min-height: 34px !important;
          border-radius: 7px !important;
        }
        .chart-data-column .chart-dataset-select .ant-select-selection-item,
        .chart-data-column .chart-dataset-select .ant-select-selection-placeholder {
          font-size: 12px !important;
        }
        .chart-data-column .chart-dataset-select .ant-select-selection-item {
          font-weight: 600 !important;
          color: #344054 !important;
        }
      `}</style>
    </section>
  );
}
