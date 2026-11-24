import { useEffect, useMemo, useState, type ReactNode } from "react";
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
  toast,
} from "@yak-ops/yak-ui";
import { ListChecks, ListPlus, X } from "lucide-react";

import {
  listDataSourceColumns,
  listDataSourceDatabases,
  listDataSources,
  listDataSourceTables,
  type DataSourceCatalogColumn,
  type DataSourceRecord,
} from "@/service/datasource";
import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import type { DimensionFieldRecord } from "@/service/data-modeling/models/dimension-modeling";
import {
  createDraftField,
  normalizeImportedDataType,
} from "./field-spreadsheet/field-grid-utils";

type SourceFieldImportCardProps = {
  projectCode: string;
  fields: DimensionFieldRecord[];
  onChange: (fields: DimensionFieldRecord[]) => void;
};

function sourceColumnToField(
  column: DataSourceCatalogColumn,
  tableName: string,
): DimensionFieldRecord {
  return {
    ...createDraftField("PROPERTY"),
    fieldName: column.name,
    cnName: column.remarks || "",
    dataType: normalizeImportedDataType(column.typeName),
    length: column.size,
    fieldPrecision: column.scale,
    remark: column.remarks || undefined,
    primaryFlag: Boolean(column.primaryKey),
    notNull: column.nullable === false,
    sourceTb: tableName,
    sourceField: column.name,
    fieldType: "PROPERTY",
  };
}

function mergeImportedFields(
  current: DimensionFieldRecord[],
  columns: DataSourceCatalogColumn[],
  tableName: string,
) {
  const next = [...current];
  const indexByName = new Map(
    next
      .map((field, index) => [field.fieldName?.trim().toLowerCase(), index] as const)
      .filter(([name]) => Boolean(name)),
  );

  for (const column of columns) {
    const imported = sourceColumnToField(column, tableName);
    const normalizedName = imported.fieldName.trim().toLowerCase();
    const existingIndex = indexByName.get(normalizedName);
    if (existingIndex === undefined) {
      indexByName.set(normalizedName, next.length);
      next.push(imported);
      continue;
    }

    const existing = next[existingIndex];
    next[existingIndex] = {
      ...existing,
      ...imported,
      id: existing.id,
      tempId: existing.tempId || imported.tempId,
      fieldStdId: existing.fieldStdId,
      codeStdId: existing.codeStdId,
      unitId: existing.unitId,
    };
  }
  return next;
}

