import { BRAND_THEME } from '@/styles/brand';
import { history, useIntl } from '@umijs/max';
import { ConfigProvider, Pagination } from 'antd';
import { useEffect } from 'react';

import DataSourceTreePane from '../table-config/components/DataSourceTreePane';
import { useDataSourceTree } from '../table-config/hooks/useDataSourceTree';
import ExecutionRecordTable from './components/ExecutionRecordTable';
import ExecutionToolbar from './components/ExecutionToolbar';
import { useQualityExecutionPage } from './hooks/useQualityExecutionPage';

const ExecutionPage = () => {
  const intl = useIntl();
  const {
    dataSourceId,
    selectedNodeKey,
    sourceNodes,
    treeLoading,
    leftWidth,
    collapsed,
    setCollapsed,
    loadSourceTree,
    selectNode,
    startResize,
  } = useDataSourceTree();
  const execution = useQualityExecutionPage(dataSourceId);

  useEffect(() => {
    void loadSourceTree();
  }, [loadSourceTree]);

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[640px] flex-col overflow-hidden bg-white">
        <header className="shrink-0 border-b border-[#e8e9ec] px-5 py-3">
          <h1 className="m-0 text-[22px] font-semibold leading-8 text-[#161823]">
            {intl.formatMessage({ id: 'pages.dataQuality.execution.title' })}
          </h1>
        </header>

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <DataSourceTreePane
            sourceNodes={sourceNodes}
            treeLoading={treeLoading}
            selectedNodeKey={selectedNodeKey}
            leftWidth={leftWidth}
            collapsed={collapsed}
            onSelect={(keys) => {
              const key = keys[0];
              if (key) selectNode(String(key));
            }}
            onResizeStart={startResize}
            onCollapsedChange={setCollapsed}
          />

          <main className="flex min-w-0 flex-1 flex-col overflow-hidden px-4 py-3">
            <ExecutionToolbar
              viewMode={execution.viewMode}
              onViewModeChange={execution.setViewMode}
              keywordDraft={execution.keywordDraft}
              onKeywordDraftChange={execution.setKeywordDraft}
              dateRange={execution.dateRange}
              onDateRangeChange={execution.setDateRange}
              advancedOpen={execution.advancedOpen}
              onAdvancedOpenChange={execution.setAdvancedOpen}
              advancedFilterCount={execution.advancedFilterCount}
              draftFilters={execution.draftFilters}
              onDraftFiltersChange={execution.setDraftFilters}
              onSearch={execution.applySearch}
              onApplyAdvanced={execution.applyAdvancedSearch}
              onReset={execution.resetFilters}
              onRefresh={() =>
                void execution.load(execution.current, execution.pageSize)
              }
            />

            <div className="min-h-0 flex-1 overflow-auto pt-2">
              <ExecutionRecordTable
                executionRecords={execution.executionRecords}
                ruleRecords={execution.ruleRecords}
                loading={execution.loading}
                mode={execution.viewMode}
                onOpenExecution={(executionNo) =>
                  history.push(`/data-quality/execution/${executionNo}`)
                }
                onOpenMonitor={(monitorId) =>
                  history.push(`/data-quality/monitor/${monitorId}`)
                }
              />
            </div>

            <div className="flex shrink-0 justify-end border-t border-[#f0f2f5] pt-3">
              <Pagination
                size="small"
                current={execution.current}
                pageSize={execution.pageSize}
                total={execution.total}
                showSizeChanger
                showTotal={(value) =>
                  intl.formatMessage(
                    { id: 'pages.dataQuality.execution.total' },
                    { count: value },
                  )
                }
                onChange={execution.changePage}
              />
            </div>
          </main>
        </div>
      </div>
    </ConfigProvider>
  );
};

export default ExecutionPage;
