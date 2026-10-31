import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { DATA_SYNC_TASK_STATUS_ITEMS } from "./task-lifecycle";
import { PRODUCT_LABELS } from "../solution/presentation";
import type { TaskListFilters } from "./filters";

interface Props {
  filters: TaskListFilters;
  keyword: string;
  onKeywordChange: (value: string) => void;
  onFilterChange: (patch: Partial<TaskListFilters>) => void;
  onCreate: () => void;
}

export function TaskFilters({
  filters,
  keyword,
  onKeywordChange,
  onFilterChange,
  onCreate,
}: Props) {
  return (
    <div className="flex shrink-0 flex-wrap items-center gap-2">
      <Button size="small" variant="primary" onClick={onCreate}>
        <Plus size={14} />
        新建同步任务
      </Button>
      <div className="w-[300px]">
        <Input
          size="small"
          variant="outlined"
          value={keyword}
          placeholder="搜索任务名称或表名"
          onChange={(event) => onKeywordChange(event.target.value)}
        />
      </div>
      <div className="w-[165px]">
        <Select
          size="small"
          items={{ ALL: "全部同步类型", ...PRODUCT_LABELS }}
          value={filters.productType}
          onValueChange={(value) =>
            onFilterChange({
              productType: String(value) as TaskListFilters["productType"],
              pageNo: 1,
            })
          }
        >
          <SelectTrigger variant="outlined">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">
              <SelectItemText>全部同步类型</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
            {Object.entries(PRODUCT_LABELS).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                <SelectItemText>{label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="w-[150px]">
        <Select
          size="small"
          items={DATA_SYNC_TASK_STATUS_ITEMS}
          value={filters.status}
          onValueChange={(value) =>
            onFilterChange({
              status: String(value || "ALL") as TaskListFilters["status"],
              pageNo: 1,
            })
          }
        >
          <SelectTrigger variant="outlined">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(DATA_SYNC_TASK_STATUS_ITEMS).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                <SelectItemText>{label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </div>
  );
}
