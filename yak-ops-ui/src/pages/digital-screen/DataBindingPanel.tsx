import type { ScreenAggregation, ScreenComponent } from '@/components/screen-engine';
import type { DatasetField, PublishedDataset } from '@/components/analysis/model';
import { Button, Select, Spin } from 'antd';
import { Database, Plus, Trash2, Unlink } from 'lucide-react';
import type { DigitalScreenComponentBinding } from './model';
import {
  SCREEN_AGGREGATION_LABELS,
  isBindableScreenComponent,
} from './screen-data-service';

const COMPONENT_LABELS: Record<ScreenComponent['type'], string> = {
  metric: '指标卡',
  line: '折线图',
  bar: '柱状图',
  pie: '饼图',
  table: '表格',
  text: '文本',
};

const aggregationOptions = Object.entries(SCREEN_AGGREGATION_LABELS).map(([value, label]) => ({
  value: value as ScreenAggregation,
  label,
}));

const defaultAggregation = (field?: DatasetField): ScreenAggregation => (
  field?.dataType === 'number' ? 'SUM' : 'COUNT'
);

export interface DataBindingPanelProps {
  component?: ScreenComponent;
  binding?: DigitalScreenComponentBinding;
  datasets: PublishedDataset[];
  datasetsLoading?: boolean;
  datasetsError?: string;
  querying?: boolean;
  queryError?: string;
  onChange: (binding?: DigitalScreenComponentBinding) => void;
}

