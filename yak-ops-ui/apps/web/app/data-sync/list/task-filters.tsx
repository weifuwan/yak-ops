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

const TASK_SCOPE_ITEMS = {
  ALL: "全部任务类型",
  SINGLE_TABLE: "单表同步",
  MULTI_TABLE: "多表同步",
  DATABASE: "整库同步",
} as const;
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
      <Button size="sm" variant="primary" onClick={onCreate}>
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
      <div className="w-[150px]">
        <Select
          size="small"
          items={TASK_SCOPE_ITEMS}
          value={filters.scope}
          onValueChange={(value) =>
            onFilterChange({
              scope: String(value || "ALL") as TaskListFilters["scope"],
              pageNo: 1,
            })
          }
        >
          <SelectTrigger variant="outlined">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(TASK_SCOPE_ITEMS).map(([value, label]) => (
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
