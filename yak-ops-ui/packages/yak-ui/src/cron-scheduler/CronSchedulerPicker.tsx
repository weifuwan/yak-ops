import { useMemo, useState, type ReactNode } from "react";

import { Button } from "../button";
import { cn } from "../cn";
import { Input, inputVariants } from "../input";
import { Popover, PopoverContent, PopoverTrigger } from "../popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "../select";
import { Tabs, TabsList, TabsPanel, TabsTab } from "../tabs";
import { createDefaultCronScheduleConfig, generateCron, parseCron } from "./cron";
import type {
  CronHourMode,
  CronMonthRule,
  CronQuartzWeekday,
  CronScheduleConfig,
  CronSchedulePeriod,
} from "./types";

type PickerSize = "small" | "medium" | "large";
type PickerVariant = "filled" | "outlined";
type PickerMode = "visual" | "advanced";

const PERIOD_ITEMS: Record<CronSchedulePeriod, string> = {
  minute: "分钟",
  hour: "小时",
  day: "每天",
  week: "每周",
  month: "每月",
  year: "每年",
};

const HOUR_MODE_ITEMS: Record<CronHourMode, string> = {
  range: "小时区间",
  specified: "指定小时",
};

const MONTH_RULE_KIND_ITEMS = {
  day: "指定日期",
  lastDay: "最后一天",
  nthWeekday: "第 N 个星期",
  lastWeekday: "最后一个星期",
} as const;

const NTH_ITEMS: Record<1 | 2 | 3 | 4, string> = {
  1: "第一个",
  2: "第二个",
  3: "第三个",
  4: "第四个",
};

const WEEKDAY_ITEMS: Array<{ label: string; shortLabel: string; value: CronQuartzWeekday }> = [
  { label: "星期一", shortLabel: "一", value: 2 },
  { label: "星期二", shortLabel: "二", value: 3 },
  { label: "星期三", shortLabel: "三", value: 4 },
  { label: "星期四", shortLabel: "四", value: 5 },
  { label: "星期五", shortLabel: "五", value: 6 },
  { label: "星期六", shortLabel: "六", value: 7 },
  { label: "星期日", shortLabel: "日", value: 1 },
];

const MONTH_ITEMS = [
  "一月",
  "二月",
  "三月",
  "四月",
  "五月",
  "六月",
  "七月",
  "八月",
  "九月",
  "十月",
  "十一月",
  "十二月",
].map((label, index) => ({ label, value: index + 1 }));

const sizeClasses: Record<PickerSize, string> = {
  small: "text-[length:var(--yak-font-size-control-small)]",
  medium: "text-[length:var(--yak-font-size-control-medium)]",
  large: "text-[length:var(--yak-font-size-control-large)]",
};

const NESTED_SELECT_POSITIONER_CLASS = "!z-[70]";

function FieldRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid min-h-8 grid-cols-[110px_minmax(0,1fr)] items-start gap-3">
      <div className="pt-1.5 text-right text-xs leading-4 text-[var(--yak-components-field-label)]">
        {label}
      </div>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

function TimeInput({
  value,
  disabled,
  onValueChange,
}: {
  value: string;
  disabled?: boolean;
  onValueChange: (value: string) => void;
}) {
  return (
    <Input
      type="time"
      size="small"
      variant="outlined"
      value={value}
      disabled={disabled}
      className="max-w-[180px]"
      onChange={(event) => onValueChange(event.target.value || "00:00")}
    />
  );
}

function NumberInput({
  value,
  min,
  max,
  disabled,
  onValueChange,
}: {
  value: number;
  min: number;
  max: number;
  disabled?: boolean;
  onValueChange: (value: number) => void;
}) {
  return (
    <Input
      type="number"
      size="small"
      variant="outlined"
      value={value}
      min={min}
      max={max}
      step={1}
      disabled={disabled}
      className="max-w-[180px]"
      onChange={(event) => {
        const next = Number(event.target.value);
        if (!Number.isFinite(next)) return;
        onValueChange(Math.max(min, Math.min(max, Math.round(next))));
      }}
    />
  );
}

