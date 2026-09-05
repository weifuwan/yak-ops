import YakOpsEmpty from '@/components/YakOpsEmpty';
import YakTab from '@/components/YakTab';
import type { TemplateView } from '@/services/data-quality';
import { BRAND_COLOR, BRAND_COLOR_SOFT } from '@/styles/brand';
import { useIntl } from '@umijs/max';
import { Button, Dropdown, Input, Spin, Table, Tag } from 'antd';
import { Copy, Ellipsis, Plus, RefreshCw, Search, Trash2 } from 'lucide-react';

import { dataQualityTableClassName } from '../../components/tableStyle';
import { formatQualityDimension } from '../../i18n';
import type { useQualityTemplateLibrary } from '../hooks/useQualityTemplateLibrary';
import type { TemplateTabKey } from '../hooks/useQualityTemplateLibrary';

type TemplateLibraryModel = ReturnType<typeof useQualityTemplateLibrary>;

interface TemplateLibraryMainProps {
  library: TemplateLibraryModel;
}

const dimensionDescriptionId = (dimension: string) => {
  const ids: Record<string, string> = {
    全部: 'pages.dataQuality.template.dimensionDesc.all',
    完整性: 'pages.dataQuality.template.dimensionDesc.completeness',
    唯一性: 'pages.dataQuality.template.dimensionDesc.uniqueness',
    有效性: 'pages.dataQuality.template.dimensionDesc.validity',
    一致性: 'pages.dataQuality.template.dimensionDesc.consistency',
    准确性: 'pages.dataQuality.template.dimensionDesc.accuracy',
    时效性: 'pages.dataQuality.template.dimensionDesc.timeliness',
    及时性: 'pages.dataQuality.template.dimensionDesc.timeliness',
    规范性: 'pages.dataQuality.template.dimensionDesc.standardization',
    自定义: 'pages.dataQuality.template.dimensionDesc.custom',
  };
  return ids[dimension];
};

