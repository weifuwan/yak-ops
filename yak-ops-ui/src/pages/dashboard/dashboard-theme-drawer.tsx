import YakTab from '@/components/YakTab';
import { useIntl } from '@umijs/max';
import { Button, Collapse, ColorPicker, Drawer } from 'antd';
import {
  BarChart3,
  Box,
  Check,
  LayoutDashboard,
  Moon,
  RotateCcw,
  Sun,
  Table2,
} from 'lucide-react';
import type { ReactNode } from 'react';
import {
  DASHBOARD_THEME_PRESETS,
  hasDashboardThemeOverrides,
  normalizeDashboardTheme,
  resolveDashboardTheme,
  themeFromPreset,
  type ResolvedDashboardTheme,
} from './dashboard-theme';
import type {
  DashboardTheme,
  DashboardThemeCanvas,
  DashboardThemeChart,
  DashboardThemeComponent,
  DashboardThemePresetId,
  DashboardThemeTable,
} from './model';

const ThemePreview = ({ preset }: { preset: ResolvedDashboardTheme }) => {
  const theme = resolveDashboardTheme(themeFromPreset(preset.presetId));
  return (
    <div
      className="h-[96px] overflow-hidden border-b"
      style={{
        backgroundColor: theme.canvas.backgroundColor,
        borderColor: theme.component.borderColor,
      }}
    >
      <div className="grid h-full grid-cols-[1.15fr_.85fr] gap-2 p-2.5">
        <div className="flex min-h-0 flex-col gap-2">
          <div
            className="flex min-h-0 flex-1 items-end gap-1.5 border px-2 pb-2"
            style={{
              backgroundColor: theme.component.backgroundColor,
              borderColor: theme.component.borderColor,
            }}
          >
            {[18, 30, 14, 38, 24].map((height, index) => (
              <span
                key={index}
                className="min-w-0 flex-1"
                style={{
                  height,
                  maxWidth: 9,
                  backgroundColor: theme.chart.palette[index % theme.chart.palette.length],
                }}
              />
            ))}
          </div>
          <div className="flex h-2 items-center gap-1">
            {theme.chart.palette.slice(0, 5).map((color) => (
              <span key={color} className="h-1.5 flex-1" style={{ backgroundColor: color }} />
            ))}
          </div>
        </div>

        <div
          className="flex flex-col justify-between border p-2"
          style={{
            backgroundColor: theme.component.backgroundColor,
            borderColor: theme.component.borderColor,
          }}
        >
          <div
            className="h-1 w-10"
            style={{ backgroundColor: theme.component.mutedTextColor, opacity: 0.55 }}
          />
          <div>
            <div
              className="text-[15px] font-semibold leading-none"
              style={{ color: theme.chart.metricValueColor }}
            >
              86.4%
            </div>
            <div
              className="mt-2 h-1 w-9"
              style={{ backgroundColor: theme.component.mutedTextColor, opacity: 0.35 }}
            />
          </div>
        </div>
      </div>
    </div>
  );
};

