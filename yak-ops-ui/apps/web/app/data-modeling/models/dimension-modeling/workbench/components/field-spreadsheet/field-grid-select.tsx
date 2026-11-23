import {
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import type { FieldOption } from "./field-grid-types";

export function FieldGridSelect({
  value,
  options,
  placeholder,
  onChange,
}: {
  value: string;
  options: FieldOption[];
  placeholder: string;
  onChange: (value: string) => void;
}) {
  return (
    <Select<string>
      size="small"
      items={Object.fromEntries(options.map((option) => [option.value, option.label]))}
      value={value || null}
      onValueChange={(nextValue) => onChange(nextValue || "")}
    >
      <SelectTrigger
        variant="outlined"
        className="!h-[27px] w-full !rounded-none !border-0 !bg-transparent !px-2 !text-xs !shadow-none focus-visible:!ring-0"
      >
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent emptyContent="暂无可选项">
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

export default FieldGridSelect;