function ToggleChip({
  active,
  children,
  disabled,
  onClick,
}: {
  active: boolean;
  children: ReactNode;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-pressed={active}
      disabled={disabled}
      className={cn(
        "flex h-7 min-w-7 cursor-pointer items-center justify-center rounded-[var(--yak-radius-control-small)] border px-2 text-xs outline-none transition-[background-color,border-color,color]",
        "focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-button-focus-ring)] disabled:cursor-not-allowed disabled:opacity-45",
        active
          ? "border-[var(--yak-color-primary)] bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
          : "border-[var(--yak-components-input-border)] bg-white text-[var(--yak-components-input-text)] hover:bg-[var(--yak-components-input-bg-hover)]",
      )}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

function toggleNumber(values: number[], value: number) {
  return values.includes(value)
    ? values.filter((item) => item !== value)
    : [...values, value].sort((left, right) => left - right);
}

function isVisualConfigSupported(config: CronScheduleConfig) {
  if (config.period === "month") return config.monthRules.length === 1;
  if (config.period === "year") return config.yearRules.length === 1;
  return true;
}

function cronSummary(value: string) {
  const parsed = parseCron(value);
  if (!parsed || !isVisualConfigSupported(parsed)) return value ? "高级 Cron" : "未配置";

  switch (parsed.period) {
    case "minute":
      return `每 ${parsed.minuteInterval} 分钟`;
    case "hour":
      return parsed.hourMode === "specified"
        ? `指定小时 · ${String(parsed.specifiedHourMinute).padStart(2, "0")} 分`
        : `每 ${parsed.hourInterval} 小时`;
    case "day":
      return `每天 ${parsed.dayTime}`;
    case "week":
      return `每周 · ${parsed.weekTime}`;
    case "month":
      return `每月 · ${parsed.monthTime}`;
    case "year":
      return `每年 · ${parsed.yearTime}`;
  }
}

function ruleKind(rule: CronMonthRule) {
  return rule.kind;
}

function createRule(kind: CronMonthRule["kind"]): CronMonthRule {
  if (kind === "lastDay") return { kind: "lastDay" };
  if (kind === "nthWeekday") return { kind: "nthWeekday", nth: 1, weekday: 2 };
  if (kind === "lastWeekday") return { kind: "lastWeekday", weekday: 2 };
  return { kind: "day", day: 1 };
}

function SingleMonthRuleEditor({
  value,
  disabled,
  onValueChange,
}: {
  value: CronMonthRule;
  disabled?: boolean;
  onValueChange: (value: CronMonthRule) => void;
}) {
  const kind = ruleKind(value);

  return (
    <div className="space-y-2">
      <Select
        size="small"
        value={kind}
        items={MONTH_RULE_KIND_ITEMS}
        disabled={disabled}
        onValueChange={(next) =>
          onValueChange(createRule(String(next || "day") as CronMonthRule["kind"]))
        }
      >
        <SelectTrigger variant="outlined" className="max-w-[220px]">
          <SelectValue />
        </SelectTrigger>
        <SelectContent positionerClassName={NESTED_SELECT_POSITIONER_CLASS}>
          {Object.entries(MONTH_RULE_KIND_ITEMS).map(([optionValue, label]) => (
            <SelectItem key={optionValue} value={optionValue}>
              <SelectItemText>{label}</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {value.kind === "day" ? (
        <NumberInput
          value={value.day}
          min={1}
          max={31}
          disabled={disabled}
          onValueChange={(day) => onValueChange({ kind: "day", day })}
        />
      ) : null}

      {value.kind === "nthWeekday" ? (
        <div className="flex flex-wrap gap-2">
          <Select
            size="small"
            value={value.nth}
            items={NTH_ITEMS}
            disabled={disabled}
            onValueChange={(next) =>
              onValueChange({
                ...value,
                nth: Number(next || 1) as 1 | 2 | 3 | 4,
              })
            }
          >
            <SelectTrigger variant="outlined" className="w-[120px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent positionerClassName={NESTED_SELECT_POSITIONER_CLASS}>
              {(Object.entries(NTH_ITEMS) as Array<[string, string]>).map(
                ([optionValue, label]) => (
                  <SelectItem key={optionValue} value={Number(optionValue)}>
                    <SelectItemText>{label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ),
              )}
            </SelectContent>
          </Select>
          <WeekdaySelect
            value={value.weekday}
            disabled={disabled}
            onValueChange={(weekday) => onValueChange({ ...value, weekday })}
          />
        </div>
      ) : null}

      {value.kind === "lastWeekday" ? (
        <WeekdaySelect
          value={value.weekday}
          disabled={disabled}
          onValueChange={(weekday) => onValueChange({ ...value, weekday })}
        />
      ) : null}
    </div>
  );
}

function WeekdaySelect({
  value,
  disabled,
  onValueChange,
}: {
  value: CronQuartzWeekday;
  disabled?: boolean;
  onValueChange: (value: CronQuartzWeekday) => void;
}) {
  const items = Object.fromEntries(
    WEEKDAY_ITEMS.map((item) => [String(item.value), item.label]),
  ) as Record<string, string>;

  return (
    <Select
      size="small"
      value={String(value)}
      items={items}
      disabled={disabled}
      onValueChange={(next) => onValueChange(Number(next || 2) as CronQuartzWeekday)}
    >
      <SelectTrigger variant="outlined" className="w-[140px]">
        <SelectValue />
      </SelectTrigger>
      <SelectContent positionerClassName={NESTED_SELECT_POSITIONER_CLASS}>
        {WEEKDAY_ITEMS.map((item) => (
          <SelectItem key={item.value} value={String(item.value)}>
            <SelectItemText>{item.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

interface VisualEditorProps {
  config: CronScheduleConfig;
  disabled?: boolean;
  onChange: (config: CronScheduleConfig) => void;
}

function VisualEditor({ config, disabled, onChange }: VisualEditorProps) {
  const patch = (next: Partial<CronScheduleConfig>) => onChange({ ...config, ...next });

  const visualFields = useMemo(() => {
    switch (config.period) {
      case "minute":
        return (
          <>
            <FieldRow label="开始时间">
              <TimeInput
                value={config.minuteStartTime}
                disabled={disabled}
                onValueChange={(minuteStartTime) => patch({ minuteStartTime })}
              />
            </FieldRow>
            <FieldRow label="时间间隔">
              <div className="flex items-center gap-2">
                <NumberInput
                  value={config.minuteInterval}
                  min={1}
                  max={59}
                  disabled={disabled}
                  onValueChange={(minuteInterval) => patch({ minuteInterval })}
                />
                <span className="text-xs text-[var(--yak-components-muted-text)]">分钟</span>
              </div>
            </FieldRow>
            <FieldRow label="结束时间">
              <TimeInput
                value={config.minuteEndTime}
                disabled={disabled}
                onValueChange={(minuteEndTime) => patch({ minuteEndTime })}
              />
            </FieldRow>
          </>
        );

      case "hour":
        return (
          <>
            <FieldRow label="小时模式">
              <Select
                size="small"
                value={config.hourMode}
                items={HOUR_MODE_ITEMS}
                disabled={disabled}
                onValueChange={(next) =>
                  patch({ hourMode: String(next || "range") as CronHourMode })
                }
              >
                <SelectTrigger variant="outlined" className="max-w-[220px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent positionerClassName={NESTED_SELECT_POSITIONER_CLASS}>
                  {Object.entries(HOUR_MODE_ITEMS).map(([optionValue, label]) => (
                    <SelectItem key={optionValue} value={optionValue}>
                      <SelectItemText>{label}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </FieldRow>

            {config.hourMode === "range" ? (
              <>
                <FieldRow label="开始时间">
                  <TimeInput
                    value={config.hourStartTime}
                    disabled={disabled}
                    onValueChange={(hourStartTime) => patch({ hourStartTime })}
                  />
                </FieldRow>
                <FieldRow label="时间间隔">
                  <div className="flex items-center gap-2">
                    <NumberInput
                      value={config.hourInterval}
                      min={1}
                      max={23}
                      disabled={disabled}
                      onValueChange={(hourInterval) => patch({ hourInterval })}
                    />
                    <span className="text-xs text-[var(--yak-components-muted-text)]">小时</span>
                  </div>
                </FieldRow>
                <FieldRow label="结束时间">
                  <TimeInput
                    value={config.hourEndTime}
                    disabled={disabled}
                    onValueChange={(hourEndTime) => patch({ hourEndTime })}
                  />
                </FieldRow>
              </>
            ) : (
              <>
                <FieldRow label="指定小时">
                  <div className="grid max-w-[456px] grid-cols-8 gap-1.5 max-sm:grid-cols-6">
                    {Array.from({ length: 24 }, (_, hour) => (
                      <ToggleChip
                        key={hour}
                        active={config.specifiedHours.includes(hour)}
                        disabled={disabled}
                        onClick={() => {
                          const specifiedHours = toggleNumber(config.specifiedHours, hour);
                          patch({
                            specifiedHours: specifiedHours.length ? specifiedHours : [hour],
                          });
                        }}
                      >
                        {String(hour).padStart(2, "0")}
                      </ToggleChip>
                    ))}
                  </div>
                </FieldRow>
                <FieldRow label="执行分钟">
                  <div className="flex items-center gap-2">
                    <NumberInput
                      value={config.specifiedHourMinute}
                      min={0}
                      max={59}
                      disabled={disabled}
                      onValueChange={(specifiedHourMinute) => patch({ specifiedHourMinute })}
                    />
                    <span className="text-xs text-[var(--yak-components-muted-text)]">分</span>
                  </div>
                </FieldRow>
              </>
            )}
          </>
        );

      case "day":
        return (
          <FieldRow label="执行时间">
            <TimeInput
              value={config.dayTime}
              disabled={disabled}
              onValueChange={(dayTime) => patch({ dayTime })}
            />
          </FieldRow>
        );

      case "week":
        return (
          <>
            <FieldRow label="指定星期">
              <div className="flex flex-wrap gap-1.5">
                {WEEKDAY_ITEMS.map((item) => (
                  <ToggleChip
                    key={item.value}
                    active={config.weekdays.includes(item.value)}
                    disabled={disabled}
                    onClick={() => {
                      const weekdays = toggleNumber(
                        config.weekdays,
                        item.value,
                      ) as CronQuartzWeekday[];
                      patch({ weekdays: weekdays.length ? weekdays : [item.value] });
                    }}
                  >
                    {item.shortLabel}
                  </ToggleChip>
                ))}
              </div>
            </FieldRow>
            <FieldRow label="执行时间">
              <TimeInput
                value={config.weekTime}
                disabled={disabled}
                onValueChange={(weekTime) => patch({ weekTime })}
              />
            </FieldRow>
          </>
        );

      case "month":
        return (
          <>
            <FieldRow label="日期规则">
              <SingleMonthRuleEditor
                value={config.monthRules[0] || { kind: "day", day: 1 }}
                disabled={disabled}
                onValueChange={(rule) => patch({ monthRules: [rule] })}
              />
            </FieldRow>
            <FieldRow label="执行时间">
              <TimeInput
                value={config.monthTime}
                disabled={disabled}
                onValueChange={(monthTime) => patch({ monthTime })}
              />
            </FieldRow>
          </>
        );

      case "year":
        return (
          <>
            <FieldRow label="指定月份">
              <div className="grid max-w-[456px] grid-cols-6 gap-1.5 max-sm:grid-cols-4">
                {MONTH_ITEMS.map((item) => (
                  <ToggleChip
                    key={item.value}
                    active={config.months.includes(item.value)}
                    disabled={disabled}
                    onClick={() => {
                      const months = toggleNumber(config.months, item.value);
                      patch({ months: months.length ? months : [item.value] });
                    }}
                  >
                    {item.value}月
                  </ToggleChip>
                ))}
              </div>
            </FieldRow>
            <FieldRow label="日期规则">
              <SingleMonthRuleEditor
                value={config.yearRules[0] || { kind: "day", day: 1 }}
                disabled={disabled}
                onValueChange={(rule) => patch({ yearRules: [rule] })}
              />
            </FieldRow>
            <FieldRow label="执行时间">
              <TimeInput
                value={config.yearTime}
                disabled={disabled}
                onValueChange={(yearTime) => patch({ yearTime })}
              />
            </FieldRow>
          </>
        );
    }
  }, [config, disabled]);

  return (
    <div className="space-y-3">
      <FieldRow label="调度周期">
        <Select
          size="small"
          value={config.period}
          items={PERIOD_ITEMS}
          disabled={disabled}
          onValueChange={(next) => patch({ period: String(next || "day") as CronSchedulePeriod })}
        >
          <SelectTrigger variant="outlined" className="max-w-[220px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent positionerClassName={NESTED_SELECT_POSITIONER_CLASS}>
            {Object.entries(PERIOD_ITEMS).map(([optionValue, label]) => (
              <SelectItem key={optionValue} value={optionValue}>
                <SelectItemText>{label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </FieldRow>

      {visualFields}
    </div>
  );
}

export interface CronSchedulerPickerProps {
  value?: string;
  onValueChange?: (cronExpression: string) => void;
  disabled?: boolean;
  placeholder?: string;
  className?: string;
  panelClassName?: string;
  panelWidth?: number;
  size?: PickerSize;
  variant?: PickerVariant;
  allowClear?: boolean;
  renderPanelExtra?: (draftCronExpression: string) => ReactNode;
}

export function CronSchedulerPicker({
  value = "",
  onValueChange,
  disabled = false,
  placeholder = "选择调度规则",
  className,
  panelClassName,
  panelWidth = 760,
  size = "small",
  variant = "outlined",
  allowClear = true,
  renderPanelExtra,
}: CronSchedulerPickerProps) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<PickerMode>("visual");
  const [config, setConfig] = useState<CronScheduleConfig>(createDefaultCronScheduleConfig());
  const [draftCron, setDraftCron] = useState("");
  const [modeError, setModeError] = useState(false);

  const beginDraft = () => {
    const normalized = value.trim();
    const parsed = parseCron(normalized);
    const visual = parsed && isVisualConfigSupported(parsed) ? parsed : undefined;
    const nextConfig = visual || createDefaultCronScheduleConfig();

    setConfig(nextConfig);
    setModeError(false);
    setDraftCron(normalized ? value : generateCron(nextConfig).cron);
    setMode(normalized && !visual ? "advanced" : "visual");
  };

  const handleOpenChange = (nextOpen: boolean) => {
    if (nextOpen) beginDraft();
    setOpen(nextOpen);
  };

  const updateVisualConfig = (nextConfig: CronScheduleConfig) => {
    setConfig(nextConfig);
    setDraftCron(generateCron(nextConfig).cron);
  };

  const changeMode = (nextMode: string) => {
    const next = nextMode === "advanced" ? "advanced" : "visual";
    if (next === "visual") {
      const parsed = parseCron(draftCron);
      if (!parsed || !isVisualConfigSupported(parsed)) {
        setModeError(true);
        return;
      }
      setConfig(parsed);
      // Switching tabs is not an edit. Only visual control changes generate a replacement.
      if (!draftCron.trim()) setDraftCron(generateCron(parsed).cron);
    }
    setModeError(false);
    setMode(next);
  };

  const commit = () => {
    onValueChange?.(draftCron);
    setOpen(false);
  };

  const clear = () => {
    onValueChange?.("");
    setOpen(false);
  };

  const summary = cronSummary(value.trim());

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <div className={cn("relative w-full", className)}>
        <PopoverTrigger
          disabled={disabled}
          aria-label="打开 Cron 调度配置"
          className={cn(
            inputVariants({ size, variant }),
            "group/cron-picker flex cursor-pointer items-center gap-2 pr-8 text-left",
            "focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-input-focus-ring)]",
            disabled && "cursor-not-allowed",
          )}
        >
          <span className="min-w-0 flex-1 truncate">
            {value ? (
              <span className="flex min-w-0 items-center gap-2">
                <span className="shrink-0 text-[var(--yak-components-muted-text)]">{summary}</span>
                <code className="min-w-0 truncate font-mono text-[11px] text-[var(--yak-components-input-text)]">
                  {value}
                </code>
              </span>
            ) : (
              <span className="text-[var(--yak-components-input-placeholder)]">{placeholder}</span>
            )}
          </span>
          <span
            aria-hidden="true"
            className="shrink-0 text-[var(--yak-components-input-icon)] transition-transform duration-[360ms] ease-[cubic-bezier(0.16,1,0.3,1)] group-data-popup-open/cron-picker:rotate-180 motion-reduce:transition-none"
          >
            <svg viewBox="0 0 20 20" className="size-4" fill="none">
              <path
                d="m6 8 4 4 4-4"
                stroke="currentColor"
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth="1.5"
              />
            </svg>
          </span>
        </PopoverTrigger>
      </div>

      <PopoverContent
        align="start"
        sideOffset={6}
        className={cn(
          "max-h-[min(680px,calc(100vh-32px))] overflow-y-auto p-0",
          "origin-top transition-[clip-path,opacity,transform] duration-[360ms] ease-[cubic-bezier(0.16,1,0.3,1)]",
          "[clip-path:inset(-48px)] data-starting-style:[clip-path:inset(0_-48px_100%_-48px)] data-starting-style:-translate-y-1",
          "data-ending-style:[clip-path:inset(0_-48px_100%_-48px)] data-ending-style:-translate-y-1 motion-reduce:transition-none",
          sizeClasses[size],
          panelClassName,
        )}
        style={{ width: `min(${panelWidth}px, calc(100vw - 32px))` }}
      >
        <div className="border-b border-[var(--yak-components-control-border)] px-5 py-4">
          <div className="text-sm font-semibold text-[var(--yak-components-panel-text)]">
            Cron 调度配置
          </div>
        </div>

        <div className="px-5 pt-4">
          <Tabs value={mode} onValueChange={changeMode}>
            <TabsList>
              <TabsTab value="visual">常用配置</TabsTab>
              <TabsTab value="advanced">高级 Cron</TabsTab>
            </TabsList>

            <TabsPanel value="visual" className="py-4">
              <VisualEditor config={config} disabled={disabled} onChange={updateVisualConfig} />
            </TabsPanel>

            <TabsPanel value="advanced" className="py-4">
              <FieldRow label="Cron 表达式">
                <div>
                  <Input
                    size="small"
                    variant="outlined"
                    value={draftCron}
                    disabled={disabled}
                    placeholder="0 0 2 * * ?"
                    className="font-mono"
                    maxLength={256}
                    aria-label="Cron 表达式"
                    onChange={(event) => {
                      setModeError(false);
                      setDraftCron(event.target.value);
                    }}
                  />
                  {modeError ? (
                    <div
                      role="status"
                      className="mt-1.5 text-xs text-[var(--yak-components-field-error)]"
                    >
                      该表达式无法转换为常用配置
                    </div>
                  ) : null}
                </div>
              </FieldRow>
            </TabsPanel>
          </Tabs>
        </div>

        {renderPanelExtra ? (
          <div className="border-t border-[var(--yak-components-control-border)] px-5 py-4">
            {renderPanelExtra(draftCron)}
          </div>
        ) : null}

        <div className="border-t border-[var(--yak-components-control-border)] bg-white px-5 py-3">
          <div className="mb-3 flex min-w-0 items-center gap-2 rounded-[var(--yak-radius-control-small)] bg-[var(--yak-components-input-bg)] px-3 py-2">
            <span className="shrink-0 text-xs text-[var(--yak-components-muted-text)]">Cron</span>
            <code className="min-w-0 flex-1 break-all font-mono text-xs text-[var(--yak-components-input-text)]">
              {draftCron || "-"}
            </code>
          </div>

          <div className="flex items-center justify-between gap-3">
            <div>
              {allowClear && value ? (
                <Button size="small" variant="ghost" disabled={disabled} onClick={clear}>
                  清除
                </Button>
              ) : null}
            </div>
            <div className="flex items-center gap-2">
              <Button size="small" disabled={disabled} onClick={() => setOpen(false)}>
                取消
              </Button>
              <Button
                size="small"
                variant="primary"
                disabled={disabled || !draftCron.trim()}
                onClick={commit}
              >
                确认
              </Button>
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
