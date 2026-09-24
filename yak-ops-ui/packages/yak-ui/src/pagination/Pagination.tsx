import { useEffect, useMemo, useState, type ReactNode } from "react";

import { Button } from "../button";
import { NumberField, NumberFieldGroup, NumberFieldInput } from "../number-field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "../select";
import { cn } from "../cn";

type PageItem = number | "ellipsis-start" | "ellipsis-end";

const buildItems = (page: number, totalPages: number): PageItem[] => {
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index + 1);
  const items: PageItem[] = [1];
  if (page > 4) items.push("ellipsis-start");
  const start = Math.max(2, page - 1);
  const end = Math.min(totalPages - 1, page + 1);
  for (let current = start; current <= end; current += 1) items.push(current);
  if (page < totalPages - 3) items.push("ellipsis-end");
  items.push(totalPages);
  return items;
};

export interface PaginationProps {
  page: number;
  pageSize: number;
  total: number;
  pageSizeOptions?: readonly number[];
  disabled?: boolean;
  showSizeChanger?: boolean;
  showQuickJumper?: boolean;
  renderTotal?: (total: number, range: [number, number]) => ReactNode;
  className?: string;
  onChange: (page: number, pageSize: number) => void;
}

export function Pagination({
  className,
  disabled = false,
  onChange,
  page,
  pageSize,
  pageSizeOptions = [10, 20, 50, 100],
  renderTotal,
  showQuickJumper = false,
  showSizeChanger = false,
  total,
}: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const currentPage = Math.min(Math.max(page, 1), totalPages);
  const [jumpPage, setJumpPage] = useState<number | null>(currentPage);

  useEffect(() => {
    setJumpPage(currentPage);
  }, [currentPage]);

  const items = useMemo(() => buildItems(currentPage, totalPages), [currentPage, totalPages]);
  const start = total === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const end = Math.min(currentPage * pageSize, total);

  return (
    <nav aria-label="Pagination" className={cn("flex flex-wrap items-center justify-end gap-2 text-xs", className)}>
      {renderTotal ? <div className="mr-2 text-[var(--yak-components-muted-text)]">{renderTotal(total, [start, end])}</div> : null}

      <Button
        size="small"
        disabled={disabled || currentPage <= 1}
        aria-label="Previous page"
        onClick={() => onChange(currentPage - 1, pageSize)}
      >
        ‹
      </Button>

      <div className="flex items-center gap-1">
        {items.map((item) =>
          typeof item === "number" ? (
            <Button
              key={item}
              size="small"
              variant={item === currentPage ? "primary" : "ghost"}
              disabled={disabled}
              aria-current={item === currentPage ? "page" : undefined}
              className="min-w-7 px-2"
              onClick={() => onChange(item, pageSize)}
            >
              {item}
            </Button>
          ) : (
            <span key={item} className="flex h-7 min-w-7 items-center justify-center text-[var(--yak-components-muted-text)]">…</span>
          ),
        )}
      </div>

      <Button
        size="small"
        disabled={disabled || currentPage >= totalPages}
        aria-label="Next page"
        onClick={() => onChange(currentPage + 1, pageSize)}
      >
        ›
      </Button>

      {showSizeChanger ? (
        <Select
          value={pageSize}
          onValueChange={(nextSize) => onChange(1, Number(nextSize))}
        >
          <SelectTrigger size="small" className="w-24">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {pageSizeOptions.map((option) => (
              <SelectItem key={option} value={option}>
                <SelectItemText>{option} / page</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      ) : null}

      {showQuickJumper ? (
        <div className="flex items-center gap-1">
          <span className="text-[var(--yak-components-muted-text)]">Go to</span>
          <NumberField
            value={jumpPage}
            min={1}
            max={totalPages}
            onValueChange={setJumpPage}
          >
            <NumberFieldGroup size="small" className="w-16">
              <NumberFieldInput aria-label="Page number" />
            </NumberFieldGroup>
          </NumberField>
          <Button
            size="small"
            disabled={disabled || jumpPage == null}
            onClick={() => onChange(Math.min(Math.max(jumpPage ?? currentPage, 1), totalPages), pageSize)}
          >
            Go
          </Button>
        </div>
      ) : null}
    </nav>
  );
}
