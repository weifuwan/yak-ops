import {
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

export interface ScheduleSelectOption<Value extends string> {
  value: Value;
  label: string;
}

interface ScheduleSelectProps<Value extends string> {
  ariaLabel: string;
  value: Value | null;
  options: readonly ScheduleSelectOption<Value>[];
  onValueChange: (value: Value) => void;
  disabled?: boolean;
  placeholder?: string;
  triggerClassName?: string;
}

export function ScheduleSelect<Value extends string>({
  ariaLabel,
  value,
  options,
  onValueChange,
  disabled = false,
  placeholder,
  triggerClassName,
}: ScheduleSelectProps<Value>) {
  const items = Object.fromEntries(options.map((option) => [option.value, option.label]));

  return (
    <Select<Value>
      size="small"
      items={items}
      value={value}
      disabled={disabled}
      onValueChange={(nextValue) => {
        if (nextValue != null) onValueChange(nextValue);
      }}
    >
      <SelectTrigger
        variant="outlined"
        aria-label={ariaLabel}
        className={triggerClassName}
      >
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