const ColorSetting = ({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) => (
  <div className="flex min-h-9 items-center justify-between gap-4 py-1">
    <span className="text-[12px] text-[#344054]">{label}</span>
    <div className="flex shrink-0 items-center gap-2">
      <ColorPicker
        size="small"
        value={value}
        onChange={(color) => onChange(color.toHexString())}
      />
      <span className="w-[68px] font-mono text-[10px] uppercase text-[#667085]">{value}</span>
    </div>
  </div>
);

const SectionLabel = ({
  icon,
  children,
}: {
  icon: ReactNode;
  children: ReactNode;
}) => (
  <div className="flex items-center gap-2 text-[12px] font-semibold text-[#344054]">
    <span className="text-[#667085]">{icon}</span>
    {children}
  </div>
);

export function DashboardThemeDrawer({
  open,
  theme,
  onChange,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  theme?: DashboardTheme;
  onChange: (theme: DashboardTheme) => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const intl = useIntl();
  const normalized = normalizeDashboardTheme(theme);
  const resolved = resolveDashboardTheme(theme);
  const selectedId = resolved.presetId;
  const customized = hasDashboardThemeOverrides(theme);
  const presetName = (preset: Pick<ResolvedDashboardTheme, 'presetId' | 'name'>) =>
    intl.formatMessage({
      id: `pages.dashboard.editor.theme.preset.${preset.presetId}`,
      defaultMessage: preset.name,
    });

  const updateCanvas = (patch: DashboardThemeCanvas) => onChange({
    ...normalized,
    canvas: { ...normalized.canvas, ...patch },
  });
  const updateComponent = (patch: DashboardThemeComponent) => onChange({
    ...normalized,
    component: { ...normalized.component, ...patch },
  });
  const updateChart = (patch: DashboardThemeChart) => onChange({
    ...normalized,
    chart: { ...normalized.chart, ...patch },
  });
  const updateTable = (patch: DashboardThemeTable) => onChange({
    ...normalized,
    table: { ...normalized.table, ...patch },
  });
  const updatePalette = (index: number, color: string) => {
    const palette = [...resolved.chart.palette];
    palette[index] = color;
    updateChart({ palette });
  };

  const presetContent = (
    <div className="grid grid-cols-2 gap-3 pt-1">
      {DASHBOARD_THEME_PRESETS.map((preset) => {
        const selected = selectedId === preset.presetId;
        const Icon = preset.tone === 'dark' ? Moon : Sun;
        return (
          <button
            key={preset.presetId}
            type="button"
            className={[
              'relative overflow-hidden border bg-white p-0 text-left transition-colors',
              selected
                ? 'border-[var(--yak-brand-color)]'
                : 'border-[#e4e7ec] hover:border-[#c8ced8]',
            ].join(' ')}
            onClick={() => onChange(themeFromPreset(preset.presetId as DashboardThemePresetId))}
          >
            <ThemePreview preset={preset} />
            <div className="flex h-9 items-center gap-1.5 px-2.5">
              <Icon
                size={13}
                className={selected ? 'text-[var(--yak-brand-color)]' : 'text-[#667085]'}
              />
              <span className="text-[12px] font-medium text-[#344054]">{presetName(preset)}</span>
            </div>
            {selected ? (
              <span className="absolute right-2 top-2 flex h-5 w-5 items-center justify-center rounded-full bg-[var(--yak-brand-color)] text-white">
                <Check size={12} strokeWidth={2.4} />
              </span>
            ) : null}
          </button>
        );
      })}
    </div>
  );

  const customContent = (
    <div className="pt-1">
      <div className="mb-3 flex h-9 items-center justify-between bg-[#f7f8fa] px-3">
        <div className="flex items-center gap-2 text-[11px] text-[#667085]">
          <span>{intl.formatMessage({ id: 'pages.dashboard.editor.theme.basedOn' })}</span>
          <span className="font-medium text-[#344054]">
            {presetName({ presetId: resolved.presetId, name: resolved.name })}
          </span>
          {customized ? (
            <span className="bg-[var(--yak-brand-color-soft)] px-1.5 py-0.5 text-[9px] text-[var(--yak-brand-color)]">
              {intl.formatMessage({ id: 'pages.dashboard.editor.theme.customized' })}
            </span>
          ) : null}
        </div>
        <Button
          type="text"
          size="small"
          className="!h-7 !px-1.5 !text-[10px] !text-[#667085] hover:!text-[#344054]"
          icon={<RotateCcw size={11} />}
          onClick={() => onChange(themeFromPreset(selectedId))}
        >
          {intl.formatMessage({ id: 'pages.dashboard.editor.theme.restorePreset' })}
        </Button>
      </div>

      <Collapse
        ghost
        size="small"
        defaultActiveKey={['canvas', 'component', 'chart', 'table']}
        className="dashboard-theme-custom"
        items={[
          {
            key: 'canvas',
            label: (
              <SectionLabel icon={<LayoutDashboard size={13} />}>
                {intl.formatMessage({ id: 'pages.dashboard.editor.theme.dashboard' })}
              </SectionLabel>
            ),
            children: (
              <ColorSetting
                label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.canvasBackground' })}
                value={resolved.canvas.backgroundColor}
                onChange={(backgroundColor) => updateCanvas({ backgroundColor })}
              />
            ),
          },
          {
            key: 'component',
            label: (
              <SectionLabel icon={<Box size={13} />}>
                {intl.formatMessage({ id: 'pages.dashboard.editor.theme.component' })}
              </SectionLabel>
            ),
            children: (
              <div>
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.componentBackground' })}
                  value={resolved.component.backgroundColor}
                  onChange={(backgroundColor) => updateComponent({ backgroundColor })}
                />
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.primaryText' })}
                  value={resolved.component.textColor}
                  onChange={(textColor) => updateComponent({ textColor })}
                />
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.secondaryText' })}
                  value={resolved.component.mutedTextColor}
                  onChange={(mutedTextColor) => updateComponent({ mutedTextColor })}
                />
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.componentBorder' })}
                  value={resolved.component.borderColor}
                  onChange={(borderColor) => updateComponent({ borderColor })}
                />
              </div>
            ),
          },
          {
            key: 'chart',
            label: (
              <SectionLabel icon={<BarChart3 size={13} />}>
                {intl.formatMessage({ id: 'pages.dashboard.editor.theme.chart' })}
              </SectionLabel>
            ),
            children: (
              <div>
                <div className="flex min-h-10 items-center justify-between gap-4 py-1">
                  <span className="text-[12px] text-[#344054]">
                    {intl.formatMessage({ id: 'pages.dashboard.editor.theme.palette' })}
                  </span>
                  <div className="flex items-center gap-1.5">
                    {resolved.chart.palette.slice(0, 6).map((color, index) => (
                      <ColorPicker
                        key={`${index}-${color}`}
                        size="small"
                        value={color}
                        onChange={(nextColor) => updatePalette(index, nextColor.toHexString())}
                      />
                    ))}
                  </div>
                </div>
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.axis' })}
                  value={resolved.chart.axisColor}
                  onChange={(axisColor) => updateChart({ axisColor })}
                />
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.grid' })}
                  value={resolved.chart.gridColor}
                  onChange={(gridColor) => updateChart({ gridColor })}
                />
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.metricValue' })}
                  value={resolved.chart.metricValueColor}
                  onChange={(metricValueColor) => updateChart({ metricValueColor })}
                />
              </div>
            ),
          },
          {
            key: 'table',
            label: (
              <SectionLabel icon={<Table2 size={13} />}>
                {intl.formatMessage({ id: 'pages.dashboard.editor.theme.table' })}
              </SectionLabel>
            ),
            children: (
              <div>
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.tableHeader' })}
                  value={resolved.table.headerBackgroundColor}
                  onChange={(headerBackgroundColor) => updateTable({ headerBackgroundColor })}
                />
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.stripedRow' })}
                  value={resolved.table.stripedBackgroundColor}
                  onChange={(stripedBackgroundColor) => updateTable({ stripedBackgroundColor })}
                />
                <ColorSetting
                  label={intl.formatMessage({ id: 'pages.dashboard.editor.theme.hoverBackground' })}
                  value={resolved.table.hoverBackgroundColor}
                  onChange={(hoverBackgroundColor) => updateTable({ hoverBackgroundColor })}
                />
              </div>
            ),
          },
        ]}
      />
    </div>
  );

  return (
    <Drawer
      title={<span className="text-[14px] font-semibold text-[#161823]">
        {intl.formatMessage({ id: 'pages.dashboard.editor.theme.title' })}
      </span>}
      width={420}
      open={open}
      onClose={onCancel}
      mask={false}
      destroyOnClose={false}
      styles={{
        header: { padding: '12px 16px', minHeight: 48 },
        body: { padding: '0 16px 16px', background: '#fff' },
        footer: { padding: '10px 16px' },
      }}
      footer={(
        <div className="flex justify-end gap-2">
          <Button size="small" className="!h-8 !px-4" onClick={onCancel}>
            {intl.formatMessage({ id: 'pages.dashboard.editor.common.cancel' })}
          </Button>
          <Button size="small" type="primary" className="!h-8 !px-4 !shadow-none" onClick={onConfirm}>
            {intl.formatMessage({ id: 'pages.dashboard.editor.common.confirm' })}
          </Button>
        </div>
      )}
    >
      <YakTab
        defaultActiveKey={customized ? 'custom' : 'preset'}
        size="small"
        items={[
          {
            key: 'preset',
            label: intl.formatMessage({ id: 'pages.dashboard.editor.theme.preset' }),
            children: presetContent,
          },
          {
            key: 'custom',
            label: intl.formatMessage({ id: 'pages.dashboard.editor.theme.custom' }),
            children: customContent,
          },
        ]}
      />

      <style>{`
        .dashboard-theme-custom > .ant-collapse-item {
          border-bottom: 1px solid #eef0f3 !important;
        }
        .dashboard-theme-custom > .ant-collapse-item > .ant-collapse-header {
          padding: 10px 0 !important;
        }
        .dashboard-theme-custom .ant-collapse-content-box {
          padding: 0 0 8px !important;
        }
      `}</style>
    </Drawer>
  );
}
