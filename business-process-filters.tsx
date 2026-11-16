import {
  Button, Input, Select, SelectContent, SelectItem, SelectItemText,
  SelectTrigger, SelectValue,
} from "@yak-ops/yak-ui";

import { withSelectedOption } from "../../select-options";
import type { BusinessProcessFilters } from "./types";

interface SelectOption { value: string; label: string; }
interface Props {
  filters: BusinessProcessFilters;
  domainOptions: SelectOption[];
  creatorOptions: string[];
  directorOptions: string[];
  domainsLoading: boolean;
  domainsError: boolean;
  processOptionsLoading: boolean;
  onChange: (key: keyof BusinessProcessFilters, value: string) => void;
  onSearch: () => void;
  onReset: () => void;
}

const FIELD_CLASS = "grid grid-cols-[64px_minmax(0,1fr)] items-center gap-2";

function FilterSelect({
  value, placeholder, options, disabled, fallbackLabel, onChange,
}: {
  value: string;
  placeholder: string;
  options: SelectOption[];
  disabled?: boolean;
  fallbackLabel?: string;
  onChange: (value: string) => void;
}) {
  const selectItems = [
    { value: "__all__", label: "不限" },
    ...withSelectedOption(options, value, fallbackLabel || value),
  ];
  return (
    <Select
      items={selectItems}
      value={value || undefined}
      disabled={disabled}
      onValueChange={(nextValue) => onChange(nextValue === "__all__" ? "" : String(nextValue || ""))}
    >
      <SelectTrigger size="small" variant="outlined" className="w-full">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="__all__"><SelectItemText>不限</SelectItemText></SelectItem>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            <SelectItemText>{option.label}</SelectItemText>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function BusinessProcessFiltersPanel({
  filters, domainOptions, creatorOptions, directorOptions,
  domainsLoading, domainsError, processOptionsLoading, onChange, onSearch, onReset,
}: Props) {
  return (
    <section className="bg-white pb-4 pt-4">
      <div className="grid grid-cols-1 gap-x-8 gap-y-2 px-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] 2xl:grid-cols-[440px_440px_auto] 2xl:gap-x-[100px]">
        <div className="space-y-2">
          <label className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">英文缩写：</span>
            <Input size="small" variant="outlined" value={filters.enShortName}
              onChange={(event) => onChange("enShortName", event.target.value)} />
          </label>
          <label className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">英文名：</span>
            <Input size="small" variant="outlined" value={filters.enName}
              onChange={(event) => onChange("enName", event.target.value)} />
          </label>
          <label className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">中文名：</span>
            <Input size="small" variant="outlined" value={filters.cnName}
              onChange={(event) => onChange("cnName", event.target.value)} />
          </label>
        </div>

        <div className="space-y-2">
          <label className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">数据域：</span>
            <FilterSelect value={filters.dataDomainId} placeholder="请选择"
              options={domainOptions} disabled={domainsLoading || domainsError}
              fallbackLabel="已选数据域（名称暂不可用）"
              onChange={(value) => onChange("dataDomainId", value)} />
          </label>
          <label className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">创建人：</span>
            <FilterSelect value={filters.createBy} placeholder="请选择"
              options={creatorOptions.map((value) => ({ value, label: value }))}
              disabled={processOptionsLoading}
              onChange={(value) => onChange("createBy", value)} />
          </label>
          <label className={FIELD_CLASS}>
            <span className="text-right text-xs text-[var(--yak-components-muted-text)]">负责人：</span>
            <FilterSelect value={filters.director} placeholder="请选择"
              options={directorOptions.map((value) => ({ value, label: value }))}
              disabled={processOptionsLoading}
              onChange={(value) => onChange("director", value)} />
          </label>
        </div>

        <div className="flex items-start gap-2 pt-0.5">
          <Button size="sm" variant="primary" onClick={onSearch}>搜索</Button>
          <Button size="sm" variant="secondary" onClick={onReset}>重置</Button>
        </div>
      </div>
    </section>
  );
}

export default BusinessProcessFiltersPanel;
