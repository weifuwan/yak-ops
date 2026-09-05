import YakButton from '@/components/YakButton';
import { useIntl } from '@umijs/max';
import { Input, Select } from 'antd';
import { Search } from 'lucide-react';

import { formatQualityDimension } from '../../i18n';
import type { ExecutionAdvancedFilterState } from '../hooks/useQualityExecutionPage';

const DIMENSION_VALUES = ['完整性', '唯一性', '有效性', '准确性', '自定义'];

interface ExecutionAdvancedFilterProps {
  value: ExecutionAdvancedFilterState;
  onChange: (value: ExecutionAdvancedFilterState) => void;
  onApply: () => void;
  onReset: () => void;
}

export default function ExecutionAdvancedFilter({
  value,
  onChange,
  onApply,
  onReset,
}: ExecutionAdvancedFilterProps) {
  const intl = useIntl();
  const patch = (next: Partial<ExecutionAdvancedFilterState>) =>
    onChange({ ...value, ...next });

  return (
    <div className="w-[340px]">
      <div className="mb-3 text-[13px] font-semibold text-[#161823]">
        {intl.formatMessage({ id: 'pages.dataQuality.execution.advancedTitle' })}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="col-span-2">
          <div className="mb-1.5 text-xs text-[#667085]">
            {intl.formatMessage({ id: 'pages.dataQuality.execution.object' })}
          </div>
          <Input
            allowClear
            variant="filled"
            value={value.objectKeyword}
            onChange={(event) => patch({ objectKeyword: event.target.value })}
            onPressEnter={onApply}
            prefix={<Search size={14} className="text-[#98a2b3]" />}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.execution.objectPlaceholder',
            })}
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">
            {intl.formatMessage({
              id: 'pages.dataQuality.execution.executionStatus',
            })}
          </div>
          <Select
            allowClear
            variant="filled"
            value={value.executionStatus}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.execution.allStatus',
            })}
            className="w-full"
            onChange={(executionStatus) => patch({ executionStatus })}
            options={[
              {
                value: 'WAITING',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.status.waiting',
                }),
              },
              {
                value: 'RUNNING',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.status.running',
                }),
              },
              {
                value: 'SUCCESS',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.status.success',
                }),
              },
              {
                value: 'FAILED',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.status.failed',
                }),
              },
            ]}
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">
            {intl.formatMessage({ id: 'pages.dataQuality.execution.checkResult' })}
          </div>
          <Select
            allowClear
            variant="filled"
            value={value.checkResult}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.execution.allResult',
            })}
            className="w-full"
            onChange={(checkResult) => patch({ checkResult })}
            options={[
              {
                value: 'PASSED',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.status.passed',
                }),
              },
              {
                value: 'NOT_PASSED',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.status.notPassed',
                }),
              },
              {
                value: 'ERROR',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.status.error',
                }),
              },
              {
                value: 'RUNNING',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.status.running',
                }),
              },
            ]}
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">
            {intl.formatMessage({ id: 'pages.dataQuality.execution.issueState' })}
          </div>
          <Select
            allowClear
            variant="filled"
            value={value.hasIssues}
            placeholder={intl.formatMessage({ id: 'pages.dataQuality.execution.all' })}
            className="w-full"
            onChange={(hasIssues) => patch({ hasIssues })}
            options={[
              {
                value: true,
                label: intl.formatMessage({
                  id: 'pages.dataQuality.execution.hasIssues',
                }),
              },
              {
                value: false,
                label: intl.formatMessage({
                  id: 'pages.dataQuality.execution.noIssues',
                }),
              },
            ]}
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">
            {intl.formatMessage({ id: 'pages.dataQuality.execution.dimension' })}
          </div>
          <Select
            allowClear
            variant="filled"
            value={value.dimension}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.execution.allDimensions',
            })}
            className="w-full"
            onChange={(dimension) => patch({ dimension })}
            options={DIMENSION_VALUES.map((dimension) => ({
              value: dimension,
              label: formatQualityDimension(intl, dimension),
            }))}
          />
        </div>

        <div>
          <div className="mb-1.5 text-xs text-[#667085]">
            {intl.formatMessage({ id: 'pages.dataQuality.execution.scope' })}
          </div>
          <Select
            allowClear
            variant="filled"
            value={value.scope}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.execution.allScopes',
            })}
            className="w-full"
            onChange={(scope) => patch({ scope })}
            options={[
              {
                value: 'TABLE',
                label: intl.formatMessage({ id: 'pages.dataQuality.common.scope.table' }),
              },
              {
                value: 'COLUMN',
                label: intl.formatMessage({ id: 'pages.dataQuality.common.scope.column' }),
              },
            ]}
          />
        </div>

        <div className="col-span-2">
          <div className="mb-1.5 text-xs text-[#667085]">
            {intl.formatMessage({ id: 'pages.dataQuality.execution.trigger' })}
          </div>
          <Select
            allowClear
            variant="filled"
            value={value.triggerType}
            placeholder={intl.formatMessage({
              id: 'pages.dataQuality.execution.allTriggers',
            })}
            className="w-full"
            onChange={(triggerType) => patch({ triggerType })}
            options={[
              {
                value: 'MANUAL',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.trigger.manual',
                }),
              },
              {
                value: 'SCHEDULE',
                label: intl.formatMessage({
                  id: 'pages.dataQuality.common.trigger.schedule',
                }),
              },
            ]}
          />
        </div>
      </div>

      <div className="mt-4 flex justify-end gap-2 border-t border-[#f0f1f3] pt-3">
        <YakButton
          size="small"
          type="text"
          className="!text-[#667085]"
          onClick={onReset}
        >
          {intl.formatMessage({ id: 'pages.dataQuality.common.reset' })}
        </YakButton>
        <YakButton size="small" type="primary" onClick={onApply}>
          {intl.formatMessage({ id: 'pages.dataQuality.common.apply' })}
        </YakButton>
      </div>
    </div>
  );
}