function SourceSelect({
  value,
  placeholder,
  options,
  disabled,
  triggerClassName = "w-full",
  onChange,
}: {
  value: string;
  placeholder: string;
  options: Array<{ value: string; label: string; icon?: ReactNode }>;
  disabled?: boolean;
  triggerClassName?: string;
  onChange: (value: string) => void;
}) {
  const selectedOption = options.find((option) => option.value === value);

  return (
    <Select<string>
      size="small"
      items={Object.fromEntries(options.map((option) => [option.value, option.label]))}
      value={value || null}
      disabled={disabled}
      onValueChange={(nextValue) => onChange(nextValue ?? "")}
    >
      <SelectTrigger variant="outlined" className={triggerClassName}>
        <span className="flex min-w-0 items-center gap-1.5">
          {selectedOption?.icon ? (
            <span className="flex size-4 shrink-0 items-center justify-center">
              {selectedOption.icon}
            </span>
          ) : null}
          <SelectValue placeholder={placeholder} />
        </span>
      </SelectTrigger>
      <SelectContent emptyContent={options.length ? undefined : "暂无可选项"}>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.icon ? (
              <span className="flex size-4 shrink-0 items-center justify-center">
                {option.icon}
              </span>
            ) : null}
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function SourceFieldImportCard({
  projectCode,
  fields,
  onChange,
}: SourceFieldImportCardProps) {
  const [dataSourceId, setDataSourceId] = useState("");
  const [tableName, setTableName] = useState("");
  const [dialog, setDialog] = useState<"all" | "partial" | null>(null);
  const [selectedColumns, setSelectedColumns] = useState<Set<string>>(new Set());

  const dataSourcesQuery = useQuery({
    queryKey: ["modeling", "field-import-datasources", projectCode],
    queryFn: () => listDataSources({ pageNo: 1, pageSize: 200 }),
    enabled: Boolean(projectCode),
  });

  const dataSources = dataSourcesQuery.data?.totalList || [];
  const selectedDataSource = dataSources.find(
    (item: DataSourceRecord) => String(item.id || "") === dataSourceId,
  );

  const databasesQuery = useQuery({
    queryKey: ["modeling", "field-import-databases", projectCode, dataSourceId],
    queryFn: () => listDataSourceDatabases(dataSourceId),
    enabled: Boolean(dataSourceId && !selectedDataSource?.database),
  });

  const database =
    String(selectedDataSource?.database || "").trim() ||
    String(databasesQuery.data?.[0] || "").trim();

  const tablesQuery = useQuery({
    queryKey: ["modeling", "field-import-tables", projectCode, dataSourceId, database],
    queryFn: () =>
      listDataSourceTables(dataSourceId, {
        database,
        schema: selectedDataSource?.schema,
        limit: 500,
      }),
    enabled: Boolean(dataSourceId && database),
  });

  const columnsQuery = useQuery({
    queryKey: [
      "modeling",
      "field-import-columns",
      projectCode,
      dataSourceId,
      database,
      selectedDataSource?.schema || "",
      tableName,
    ],
    queryFn: () =>
      listDataSourceColumns(dataSourceId, {
        database,
        schema: selectedDataSource?.schema,
        table: tableName,
      }),
    enabled: Boolean(dataSourceId && database && tableName),
  });

  useEffect(() => {
    setTableName("");
    setDialog(null);
    setSelectedColumns(new Set());
  }, [dataSourceId]);

  const dataSourceOptions = useMemo(
    () =>
      dataSources
        .filter((item: DataSourceRecord) => item.id)
        .map((item: DataSourceRecord) => ({
          value: String(item.id),
          label: item.name || String(item.id),
          icon: <DatabaseIcons dbType={item.dbType} width="14px" height="14px" />,
        })),
    [dataSources],
  );

  const tableOptions = useMemo(
    () =>
      (tablesQuery.data || []).map((table) => ({
        value: table.name,
        label: table.name,
      })),
    [tablesQuery.data],
  );

  const columns = useMemo(
    () =>
      [...(columnsQuery.data || [])].sort(
        (left, right) =>
          Number(left.ordinalPosition || 0) - Number(right.ordinalPosition || 0),
      ),
    [columnsQuery.data],
  );

  const closeDialog = () => {
    setDialog(null);
    setSelectedColumns(new Set());
  };

  const applyColumns = (picked: DataSourceCatalogColumn[]) => {
    if (!tableName || !picked.length) return;
    onChange(mergeImportedFields(fields, picked, tableName));
    toast.success(
      "已从 " + tableName + " 导入 " + picked.length + " 个字段，同名字段已覆盖",
    );
    closeDialog();
  };

  const openPartialDialog = () => {
    setSelectedColumns(new Set());
    setDialog("partial");
  };

  const toggleColumn = (name: string, checked: boolean) => {
    setSelectedColumns((current) => {
      const next = new Set(current);
      if (checked) next.add(name);
      else next.delete(name);
      return next;
    });
  };

  const allSelected =
    columns.length > 0 && selectedColumns.size === columns.length;

  return (
    <>
      <div className="mb-3 rounded border border-[var(--yak-components-panel-border)] bg-white px-3 py-2.5">
        <div className="grid grid-cols-[112px_minmax(0,1fr)] items-center gap-x-2 gap-y-2">
          <span className="text-xs text-[var(--yak-components-panel-text)]">
            查找已有表/视图：
          </span>
          <div className="flex min-w-0 items-center">
            <div className="w-[180px] shrink-0">
              <SourceSelect
                value={dataSourceId}
                options={dataSourceOptions}
                placeholder={
                  dataSourcesQuery.isPending ? "正在加载数据源..." : "请选择数据源"
                }
                disabled={dataSourcesQuery.isPending}
                triggerClassName="w-full rounded-r-none"
                onChange={setDataSourceId}
              />
            </div>
            <div className="-ml-px min-w-0 flex-1">
              <SourceSelect
                value={tableName}
                options={tableOptions}
                placeholder={
                  !dataSourceId
                    ? "请先选择数据源"
                    : databasesQuery.isFetching || tablesQuery.isPending
                      ? "正在加载来源表..."
                      : "请选择来源表"
                }
                disabled={
                  !dataSourceId ||
                  !database ||
                  databasesQuery.isFetching ||
                  tablesQuery.isPending
                }
                triggerClassName="w-full rounded-l-none"
                onChange={(value) => {
                  setTableName(value);
                  setDialog(null);
                  setSelectedColumns(new Set());
                }}
              />
            </div>
          </div>

          <span className="self-start pt-2 text-xs text-[var(--yak-components-panel-text)]">
            已引用的表/视图：
          </span>
          <div className="min-h-8">
            {tableName ? (
              <div className="flex h-8 max-w-[560px] items-center gap-2 rounded bg-[#f7f7f8] px-2.5">
                <span className="shrink-0 text-[11px] text-[var(--yak-components-muted-text)]">
                  表
                </span>
                <span
                  className="min-w-0 flex-1 truncate text-xs text-[var(--yak-components-panel-text)]"
                  title={tableName}
                >
                  {tableName}
                </span>
                <button
                  type="button"
                  title="导入全部字段"
                  aria-label="导入全部字段"
                  disabled={columnsQuery.isPending || columnsQuery.isError || !columns.length}
                  onClick={() => setDialog("all")}
                  className="inline-flex size-6 shrink-0 cursor-pointer items-center justify-center rounded text-[#5664ff] transition-colors hover:bg-[#eceeff] disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <ListPlus size={14} />
                </button>
                <button
                  type="button"
                  title="导入部分字段"
                  aria-label="导入部分字段"
                  disabled={columnsQuery.isPending || columnsQuery.isError || !columns.length}
                  onClick={openPartialDialog}
                  className="inline-flex size-6 shrink-0 cursor-pointer items-center justify-center rounded text-[#5664ff] transition-colors hover:bg-[#eceeff] disabled:cursor-not-allowed disabled:opacity-40"
                >
                  <ListChecks size={14} />
                </button>
                <button
                  type="button"
                  title="移除来源表"
                  aria-label="移除来源表"
                  onClick={() => {
                    setTableName("");
                    setDialog(null);
                    setSelectedColumns(new Set());
                  }}
                  className="inline-flex size-6 shrink-0 cursor-pointer items-center justify-center rounded text-[#b2b7c2] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]"
                >
                  <X size={13} />
                </button>
              </div>
            ) : null}
          </div>
        </div>

        {dataSourceId && !database && !databasesQuery.isFetching ? (
          <p className="ml-[120px] mt-1 text-xs text-[var(--yak-components-danger-text)]">
            当前数据源未绑定可用数据库，暂时无法加载来源表。
          </p>
        ) : null}

        {tablesQuery.isError ? (
          <p className="ml-[120px] mt-1 text-xs text-[var(--yak-components-danger-text)]">
            来源表加载失败，请检查数据源连接后重试。
          </p>
        ) : null}

        {tableName && columnsQuery.isPending ? (
          <p className="ml-[120px] mt-1 text-xs text-[var(--yak-components-muted-text)]">
            正在读取来源表字段...
          </p>
        ) : null}
        {tableName && columnsQuery.isError ? (
          <p className="ml-[120px] mt-1 text-xs text-[var(--yak-components-danger-text)]">
            来源字段读取失败，请检查数据源连接后重试。
          </p>
        ) : null}
      </div>

      <Modal
        open={dialog === "all"}
        centered
        width={440}
        title="导入全部字段"
        onClose={closeDialog}
        footer={
          <>
            <Button size="sm" variant="secondary" onClick={closeDialog}>
              取消
            </Button>
            <Button
              size="sm"
              variant="primary"
              onClick={() => applyColumns(columns)}
            >
              确认导入
            </Button>
          </>
        }
      >
        <div className="space-y-2 text-sm leading-6">
          <p>
            将从来源表 <span className="font-medium">{tableName}</span> 导入全部
            {columns.length} 个字段。
          </p>
          <p className="text-[var(--yak-components-muted-text)]">
            同名字段会按来源表元数据覆盖，不存在的字段会追加；当前其它字段和分区字段不会删除。
          </p>
        </div>
      </Modal>

      <Modal
        open={dialog === "partial"}
        centered
        width={900}
        title={tableName ? tableName + " · 导入部分字段" : "导入部分字段"}
        onClose={closeDialog}
        footer={
          <>
            <Button size="sm" variant="secondary" onClick={closeDialog}>
              取消
            </Button>
            <Button
              size="sm"
              variant="primary"
              isDisabled={!selectedColumns.size}
              onClick={() =>
                applyColumns(
                  columns.filter((column) => selectedColumns.has(column.name)),
                )
              }
            >
              导入所选字段
            </Button>
          </>
        }
      >
        <div className="max-h-[520px] overflow-auto rounded border border-[var(--yak-components-panel-border)]">
          <table className="w-full border-collapse text-xs">
            <thead className="sticky top-0 z-[1] bg-[#fafafa] text-left text-[var(--yak-components-panel-text)]">
              <tr>
                <th className="w-12 border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2 text-center">
                  <Checkbox
                    checked={allSelected}
                    aria-label="选择全部字段"
                    onCheckedChange={(checked) =>
                      setSelectedColumns(
                        checked === true
                          ? new Set(columns.map((column) => column.name))
                          : new Set(),
                      )
                    }
                  />
                </th>
                <th className="w-14 border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2">
                  序号
                </th>
                <th className="min-w-[160px] border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2">
                  字段名称
                </th>
                <th className="min-w-[120px] border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2">
                  类型
                </th>
                <th className="min-w-[180px] border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2">
                  字段显示名
                </th>
                <th className="w-16 border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2 text-center">
                  主键
                </th>
                <th className="w-16 border-b border-[var(--yak-components-panel-border)] px-3 py-2 text-center">
                  非空
                </th>
              </tr>
            </thead>
            <tbody>
              {columns.map((column, index) => (
                <tr key={column.name} className="hover:bg-[var(--yak-color-hover)]">
                  <td className="border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2 text-center">
                    <Checkbox
                      checked={selectedColumns.has(column.name)}
                      aria-label={"选择字段 " + column.name}
                      onCheckedChange={(checked) =>
                        toggleColumn(column.name, checked === true)
                      }
                    />
                  </td>
                  <td className="border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2">
                    {index + 1}
                  </td>
                  <td className="border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2">
                    {column.name}
                  </td>
                  <td className="border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2">
                    {column.typeName || "-"}
                  </td>
                  <td className="border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2">
                    {column.remarks || "-"}
                  </td>
                  <td className="border-b border-r border-[var(--yak-components-panel-border)] px-3 py-2 text-center">
                    {column.primaryKey ? "是" : "否"}
                  </td>
                  <td className="border-b border-[var(--yak-components-panel-border)] px-3 py-2 text-center">
                    {column.nullable === false ? "是" : "否"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Modal>
    </>
  );
}

export default SourceFieldImportCard;