export default function TemplateLibraryMain({
  library,
}: TemplateLibraryMainProps) {
  const intl = useIntl();
  const {
    data,
    catalogMeta,
    dimension,
    activeTab,
    setActiveTab,
    keyword,
    setKeyword,
    loading,
    relatedRuleCount,
    loadCatalogMeta,
    loadTemplates,
    openCreateTemplate,
    openEditTemplate,
    removeTemplate,
    openCopy,
  } = library;
  const descriptionId = dimensionDescriptionId(dimension);
  const dimensionLabel = formatQualityDimension(intl, dimension);

  return (
    <main className="min-w-0 flex-1 overflow-hidden px-5 py-4">
      <div className="flex h-full flex-col overflow-hidden">
        <section className="shrink-0">
          <div className="flex items-start justify-between gap-6">
            <div className="min-w-0 flex-1">
              <h2 className="m-0 text-[15px] font-semibold leading-6 text-[#161823]">
                {dimensionLabel}
              </h2>
              <div className="mt-1 max-w-[900px] text-[13px] leading-6 text-[#8a8f99]">
                {descriptionId
                  ? intl.formatMessage({ id: descriptionId })
                  : intl.formatMessage(
                      { id: 'pages.dataQuality.template.dimensionDesc.fallback' },
                      { dimension: dimensionLabel },
                    )}
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <Input
                allowClear
                variant="filled"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                prefix={<Search size={14} className="text-[#98a2b3]" />}
                placeholder={intl.formatMessage({
                  id: 'pages.dataQuality.template.searchPlaceholder',
                })}
                className="w-[330px]"
              />
              <Button
                icon={<RefreshCw size={14} />}
                onClick={() => {
                  void loadCatalogMeta();
                  void loadTemplates();
                }}
              />
            </div>
          </div>

          <div className="mt-2.5 flex flex-wrap items-center gap-2">
            <div className="inline-flex h-7 items-center rounded bg-[#f5f5f6] px-2.5 text-xs text-[#60646f]">
              {intl.formatMessage({ id: 'pages.dataQuality.template.dimensionType' })}
              <strong className="ml-1 text-[#30323b]">
                {intl.formatMessage({ id: 'pages.dataQuality.template.systemDimension' })}
              </strong>
            </div>
            <div className="inline-flex h-7 items-center rounded bg-[#f5f5f6] px-2.5 text-xs text-[#60646f]">
              {intl.formatMessage({ id: 'pages.dataQuality.template.relatedTemplates' })}
              <strong className="ml-1 text-[#30323b]">{data.records.length}</strong>
            </div>
            <div className="inline-flex h-7 items-center rounded bg-[#f5f5f6] px-2.5 text-xs text-[#60646f]">
              {intl.formatMessage({ id: 'pages.dataQuality.template.relatedRules' })}
              <strong className="ml-1 text-[#30323b]">{relatedRuleCount}</strong>
            </div>
          </div>

          <YakTab
            activeKey={activeTab}
            animated={false}
            items={[
              {
                key: 'SYSTEM',
                label: intl.formatMessage(
                  { id: 'pages.dataQuality.template.systemTemplates' },
                  { count: catalogMeta.systemTotal },
                ),
              },
              {
                key: 'CUSTOM',
                label: intl.formatMessage(
                  { id: 'pages.dataQuality.template.customTemplates' },
                  { count: catalogMeta.customTotal },
                ),
              },
            ]}
            onChange={(key) => setActiveTab(key as TemplateTabKey)}
            className="mt-2"
          />
        </section>

        <section className="min-h-0 flex flex-1 flex-col overflow-hidden pt-3">
          {activeTab === 'CUSTOM' ? (
            <div className="mb-3 flex shrink-0 items-center justify-between">
              <Button
                type="primary"
                icon={<Plus size={14} />}
                onClick={openCreateTemplate}
              >
                {intl.formatMessage({ id: 'pages.dataQuality.template.create' })}
              </Button>
              <div className="text-xs text-[#8a8f99]">
                {intl.formatMessage({ id: 'pages.dataQuality.template.changeHint' })}
              </div>
            </div>
          ) : null}

          <div className="min-h-0 flex-1 overflow-auto">
            <Spin spinning={loading}>
              <Table<TemplateView>
                rowKey="id"
                size="small"
                bordered
                pagination={false}
                scroll={{ x: 1080 }}
                className={dataQualityTableClassName()}
                dataSource={data.records}
                locale={{
                  emptyText: (
                    <div className="flex min-h-[220px] items-center justify-center">
                      <YakOpsEmpty
                        width={176}
                        height={120}
                        title={intl.formatMessage({
                          id:
                            activeTab === 'CUSTOM'
                              ? 'pages.dataQuality.template.emptyCustom'
                              : 'pages.dataQuality.template.emptySystem',
                        })}
                        description={intl.formatMessage({
                          id:
                            activeTab === 'CUSTOM'
                              ? 'pages.dataQuality.template.emptyCustomDesc'
                              : 'pages.dataQuality.template.emptySystemDesc',
                        })}
                      />
                    </div>
                  ),
                }}
                columns={[
                  {
                    title: intl.formatMessage({
                      id: 'pages.dataQuality.template.column.nameCode',
                    }),
                    dataIndex: 'name',
                    width: 260,
                    render: (_, record) => (
                      <div className="min-w-0 py-1">
                        <button
                          type="button"
                          className={`block max-w-full truncate border-0 bg-transparent p-0 text-left font-medium ${
                            record.builtin
                              ? 'cursor-default text-[#172033]'
                              : 'cursor-pointer text-[var(--yak-brand-color)]'
                          }`}
                          onClick={() =>
                            !record.builtin && openEditTemplate(record)
                          }
                        >
                          {record.name}
                        </button>
                      </div>
                    ),
                  },
                  {
                    title: intl.formatMessage({
                      id: 'pages.dataQuality.template.column.dimension',
                    }),
                    dataIndex: 'dimension',
                    width: 130,
                    render: (value) => (
                      <Tag className="!m-0 !border-0 !bg-[#f2f4f7] !text-[#667085]">
                        {formatQualityDimension(intl, value)}
                      </Tag>
                    ),
                  },
                  {
                    title: intl.formatMessage({
                      id: 'pages.dataQuality.template.column.scope',
                    }),
                    dataIndex: 'scope',
                    width: 110,
                    render: (value) => (
                      <Tag
                        className="!m-0 !border-0"
                        style={{
                          color: BRAND_COLOR,
                          backgroundColor: BRAND_COLOR_SOFT,
                        }}
                      >
                        {intl.formatMessage({
                          id:
                            value === 'TABLE'
                              ? 'pages.dataQuality.common.scope.table'
                              : 'pages.dataQuality.common.scope.column',
                        })}
                      </Tag>
                    ),
                  },
                  {
                    title: intl.formatMessage({
                      id: 'pages.dataQuality.template.column.ruleCount',
                    }),
                    dataIndex: 'ruleCount',
                    width: 90,
                    render: (value) => (
                      <span className="font-medium text-[#344054]">{value}</span>
                    ),
                  },
                  ...(activeTab === 'CUSTOM'
                    ? [
                        {
                          title: intl.formatMessage({
                            id: 'pages.dataQuality.template.column.folder',
                          }),
                          dataIndex: 'folderName',
                          width: 130,
                          render: (value: string) =>
                            value ||
                            intl.formatMessage({
                              id: 'pages.dataQuality.template.uncategorized',
                            }),
                        },
                      ]
                    : []),
                  {
                    title: intl.formatMessage({
                      id: 'pages.dataQuality.template.column.description',
                    }),
                    dataIndex: 'description',
                    render: (value) => (
                      <div className="line-clamp-2 leading-5 text-[#667085]">
                        {value || '--'}
                      </div>
                    ),
                  },
                  ...(activeTab === 'CUSTOM'
                    ? [
                        {
                          title: intl.formatMessage({
                            id: 'pages.dataQuality.template.column.actions',
                          }),
                          fixed: 'right' as const,
                          width: 150,
                          render: (_: unknown, record: TemplateView) => (
                            <div className="flex items-center gap-1">
                              <Button
                                type="link"
                                size="small"
                                onClick={() => openEditTemplate(record)}
                              >
                                {intl.formatMessage({ id: 'pages.dataQuality.common.edit' })}
                              </Button>
                              <Dropdown
                                menu={{
                                  items: [
                                    {
                                      key: 'copy',
                                      icon: <Copy size={14} />,
                                      label: intl.formatMessage({
                                        id: 'pages.dataQuality.template.copy',
                                      }),
                                      onClick: () => openCopy(record),
                                    },
                                    {
                                      key: 'delete',
                                      danger: true,
                                      icon: <Trash2 size={14} />,
                                      label: intl.formatMessage({
                                        id: 'pages.dataQuality.template.deleteTemplate',
                                      }),
                                      onClick: () => removeTemplate(record),
                                    },
                                  ],
                                }}
                              >
                                <Button
                                  type="text"
                                  size="small"
                                  icon={<Ellipsis size={15} />}
                                />
                              </Dropdown>
                            </div>
                          ),
                        },
                      ]
                    : []),
                ]}
              />
            </Spin>
          </div>
        </section>
      </div>
    </main>
  );
}
