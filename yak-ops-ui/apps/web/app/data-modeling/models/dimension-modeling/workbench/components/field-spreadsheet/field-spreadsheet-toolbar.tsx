import {
  Button,
  Checkbox,
  Input,
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@yak-ops/yak-ui";
import { Settings2 } from "lucide-react";

import type { FieldColumnKey, FieldGridColumn } from "./field-grid-types";

export function FieldSpreadsheetToolbar({
  insertCount,
  validationIssueCount,
  availableColumns,
  visibleColumns,
  onInsertCountChange,
  onInsertRows,
  onRemoveEmptyRows,
  onVisibleColumnsChange,
}: {
  insertCount: string;
  validationIssueCount: number;
  availableColumns: FieldGridColumn[];
  visibleColumns: Set<FieldColumnKey>;
  onInsertCountChange: (value: string) => void;
  onInsertRows: () => void;
  onRemoveEmptyRows: () => void;
  onVisibleColumnsChange: (columns: Set<FieldColumnKey>) => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <Button size="sm" variant="secondary" onClick={onRemoveEmptyRows}>
          移除空白行
        </Button>
        <div className="flex items-center gap-1 text-xs text-[var(--yak-components-muted-text)]">
          <Button size="sm" variant="secondary" onClick={onInsertRows}>
            插入
          </Button>
          <Input
            size="small"
            variant="outlined"
            type="number"
            min={1}
            max={100}
            value={insertCount}
            className="w-14"
            onChange={(event) => onInsertCountChange(event.target.value)}
          />
          <span>行</span>
        </div>
      </div>

      <div className="flex items-center gap-2">
        {validationIssueCount ? (
          <span className="text-xs text-[var(--yak-components-danger-text)]">
            {validationIssueCount} 个配置问题
          </span>
        ) : null}
        <Popover>
          <PopoverTrigger className="inline-flex h-7 cursor-pointer items-center gap-1.5 rounded border border-[var(--yak-components-control-border)] bg-white px-2.5 text-xs text-[var(--yak-components-panel-text)] hover:bg-[var(--yak-color-hover)]">
            <Settings2 size={13} />
            字段显示设置
          </PopoverTrigger>
          <PopoverContent align="end" className="w-[360px] p-2">
            <div className="grid grid-cols-2 gap-x-2 gap-y-1">
              {availableColumns.map((column) => {
                const fixed = !column.configurable;
                return (
                  <label
                    key={column.key}
                    className={[
                      "flex items-center gap-2 rounded px-2 py-1.5 text-xs",
                      fixed
                        ? "cursor-default text-[var(--yak-components-muted-text)]"
                        : "cursor-pointer hover:bg-[var(--yak-color-hover)]",
                    ].join(" ")}
                  >
                    <Checkbox
                      checked={fixed || visibleColumns.has(column.key)}
                      disabled={fixed}
                      onCheckedChange={(checked) => {
                        if (fixed) return;
                        const next = new Set(visibleColumns);
                        if (checked === true) next.add(column.key);
                        else next.delete(column.key);
                        onVisibleColumnsChange(next);
                      }}
                    />
                    <span>{column.title}</span>
                  </label>
                );
              })}
            </div>
          </PopoverContent>
        </Popover>
      </div>
    </div>
  );
}

export default FieldSpreadsheetToolbar;
