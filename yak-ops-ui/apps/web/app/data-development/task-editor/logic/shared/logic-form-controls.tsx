import type { ReactNode } from "react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

export type LogicSelectOption = {
  value: string;
  label: string;
};

export function LogicSelect({
  id,
  value,
  options,
  placeholder,
  disabled = false,
  onChange,
}: {
  id: string;
  value: string;
  options: LogicSelectOption[];
  placeholder: string;
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <Select<string>
      size="small"
      disabled={disabled}
      items={Object.fromEntries(options.map((option) => [option.value, option.label]))}
      value={value || null}
      onValueChange={(nextValue) => onChange(nextValue ?? "")}
    >
      <SelectTrigger id={id} variant="outlined">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function LogicField({
  label,
  required = false,
  children,
  description,
}: {
  label: string;
  required?: boolean;
  children: ReactNode;
  description?: string;
}) {
  return (
    <div className="grid grid-cols-[112px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs text-[#475467]">
        {label}{required ? <span className="ml-1 text-[#DC6B5D]">*</span> : null}
      </span>
      <div className="min-w-0">
        {children}
        {description ? (
          <span className="mt-1 block text-[11px] leading-4 text-[#98A2B3]">{description}</span>
        ) : null}
      </div>
    </div>
  );
}

export function LogicSection({
  title,
  description,
  children,
  action,
}: {
  title: string;
  description?: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <section className="rounded-md border border-[var(--yak-components-panel-border)] bg-white p-3">
      <div className="mb-3 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-xs font-semibold text-[var(--yak-components-panel-text)]">{title}</h3>
          {description ? <p className="mt-1 text-[11px] leading-4 text-[var(--yak-components-muted-text)]">{description}</p> : null}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}
