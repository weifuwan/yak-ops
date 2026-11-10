import { useEffect, useMemo, useState, type ReactNode } from "react";

import { Button } from "../button";
import { cn } from "../cn";
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

type PageItem = number | "ellipsis-start" | "ellipsis-end";

const paginationButtonClass = "yak-pagination-button";

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
  pageSizeLabel?: ReactNode;
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
  pageSizeLabel,
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
    <nav
      aria-label="Pagination"
      className={cn(
        "flex max-w-full flex-nowrap items-center justify-end gap-1 overflow-x-auto whitespace-nowrap text-xs",
        className,
      )}
    >
      {renderTotal ? (
        <div className="mr-3 shrink-0 text-[var(--yak-components-muted-text)]">
          {renderTotal(total, [start, end])}
        </div>
      ) : null}

      <Button
        size="sm"
        variant="ghost"
        isDisabled={disabled || currentPage <= 1}
        aria-label="Previous page"
        className={paginationButtonClass}
        onClick={() => onChange(currentPage - 1, pageSize)}
      >
        <span aria-hidden="true" className="-mt-px text-sm">
          ‹
        </span>
      </Button>

      <div className="flex shrink-0 items-center gap-1">
        {items.map((item) =>
          typeof item === "number" ? (
            <Button
              key={item}
              size="sm"
              variant="ghost"
              isDisabled={disabled}
              aria-current={item === currentPage ? "page" : undefined}
              className={paginationButtonClass}
              onClick={() => onChange(item, pageSize)}
            >
              {item}
            </Button>
          ) : (
            <span
              key={item}
              className="yak-pagination-ellipsis"
            >
              …
            </span>
          ),
        )}
      </div>

      <Button
        size="sm"
        variant="ghost"
        isDisabled={disabled || currentPage >= totalPages}
        aria-label="Next page"
        className={paginationButtonClass}
        onClick={() => onChange(currentPage + 1, pageSize)}
      >
        <span aria-hidden="true" className="-mt-px text-sm">
          ›
        </span>
      </Button>

      {showSizeChanger ? (
        <div className="ml-2 flex shrink-0 items-center gap-1.5">
          {pageSizeLabel ? (
            <span className="text-[var(--yak-components-pagination-text)]">{pageSizeLabel}</span>
          ) : null}
          <Select
            size="small"
            value={pageSize}
            onValueChange={(nextSize) => onChange(1, Number(nextSize))}
          >
            <SelectTrigger variant="outlined" className="w-[76px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {pageSizeOptions.map((option) => (
                <SelectItem key={option} value={option}>
                  <SelectItemText>{option}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      ) : null}

      {showQuickJumper ? (
        <div className="ml-2 flex shrink-0 items-center gap-1">
          <span className="text-[var(--yak-components-muted-text)]">Go to</span>
          <NumberField value={jumpPage} min={1} max={totalPages} onValueChange={setJumpPage}>
            <NumberFieldGroup size="small" className="w-16">
              <NumberFieldInput aria-label="Page number" />
            </NumberFieldGroup>
          </NumberField>
          <Button variant="secondary"
            size="sm"
            isDisabled={disabled || jumpPage == null}
            onClick={() =>
              onChange(Math.min(Math.max(jumpPage ?? currentPage, 1), totalPages), pageSize)
            }
          >
            Go
          </Button>
        </div>
      ) : null}
    </nav>
  );
}
