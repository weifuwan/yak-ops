import {
  Input,
  PasswordInput,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import type { ShellLocalParam } from "../types";

export function LocalParamValueInput({
  item,
  index,
  disabled,
  onChange,
}: {
  item: ShellLocalParam;
  index: number;
  disabled: boolean;
  onChange: (patch: Partial<ShellLocalParam>) => void;
}) {
  if (item.type === "BOOLEAN") {
    return (
      <Select<string>
        size="small"
        items={{ true: "true", false: "false" }}
        value={item.value === "true" || item.value === "false" ? item.value : null}
        disabled={disabled}
        onValueChange={(value) => {
          if (value === "true" || value === "false") onChange({ value });
        }}
      >
        <SelectTrigger variant="outlined" className="w-full" aria-label={"布尔参数值 " + (index + 1)}>
          <SelectValue placeholder="请选择 true / false" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="true">
            <SelectItemText>true</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
          <SelectItem value="false">
            <SelectItemText>false</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        </SelectContent>
      </Select>
    );
  }

  const inputType =
    ["INTEGER", "LONG", "FLOAT", "DOUBLE"].includes(item.type)
      ? "number"
      : item.type === "DATE"
        ? "date"
        : item.type === "TIME"
          ? "time"
          : "text";
  const placeholder =
    item.type === "INTEGER" ? "请输入整数"
      : item.type === "LONG" ? "请输入长整数"
      : item.type === "FLOAT" || item.type === "DOUBLE" ? "请输入数字"
      : item.type === "DATE" ? "YYYY-MM-DD"
      : item.type === "TIME" ? "HH:mm:ss"
      : item.type === "TIMESTAMP" ? "YYYY-MM-DD HH:mm:ss"
      : item.type === "LIST" ? 'JSON 数组，例如 ["a","b"]'
      : item.type === "FILE" ? "请输入文件路径"
      : "参数值";

  if (item.sensitive) {
    return (
      <PasswordInput
        size="small"
        variant="outlined"
        value={item.value}
        disabled={disabled}
        onChange={(event) => onChange({ value: event.target.value })}
        placeholder={placeholder}
        aria-label={"敏感参数值 " + (index + 1)}
      />
    );
  }

  return (
    <Input
      size="small"
      variant="outlined"
      type={inputType}
      step={["INTEGER", "LONG", "TIME"].includes(item.type) ? 1 : "any"}
      value={item.value}
      disabled={disabled}
      onChange={(event) => onChange({ value: event.target.value })}
      placeholder={placeholder}
      aria-label={"参数值 " + (index + 1)}
    />
  );
}
