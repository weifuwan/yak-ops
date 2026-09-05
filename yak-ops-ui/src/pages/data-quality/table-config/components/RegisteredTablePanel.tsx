import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton } from '@/components/ui';
import type { TableAssetView } from '@/services/data-quality';
import { useIntl } from '@umijs/max';
import { Input } from 'antd';
import { Search } from 'lucide-react';

import type { QualityDataSourceNode } from '../types';
import RegisteredQualityTableTable from './RegisteredQualityTableTable';

interface RegisteredTablePanelProps {
  dataSourceId?: number;
  selectedSourceNode?: QualityDataSourceNode;
  assets: TableAssetView[];
  assetTotal: number;
  assetCurrent: number;
  keyword: string;
  assetLoading: boolean;
  onAssetCurrentChange: (current: number) => void;
  onKeywordChange: (keyword: string) => void;
  onOpenRegister: () => void;
  onOpenRuleManagement: (record: TableAssetView) => void;
  onCreateMonitor: (record: TableAssetView) => void;
}

const RegisteredTablePanel = ({
  dataSourceId,
  selectedSourceNode,
  assets,
  assetTotal,
  assetCurrent,
  keyword,
  assetLoading,
  onAssetCurrentChange,
  onKeywordChange,
  onOpenRegister,
  onOpenRuleManagement,
  onCreateMonitor,
}: RegisteredTablePanelProps) => {
  const intl = useIntl();
  return (
    <main className="min-w-0 flex-1 overflow-hidden px-4 py-3">
      <div className="flex h-full flex-col overflow-hidden">
        <div className="mb-3 flex shrink-0 flex-col gap-2">
          <div className="flex min-w-0 items-center gap-2">
            <Input
              allowClear
              variant="filled"
              value={keyword}
              prefix={<Search size={14} className="text-[#98a2b3]" />}
              placeholder={intl.formatMessage({
                id: 'pages.dataQuality.tableConfig.searchPlaceholder',
              })}
              className="min-w-[260px] flex-1"
              onChange={(event) => onKeywordChange(event.target.value)}
            />

            <YakButton
              type="primary"
              disabled={!dataSourceId}
              className="shrink-0"
              onClick={onOpenRegister}
            >
              {intl.formatMessage({
                id: 'pages.dataQuality.tableConfig.registerTable',
              })}
            </YakButton>
          </div>

          {selectedSourceNode ? (
            <div className="flex min-h-6 flex-wrap items-center gap-2">
              <div className="inline-flex h-6 items-center rounded-sm bg-[#f5f5f6] px-2 text-xs text-[#667085]">
                {intl.formatMessage({
                  id: 'pages.dataQuality.tableConfig.sourceType',
                })}
                <span className="ml-1 font-medium text-[#475467]">
                  {selectedSourceNode.dataSourceType}
                </span>
              </div>

              <div className="inline-flex h-6 min-w-0 items-center rounded-sm bg-[#f5f5f6] px-2 text-xs text-[#667085]">
                {intl.formatMessage({ id: 'pages.dataQuality.tableConfig.source' })}
                <span className="ml-1 max-w-[260px] truncate font-medium text-[#475467]">
                  {selectedSourceNode.dataSourceName}
                </span>
              </div>
            </div>
          ) : null}
        </div>

        {!dataSourceId ? (
          <div className="flex min-h-0 flex-1 items-center justify-center">
            <YakOpsEmpty
              width={180}
              height={124}
              title={intl.formatMessage({
                id: 'pages.dataQuality.tableConfig.selectSource',
              })}
              description={intl.formatMessage({
                id: 'pages.dataQuality.tableConfig.selectSourceDesc',
              })}
            />
          </div>
        ) : (
          <RegisteredQualityTableTable
            assets={assets}
            total={assetTotal}
            current={assetCurrent}
            loading={assetLoading}
            onCurrentChange={onAssetCurrentChange}
            onOpenRuleManagement={onOpenRuleManagement}
            onCreateMonitor={onCreateMonitor}
          />
        )}
      </div>
    </main>
  );
};

export default RegisteredTablePanel;
