import { Button, Input } from "@yak-ops/yak-ui";
import { Plus, Trash2 } from "lucide-react";

import {
  DataSourceFormField,
  type DataSourceFormFieldState,
  type FormRule,
} from "./form-runtime";

interface IntlFormatter {
  formatMessage: (
    descriptor: { id: string },
    values?: Record<string, unknown>,
  ) => string;
}

interface CustomKVListProps {
  intl: IntlFormatter;
  name: string;
  label: string;
  placeholder?: string;
  maxRows?: number;
}

interface KeyValueRow {
  key: string;
  value: string;
}

const normalizeRows = (value: unknown): KeyValueRow[] => {
  if (!Array.isArray(value)) return [];
  return value.map((item) => {
    const row = item && typeof item === "object" ? item as Record<string, unknown> : {};
    return {
      key: String(row.key ?? ""),
      value: String(row.value ?? ""),
    };
  });
};

const CustomKVList = ({
  intl,
  name,
  label,
  placeholder,
  maxRows = 50,
}: CustomKVListProps) => {
  const rules: FormRule[] = [
    {
      validator: async (value) => {
        const rows = normalizeRows(value);
        const seen = new Set<string>();

        for (let index = 0; index < rows.length; index += 1) {
          const row = rows[index];
          const key = row.key.trim();

          if (!key) {
            throw new Error(
              intl.formatMessage({ id: "pages.datasource.customKv.keyRequired" }),
            );
          }
          if (key.length > 128) {
            throw new Error(
              intl.formatMessage({ id: "pages.datasource.customKv.keyMax" }),
            );
          }
          if (seen.has(key)) {
            throw new Error(
              intl.formatMessage({ id: "pages.datasource.customKv.keyDuplicate" }),
            );
          }
          seen.add(key);

          if (row.value.length > 1024) {
            throw new Error(
              intl.formatMessage({ id: "pages.datasource.customKv.valueMax" }),
            );
          }
        }
      },
    },
  ];

  return (
    <DataSourceFormField name={name} rules={rules}>
      {(state: DataSourceFormFieldState) => {
        const rows = normalizeRows(state.value);
        const canAdd = rows.length < maxRows;

        const updateRow = (
          index: number,
          patch: Partial<KeyValueRow>,
        ) => {
          const next = rows.map((row, rowIndex) =>
            rowIndex === index ? { ...row, ...patch } : row,
          );
          state.setValue(next);
        };

        const removeRow = (index: number) => {
          state.setValue(rows.filter((_, rowIndex) => rowIndex !== index));
        };

        return (
          <div className="mb-3">
            <div className="mb-2">
              <div className="text-[13px] font-medium leading-5 text-[#344054]">
                {label}
              </div>
              {placeholder ? (
                <div className="mt-0.5 text-[11px] leading-4 text-[#98a2b3]">
                  {placeholder}
                </div>
              ) : null}
            </div>

            <div className="overflow-hidden rounded-lg border border-[#e7e9ed] bg-white">
              <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_36px] items-center gap-2 border-b border-[#eef0f3] bg-[#fafbfc] px-3 py-2 text-[11px] font-medium text-[#667085]">
                <span>
                  {intl.formatMessage({ id: "pages.datasource.customKv.key" })}
                </span>
                <span>
                  {intl.formatMessage({ id: "pages.datasource.customKv.value" })}
                </span>
                <span />
              </div>

              {rows.length > 0 ? (
                <div className="divide-y divide-[#f0f1f3]">
                  {rows.map((row, index) => (
                    <div
                      key={index}
                      className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_36px] items-start gap-2 px-3 py-2"
                    >
                      <Input
                        value={row.key}
                        aria-invalid={state.invalid || undefined}
                        placeholder={intl.formatMessage({
                          id: "pages.datasource.customKv.keyPlaceholder",
                        })}
                        onChange={(event) =>
                          updateRow(index, { key: event.target.value })
                        }
                        onBlur={() => void state.validate().catch(() => undefined)}
                      />

                      <Input
                        value={row.value}
                        aria-invalid={state.invalid || undefined}
                        placeholder={intl.formatMessage({
                          id: "pages.datasource.customKv.valuePlaceholder",
                        })}
                        onChange={(event) =>
                          updateRow(index, { value: event.target.value })
                        }
                        onBlur={() => void state.validate().catch(() => undefined)}
                      />

                      <Button
                        variant="danger"
                        size="small"
                        className="h-7 w-7 p-0"
                        aria-label={intl.formatMessage({
                          id: "pages.datasource.customKv.deleteAria",
                        })}
                        onClick={() => removeRow(index)}
                      >
                        <Trash2 size={14} />
                      </Button>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="px-3 py-5 text-center text-xs text-[#98a2b3]">
                  {intl.formatMessage({ id: "pages.datasource.customKv.empty" })}
                </div>
              )}

              {state.error ? (
                <div className="border-t border-[#f0f1f3] px-3 py-2 text-[11px] text-[#b42318]">
                  {state.error}
                </div>
              ) : null}

              <div className="flex justify-end border-t border-[#eef0f3] bg-[#fcfcfd] px-3 py-2">
                <Button
                  variant="ghost"
                  size="small"
                  disabled={!canAdd}
                  onClick={() =>
                    state.setValue([...rows, { key: "", value: "" }])
                  }
                >
                  <Plus size={14} />
                  {canAdd
                    ? intl.formatMessage({ id: "pages.datasource.customKv.add" })
                    : intl.formatMessage(
                        { id: "pages.datasource.customKv.maxRows" },
                        { maxRows },
                      )}
                </Button>
              </div>
            </div>
          </div>
        );
      }}
    </DataSourceFormField>
  );
};

export default CustomKVList;