export function DataBindingPanel({
  component,
  binding,
  datasets,
  datasetsLoading = false,
  datasetsError,
  querying = false,
  queryError,
  onChange,
}: DataBindingPanelProps) {
  if (!component) {
    return (
      <div className="rounded-[7px] border border-dashed border-[#dfe2e6] px-4 py-5 text-center">
        <div className="text-[12px] font-medium text-[#667085]">请选择一个组件</div>
        <div className="mt-1 text-[11px] leading-[18px] text-[#a3a8b0]">
          点击左侧大屏中的图表或指标卡进行数据绑定。
        </div>
      </div>
    );
  }

  if (!isBindableScreenComponent(component)) {
    return (
      <div className="rounded-[7px] bg-[#f6f7f8] px-4 py-4 text-[12px] leading-5 text-[#8a9099]">
        文本组件由模板负责展示，不需要绑定数据集。
      </div>
    );
  }

  const dataset = datasets.find((item) => item.id === binding?.datasetId);
  const fields = dataset?.fields ?? [];
  const dimensionLimit = component.type === 'table' ? undefined : 1;
  const metricLimit = component.type === 'line' || component.type === 'bar' || component.type === 'table'
    ? 4
    : 1;
  const dimensionOptions = fields.map((field) => ({
    value: field.key,
    label: `${field.label} · ${field.dataType}`,
  }));

  const updateDimensions = (values: string[]) => {
    if (!binding) return;
    onChange({
      ...binding,
      dimensions: dimensionLimit ? values.slice(0, dimensionLimit) : values,
    });
  };

  const updateMetric = (
    index: number,
    patch: Partial<DigitalScreenComponentBinding['metrics'][number]>,
  ) => {
    if (!binding) return;
    onChange({
      ...binding,
      metrics: binding.metrics.map((metric, metricIndex) => (
        metricIndex === index ? { ...metric, ...patch } : metric
      )),
    });
  };

  const addMetric = () => {
    if (!binding || binding.metrics.length >= metricLimit) return;
    const used = new Set(binding.metrics.map((metric) => metric.field));
    const candidate = fields.find((field) => field.role === 'metric' && !used.has(field.key))
      || fields.find((field) => !used.has(field.key));
    if (!candidate) return;
    onChange({
      ...binding,
      metrics: [...binding.metrics, {
        field: candidate.key,
        aggregation: defaultAggregation(candidate),
      }],
    });
  };

  const removeMetric = (index: number) => {
    if (!binding) return;
    onChange({
      ...binding,
      metrics: binding.metrics.filter((_, metricIndex) => metricIndex !== index),
    });
  };

  return (
    <div>
      <div className="rounded-[7px] bg-[#f6f7f8] px-3 py-3">
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="truncate text-[12px] font-medium text-[#444950]">
              {component.title || component.id}
            </div>
            <div className="mt-1 text-[11px] text-[#98a2b3]">
              {COMPONENT_LABELS[component.type]} · {component.id}
            </div>
          </div>
          {querying ? <Spin size="small" /> : null}
        </div>
      </div>

      <label className="mt-4 block text-[12px] text-[#667085]">
        数据集
        <Select
          allowClear
          showSearch
          loading={datasetsLoading}
          value={binding?.datasetId}
          className="mt-2 w-full"
          variant="filled"
          placeholder="选择已上线数据集"
          optionFilterProp="label"
          options={datasets.map((item) => ({
            value: item.id,
            label: item.name,
          }))}
          onChange={(datasetId?: string) => {
            if (!datasetId) {
              onChange(undefined);
              return;
            }
            onChange({ datasetId, dimensions: [], metrics: [] });
          }}
        />
      </label>

      {datasetsError ? (
        <div className="mt-2 rounded-[6px] bg-[#fff4f4] px-3 py-2 text-[11px] leading-5 text-[#c93b3b]">
          {datasetsError}
        </div>
      ) : null}

      {binding && dataset ? (
        <>
          {component.type !== 'metric' ? (
            <label className="mt-4 block text-[12px] text-[#667085]">
              {component.type === 'table' ? '维度' : '维度（最多 1 个）'}
              <Select
                mode="multiple"
                allowClear
                showSearch
                value={binding.dimensions}
                className="mt-2 w-full"
                variant="filled"
                placeholder="选择维度字段"
                optionFilterProp="label"
                options={dimensionOptions}
                onChange={updateDimensions}
              />
            </label>
          ) : null}

          <div className="mt-4">
            <div className="flex items-center justify-between">
              <span className="text-[12px] text-[#667085]">指标</span>
              <Button
                type="text"
                size="small"
                icon={<Plus size={13} />}
                disabled={!fields.length || binding.metrics.length >= metricLimit}
                onClick={addMetric}
                className="px-1 text-[11px]"
              >
                添加
              </Button>
            </div>

            <div className="mt-2 space-y-2">
              {binding.metrics.map((metric, index) => {
                const currentField = fields.find((field) => field.key === metric.field);
                const used = new Set(binding.metrics
                  .filter((_, metricIndex) => metricIndex !== index)
                  .map((item) => item.field));
                return (
                  <div key={`${index}-${metric.field}`} className="rounded-[7px] border border-[#eceef1] p-2">
                    <div className="flex items-center gap-2">
                      <Select
                        showSearch
                        value={metric.field}
                        className="min-w-0 flex-1"
                        variant="filled"
                        optionFilterProp="label"
                        options={fields.map((field) => ({
                          value: field.key,
                          label: `${field.label} · ${field.dataType}`,
                          disabled: used.has(field.key),
                        }))}
                        onChange={(fieldId: string) => {
                          const field = fields.find((item) => item.key === fieldId);
                          updateMetric(index, {
                            field: fieldId,
                            aggregation: defaultAggregation(field),
                          });
                        }}
                      />
                      <Button
                        type="text"
                        size="small"
                        icon={<Trash2 size={13} />}
                        onClick={() => removeMetric(index)}
                      />
                    </div>
                    <Select
                      value={metric.aggregation}
                      className="mt-2 w-full"
                      variant="filled"
                      options={aggregationOptions}
                      onChange={(aggregation: ScreenAggregation) => updateMetric(index, { aggregation })}
                    />
                    {currentField && currentField.dataType !== 'number' && metric.aggregation !== 'COUNT' && metric.aggregation !== 'COUNT_DISTINCT' ? (
                      <div className="mt-1 text-[10px] leading-4 text-[#c27b2b]">
                        非数值字段建议使用计数或去重计数。
                      </div>
                    ) : null}
                  </div>
                );
              })}

              {!binding.metrics.length ? (
                <button
                  type="button"
                  onClick={addMetric}
                  className="flex w-full items-center justify-center gap-1 rounded-[7px] border border-dashed border-[#dfe2e6] bg-white py-3 text-[11px] text-[#8a9099] hover:bg-[#fafafa]"
                >
                  <Plus size={13} /> 添加指标
                </button>
              ) : null}
            </div>
          </div>

          <div className="mt-4 flex items-center justify-between rounded-[7px] bg-[#f8f9fa] px-3 py-2.5">
            <div className="flex min-w-0 items-center gap-2 text-[11px] text-[#667085]">
              <Database size={13} />
              <span className="truncate">{dataset.name}</span>
            </div>
            <Button
              type="text"
              size="small"
              icon={<Unlink size={12} />}
              className="px-1 text-[11px] text-[#8a9099]"
              onClick={() => onChange(undefined)}
            >
              清除
            </Button>
          </div>

          {queryError ? (
            <div className="mt-2 rounded-[6px] bg-[#fff4f4] px-3 py-2 text-[11px] leading-5 text-[#c93b3b]">
              {queryError}
            </div>
          ) : null}
        </>
      ) : null}

      {binding && !dataset && !datasetsLoading ? (
        <div className="mt-3 rounded-[6px] bg-[#fff8eb] px-3 py-2 text-[11px] leading-5 text-[#a66a16]">
          原绑定的数据集已下线或不存在，请重新选择数据集。
        </div>
      ) : null}
    </div>
  );
}
