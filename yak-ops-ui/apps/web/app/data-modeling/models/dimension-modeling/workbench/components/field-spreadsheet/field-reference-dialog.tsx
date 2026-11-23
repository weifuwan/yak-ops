import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Checkbox,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import { useAuth } from "@/hooks/use-auth";
import {
  listDataSourceColumns,
  listDataSourceDatabases,
  listDataSources,
  listDataSourceTables,
} from "@/service/datasource";
import type { DimensionFieldRecord } from "@/service/data-modeling/models/dimension-modeling";
import { createDraftField, normalizeImportedDataType } from "./field-grid-utils";
import type { FieldKind, FieldOption } from "./field-grid-types";

function OptionSelect({
  value,
  options,
  placeholder,
  disabled,
  onChange,
}: {
  value: string;
  options: FieldOption[];
  placeholder: string;
  disabled?: boolean;
  onChange: (value: string) => void;
}) {
  return (
    <Select<string>
      size="small"
      items={Object.fromEntries(options.map((option) => [option.value, option.label]))}
      value={value || null}
      disabled={disabled}
      onValueChange={(nextValue) => onChange(nextValue || "")}
    >
      <SelectTrigger variant="outlined" className="w-full">
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

export function FieldReferenceDialog({
  open,
  projectCode,
  fieldType,
  onClose,
  onImport,
}: {
  open: boolean;
  projectCode: string;
  fieldType: FieldKind;
  onClose: () => void;
  onImport: (fields: DimensionFieldRecord[]) => void;
}) {
  const { currentUser } = useAuth();
  const [dataSourceId, setDataSourceId] = useState("");
  const [database, setDatabase] = useState("");
  const [tableName, setTableName] = useState("");
  const [excludedColumns, setExcludedColumns] = useState<Set<string>>(() => new Set());

  const dataSourcesQuery = useQuery({
    queryKey: ["modeling", "field-reference", "datasources", currentUser?.id, projectCode],
    queryFn: () => listDataSources({ pageNo: 1, pageSize: 100 }),
    enabled: Boolean(open && currentUser?.id),
  });
  const databasesQuery = useQuery({
    queryKey: ["modeling", "field-reference", "databases", currentUser?.id, projectCode, dataSourceId],
    queryFn: () => listDataSourceDatabases(dataSourceId),
    enabled: Boolean(open && dataSourceId),
  });
  const tablesQuery = useQuery({
    queryKey: ["modeling", "field-reference", "tables", currentUser?.id, projectCode, dataSourceId, database],
    queryFn: () => listDataSourceTables(dataSourceId, { database, limit: 200 }),
    enabled: Boolean(open && dataSourceId && database),
  });
  const selectedTable = useMemo(
    () => (tablesQuery.data || []).find((table) => table.name === tableName),
    [tableName, tablesQuery.data],
  );
  const columnsQuery = useQuery({
    queryKey: [
      "modeling",
      "field-reference",
      "columns",
      currentUser?.id,
      projectCode,
      dataSourceId,
      database,
      selectedTable?.schema,
      tableName,
    ],
    queryFn: () =>
      listDataSourceColumns(dataSourceId, {
        database,
        schema: selectedTable?.schema,
        table: tableName,
      }),
    enabled: Boolean(open && dataSourceId && database && tableName),
  });

  const dataSourceOptions = (dataSourcesQuery.data?.totalList || [])
    .filter((record) => record.id)
    .map((record) => ({
      value: String(record.id),
      label: record.name || String(record.id),
    }));
  const databaseOptions = (databasesQuery.data || []).map((item) => ({
    value: item,
    label: item,
  }));
  const tableOptions = (tablesQuery.data || []).map((table) => ({
    value: table.name,
    label: [table.schema, table.name].filter(Boolean).join("."),
  }));
  const selectedColumns = (columnsQuery.data || []).filter(
    (column) => !excludedColumns.has(column.name),
  );

  const reset = () => {
    setDataSourceId("");
    setDatabase("");
    setTableName("");
    setExcludedColumns(new Set());
  };

  const close = () => {
    reset();
    onClose();
  };

  const importColumns = () => {
    if (!selectedTable || selectedColumns.length === 0) return;
    const sourceTable = [
      selectedTable.database || database,
      selectedTable.schema,
      selectedTable.name,
    ]
      .filter(Boolean)
      .join(".");
    const fields = selectedColumns.map(
      (column): DimensionFieldRecord => ({
        ...createDraftField(fieldType),
        fieldName: column.name,
        cnName: column.remarks || "",
        dataType: normalizeImportedDataType(column.typeName),
        length: column.size,
        precision: column.scale,
        primaryFlag: Boolean(column.primaryKey),
        notNull: column.nullable === false,
        sourceTb: sourceTable,
        sourceField: column.name,
      }),
    );
    onImport(fields);
    close();
  };

  return (
    <Modal
      open={open}
      centered
      width={720}
      title="引用已有表字段"
      onClose={close}
      footer={
        <>
          <Button size="sm" variant="secondary" onClick={close}>
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            isDisabled={!selectedTable || selectedColumns.length === 0}
            onClick={importColumns}
          >
            引用 {selectedColumns.length || ""} 个字段
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <OptionSelect
            value={dataSourceId}
            options={dataSourceOptions}
            placeholder={dataSourcesQuery.isFetching ? "加载数据源..." : "选择数据源"}
            onChange={(value) => {
              setDataSourceId(value);
              setDatabase("");
              setTableName("");
              setExcludedColumns(new Set());
            }}
          />
          <OptionSelect
            value={database}
            options={databaseOptions}
            placeholder={databasesQuery.isFetching ? "加载数据库..." : "选择数据库"}
            disabled={!dataSourceId}
            onChange={(value) => {
              setDatabase(value);
              setTableName("");
              setExcludedColumns(new Set());
            }}
          />
          <OptionSelect
            value={tableName}
            options={tableOptions}
            placeholder={tablesQuery.isFetching ? "加载表..." : "选择表"}
            disabled={!database}
            onChange={(value) => {
              setTableName(value);
              setExcludedColumns(new Set());
            }}
          />
        </div>

        <div className="overflow-hidden rounded border border-[var(--yak-components-panel-border)]">
          <div className="flex h-8 items-center justify-between border-b border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] px-3 text-xs">
            <span>字段</span>
            <span className="text-[var(--yak-components-muted-text)]">
              已选 {selectedColumns.length} / {(columnsQuery.data || []).length}
            </span>
          </div>
          <div className="max-h-[320px] overflow-y-auto p-2">
            {columnsQuery.isFetching ? (
              <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
                正在加载字段...
              </div>
            ) : columnsQuery.isError ? (
              <div className="py-8 text-center text-xs text-[var(--yak-components-danger-text)]">
                字段加载失败
              </div>
            ) : (columnsQuery.data || []).length === 0 ? (
              <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
                请选择要引用的表
              </div>
            ) : (
              <div className="space-y-1">
                {(columnsQuery.data || []).map((column) => {
                  const checked = !excludedColumns.has(column.name);
                  return (
                    <label
                      key={column.name}
                      className="grid cursor-pointer grid-cols-[24px_minmax(0,1fr)_140px] items-center gap-2 rounded px-2 py-1.5 text-xs hover:bg-[var(--yak-color-hover)]"
                    >
                      <Checkbox
                        checked={checked}
                        onCheckedChange={(nextChecked) =>
                          setExcludedColumns((current) => {
                            const next = new Set(current);
                            if (nextChecked === true) next.delete(column.name);
                            else next.add(column.name);
                            return next;
                          })
                        }
                      />
                      <span className="min-w-0 truncate">
                        {column.name}
                        {column.remarks ? (
                          <span className="ml-2 text-[var(--yak-components-muted-text)]">
                            {column.remarks}
                          </span>
                        ) : null}
                      </span>
                      <span className="truncate text-[var(--yak-components-muted-text)]">
                        {column.typeName || "-"}
                      </span>
                    </label>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>
    </Modal>
  );
}

export default FieldReferenceDialog;
