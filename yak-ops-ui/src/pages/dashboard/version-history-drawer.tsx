import { useIntl } from '@umijs/max';
import { Button, Drawer, Empty, Spin } from 'antd';
import { RotateCcw } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { fetchDashboardVersion } from './dashboard-service';
import type { DashboardVersionDetail, DashboardVersionSummary } from './model';

const formatTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 19) : '-';

export function DashboardVersionHistoryDrawer({
  open,
  dashboardId,
  versions,
  currentVersionNo,
  publishedVersionNo,
  busy,
  onClose,
  onRestore,
}: {
  open: boolean;
  dashboardId: string;
  versions: DashboardVersionSummary[];
  currentVersionNo?: number;
  publishedVersionNo?: number;
  busy: boolean;
  onClose: () => void;
  onRestore: (versionNo: number) => void;
}) {
  const intl = useIntl();
  const [selectedVersionNo, setSelectedVersionNo] = useState<number>();
  const [detail, setDetail] = useState<DashboardVersionDetail>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open) return;
    setSelectedVersionNo(currentVersionNo ?? versions[0]?.versionNo);
  }, [currentVersionNo, open, versions]);

  useEffect(() => {
    if (!open || !selectedVersionNo) {
      setDetail(undefined);
      return;
    }
    let active = true;
    setLoading(true);
    void fetchDashboardVersion(dashboardId, selectedVersionNo)
      .then((value) => {
        if (active) setDetail(value);
      })
      .catch(() => {
        if (active) setDetail(undefined);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dashboardId, open, selectedVersionNo]);

  const selectedVersion = useMemo(
    () => versions.find((item) => item.versionNo === selectedVersionNo),
    [selectedVersionNo, versions],
  );

  return (
    <Drawer
      title={intl.formatMessage({ id: 'pages.dashboard.editor.version.title' })}
      width={680}
      open={open}
      onClose={onClose}
      destroyOnClose
      styles={{ body: { padding: 0 } }}
    >
      <div className="flex h-full min-h-[520px]">
        <div className="w-[190px] shrink-0 overflow-y-auto border-r border-[#edf0f3] bg-[#fafbfc] p-2">
          {versions.map((item) => {
            const selected = item.versionNo === selectedVersionNo;
            const current = item.versionNo === currentVersionNo;
            const published = item.versionNo === publishedVersionNo;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setSelectedVersionNo(item.versionNo)}
                className={[
                  'mb-1 w-full rounded-[6px] border-0 px-3 py-2.5 text-left transition-colors',
                  selected ? 'bg-white shadow-[0_0_0_1px_#e1e5ea]' : 'bg-transparent hover:bg-white',
                ].join(' ')}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-[12px] font-semibold text-[#344054]">V{item.versionNo}</span>
                  <div className="flex items-center gap-1">
                    {current ? (
                      <span className="rounded-[3px] bg-[#f2f4f7] px-1.5 py-0.5 text-[9px] text-[#475467]">
                        {intl.formatMessage({ id: 'pages.dashboard.editor.version.currentDraft' })}
                      </span>
                    ) : null}
                    {published ? (
                      <span className="rounded-[3px] bg-[#ecfdf3] px-1.5 py-0.5 text-[9px] text-[#1d7a4b]">
                        {intl.formatMessage({ id: 'pages.dashboard.editor.version.published' })}
                      </span>
                    ) : null}
                  </div>
                </div>
                <div className="mt-1 truncate text-[10px] text-[#667085]">{item.name}</div>
                <div className="mt-1 text-[9px] text-[#98a2b3]">{formatTime(item.createTime)}</div>
              </button>
            );
          })}
        </div>

        <div className="min-w-0 flex-1 overflow-y-auto p-5">
          {loading ? (
            <div className="flex h-[360px] items-center justify-center">
              <Spin size="small" />
            </div>
          ) : detail && selectedVersion ? (
            <>
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-[15px] font-semibold text-[#161823]">V{selectedVersion.versionNo}</span>
                    {selectedVersion.versionNo === publishedVersionNo ? (
                      <span className="rounded-[3px] bg-[#ecfdf3] px-2 py-0.5 text-[10px] text-[#1d7a4b]">
                        {intl.formatMessage({ id: 'pages.dashboard.editor.version.published' })}
                      </span>
                    ) : null}
                  </div>
                  <div className="mt-1 truncate text-[12px] text-[#475467]">{detail.version.name}</div>
                  <div className="mt-1 text-[10px] text-[#98a2b3]">
                    {intl.formatMessage(
                      { id: 'pages.dashboard.editor.version.createdAt' },
                      { time: formatTime(detail.version.createTime) },
                    )}
                  </div>
                </div>

                <Button
                  size="small"
                  icon={<RotateCcw size={12} />}
                  disabled={busy || selectedVersion.versionNo === currentVersionNo}
                  onClick={() => onRestore(selectedVersion.versionNo)}
                >
                  {intl.formatMessage({
                    id: selectedVersion.versionNo === currentVersionNo
                      ? 'pages.dashboard.editor.version.currentDraft'
                      : 'pages.dashboard.editor.version.restoreDraft',
                  })}
                </Button>
              </div>

              <div className="mt-5 grid grid-cols-4 gap-2">
                <Metric
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.version.widgets' })}
                  value={detail.widgets.length}
                />
                <Metric
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.version.filters' })}
                  value={detail.globalFilters.length}
                />
                <Metric
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.version.interactions' })}
                  value={detail.interactions.length}
                />
                <Metric label="Dataset" value={detail.version.activeDatasetId || '-'} />
              </div>

              <div className="mt-5">
                <div className="mb-2 text-[11px] font-medium text-[#667085]">
                  {intl.formatMessage({ id: 'pages.dashboard.editor.version.layout' })}
                </div>
                <div
                  className="grid h-[190px] overflow-hidden rounded-[7px] border border-[#e5e7eb] bg-[#fafbfc] p-2"
                  style={{
                    gridTemplateColumns: 'repeat(24, minmax(0, 1fr))',
                    gridAutoRows: '6px',
                    gap: '2px',
                  }}
                >
                  {detail.widgets.map((widget) => {
                    const fallbackTitle = intl.formatMessage({ id: 'pages.dashboard.editor.version.chartFallback' });
                    const unnamedTitle = intl.formatMessage({ id: 'pages.dashboard.editor.unnamedChart' });
                    return (
                      <div
                        key={widget.id}
                        title={widget.title || fallbackTitle}
                        className="overflow-hidden rounded-[3px] border border-[#dfe3e8] bg-white px-1.5 py-1 text-[9px] text-[#667085] shadow-[0_1px_2px_rgba(16,24,40,.03)]"
                        style={{
                          gridColumn: `${widget.x + 1} / span ${widget.w}`,
                          gridRow: `${widget.y + 1} / span ${Math.max(widget.h, 1)}`,
                        }}
                      >
                        <span className="block truncate">{widget.title || unnamedTitle}</span>
                      </div>
                    );
                  })}
                </div>
              </div>

              <div className="mt-5 border-t border-[#edf0f3] pt-4">
                <div className="text-[11px] font-medium text-[#667085]">
                  {intl.formatMessage({ id: 'pages.dashboard.editor.version.descriptionTitle' })}
                </div>
                <div className="mt-1 text-[11px] leading-5 text-[#98a2b3]">
                  {intl.formatMessage({ id: 'pages.dashboard.editor.version.description' })}
                </div>
              </div>
            </>
          ) : (
            <div className="flex h-[360px] items-center justify-center">
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={intl.formatMessage({ id: 'pages.dashboard.editor.version.loadFailed' })}
              />
            </div>
          )}
        </div>
      </div>
    </Drawer>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-[6px] border border-[#edf0f3] bg-white px-3 py-2.5">
      <div className="text-[9px] text-[#98a2b3]">{label}</div>
      <div className="mt-1 truncate text-[12px] font-semibold text-[#344054]" title={String(value)}>{value}</div>
    </div>
  );
}
