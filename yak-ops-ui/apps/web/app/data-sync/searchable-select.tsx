import {
  Button,
  Select,
  SelectContent,
  SelectFooter,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectSearch,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";

export interface DataSyncSearchableSelectOption {
  value: string;
  label: string;
  searchText?: string;
}

interface DataSyncSearchableSelectProps {
  value: string | null;
  options: DataSyncSearchableSelectOption[];
  placeholder: string;
  searchPlaceholder?: string;
  emptyText?: string;
  disabled?: boolean;
  refreshing?: boolean;
  footer?: ReactNode;
  onRefresh?: () => void | Promise<void>;
  onValueChange: (value: string) => void;
}

export function DataSyncSearchableSelect({
  value,
  options,
  placeholder,
  searchPlaceholder = "请输入关键字",
  emptyText = "暂无选项",
  disabled = false,
  refreshing = false,
  footer,
  onRefresh,
  onValueChange,
}: DataSyncSearchableSelectProps) {
  const [keyword, setKeyword] = useState("");
  const items = useMemo(
    () => Object.fromEntries(options.map((option) => [option.value, option.label])),
    [options],
  );
  const filteredOptions = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLocaleLowerCase();
    if (!normalizedKeyword) return options;
    return options.filter((option) =>
      [option.label, option.searchText]
        .filter(Boolean)
        .join(" ")
        .toLocaleLowerCase()
        .includes(normalizedKeyword),
    );
  }, [keyword, options]);

  return (
    <Select
      size="small"
      items={items}
      value={value}
      disabled={disabled}
      onOpenChange={(open) => {
        if (!open) setKeyword("");
      }}
      onValueChange={(nextValue) => {
        if (nextValue) onValueChange(String(nextValue));
      }}
    >
      <SelectTrigger variant="outlined">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent
        header={
          <SelectSearch
            value={keyword}
            placeholder={searchPlaceholder}
            onChange={(event) => setKeyword(event.target.value)}
            extra={
              onRefresh ? (
                <Button
                  size="small"
                  aria-label="刷新选项"
                  title="刷新"
                  className="w-8 px-0"
                  onClick={() => void onRefresh()}
                >
                  <RefreshCw
                    size={16}
                    className={refreshing ? "animate-spin motion-reduce:animate-none" : undefined}
                  />
                </Button>
              ) : undefined
            }
          />
        }
        emptyContent={filteredOptions.length === 0 ? emptyText : undefined}
        footer={footer ? <SelectFooter>{footer}</SelectFooter> : undefined}
      >
        {filteredOptions.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export default DataSyncSearchableSelect;
