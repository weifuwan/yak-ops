import { useEffect, useState } from "react";
import { Alert, Button, FieldItem, FieldLabel, Radio, RadioGroup, toast } from "@yak-ops/yak-ui";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { DataSourceCatalogTable } from "@/service/datasource";
import { resolvePastedDataSourceTables } from "@/service/datasource/table-selection";
import type {
  DataSyncCapabilities,
  DataSyncMappingTemplate,
  DataSyncTableMapping,
  DataSyncTableSelection,
  DataSyncWriteMode,
} from "@/service/data-sync";
import { ContentField, ContentSection } from "./content-layout";
import { TableTransferPanel, type TransferFilters } from "./table-transfer-panel";
import { useTableSelectionCatalog } from "./use-table-selection-catalog";
import { RegexTableSelection } from "./regex-table-selection";
import { PasteTablesDialog } from "./paste-tables-dialog";
import { addSelectedTables } from "./table-selection";
import { catalogPath, tableKey, tablePathLabel } from "./table-catalog";
import type { EditorForm } from "./types";
import type { EditorConfirmation } from "./use-definition-restoration";
import type { useResolveTableSelection } from "./use-resolve-table-selection";

interface Props {
  form: EditorForm;
  mappings: DataSyncTableMapping[];
  targetTables: DataSourceCatalogTable[];
  capabilities?: DataSyncCapabilities;
  prepareSchema: boolean;
  initialLoad: boolean;
  continuous: boolean;
  writeModes: DataSyncWriteMode[];
  resolution: ReturnType<typeof useResolveTableSelection>;
  disabled: boolean;
  onMappingsChange: (mappings: DataSyncTableMapping[]) => void;
  onRulesChange: (
    selection: DataSyncTableSelection | undefined,
    template: DataSyncMappingTemplate | undefined,
    resolved: boolean,
  ) => void;
  confirm: (value: EditorConfirmation) => void;
}

export function ReplicationTableSelection({
  form,
  mappings,
  targetTables,
  capabilities,
  prepareSchema,
  initialLoad,
  continuous,
  writeModes,
  resolution,
  disabled,
  onMappingsChange,
  onRulesChange,
  confirm,
}: Props) {
  const [sourceFilters, setSourceFilters] = useState<TransferFilters>({ database: "", table: "" });
  const [queryFilters, setQueryFilters] = useState(sourceFilters);
  const [targetFilters, setTargetFilters] = useState<TransferFilters>({ database: "", table: "" });
  const [sourceChecked, setSourceChecked] = useState<string[]>([]);
  const [targetChecked, setTargetChecked] = useState<string[]>([]);
  const [paste, setPaste] = useState<"source" | "selected">();
  const [error, setError] = useState("");
  useEffect(() => {
    const timer = setTimeout(() => {
      setQueryFilters(sourceFilters);
      setSourceChecked([]);
    }, 250);
    return () => clearTimeout(timer);
  }, [sourceFilters]);
  const catalog = useTableSelectionCatalog(
    form.selection ? "" : form.source.dataSourceId,
    queryFilters,
  );
  const maxTables = capabilities?.maxTables ?? 0;
  const regexReason =
    !initialLoad || !continuous || (form.startup?.mode && form.startup.mode !== "INITIAL")
      ? "正则选表需从全量开始持续同步"
      : !prepareSchema || !form.sink.schemaChangesEnabled
        ? "正则选表需创建目标表并同步结构变更"
        : "";
  const included = new Set(mappings.map((mapping) => tableKey(mapping.source)));
  const available = catalog.tables.map(catalogPath);
  let filterError = "";
  let selected = mappings.map((mapping) => mapping.source);
  try {
    const database = new RegExp(`^(?:${targetFilters.database || ".*"})$`);
    const table = new RegExp(`^(?:${targetFilters.table || ".*"})$`);
    selected = selected.filter(
      (item) => database.test(item.database || item.schema || "") && table.test(item.table),
    );
  } catch {
    filterError = "已选库表过滤正则无效";
  }
  const add = (tables: ReturnType<typeof catalogPath>[]) => {
    if (!writeModes[0]) return;
    onMappingsChange(
      addSelectedTables(
        mappings,
        tables,
        targetTables,
        { database: form.sink.database || undefined, schema: form.sink.schema || undefined },
        writeModes[0],
        prepareSchema,
        maxTables,
      ),
    );
    setSourceChecked([]);
    setError("");
  };
  const remove = () => {
    const selectedKeys = new Set(targetChecked);
    confirm({
      title: "移除已选库表",
      message: `将移除 ${selectedKeys.size} 张表及其目标映射和表级配置。`,
      apply: () => {
        onMappingsChange(mappings.filter((mapping) => !selectedKeys.has(tableKey(mapping.source))));
        setTargetChecked([]);
      },
    });
  };
  const switchMode = (regex: boolean) => {
    const apply = () => {
      onMappingsChange([]);
      setSourceChecked([]);
      setTargetChecked([]);
      onRulesChange(
        regex
          ? {
              mode: "REGEX",
              databasePattern: form.source.database
                ? `^${form.source.database.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}$`
                : ".*",
              tablePattern: ".*",
            }
          : undefined,
        regex
          ? {
              database: form.sink.database || "${database}",
              ...(form.sink.schema ? { schema: form.sink.schema } : {}),
              table: "${table}",
              write: { mode: "UPSERT" },
            }
          : undefined,
        !regex,
      );
    };
    if (mappings.length || form.selection)
      confirm({
        title: "切换选表方式",
        message: "切换后将清空已选库表及其目标映射和表级配置。",
        apply,
      });
    else apply();
  };
  const copy = async (tables: typeof selected) => {
    try {
      await navigator.clipboard.writeText(tables.map(tablePathLabel).join("\n"));
      toast.success("已复制库表名称");
    } catch {
      setError("复制失败，请检查浏览器剪贴板权限");
    }
  };
  return (
    <ContentSection id="source" title="数据来源">
      <ContentField label="选表方式" wide>
        <RadioGroup
          value={form.selection ? "REGEX" : "EXPLICIT"}
          disabled={disabled || resolution.loading}
          onValueChange={(mode) => switchMode(mode === "REGEX")}
          className="flex min-h-7 flex-wrap items-center gap-6"
        >
          <FieldItem>
            <FieldLabel className="flex items-center gap-2 text-xs font-normal">
              <Radio value="EXPLICIT" />
              选择指定库表
            </FieldLabel>
          </FieldItem>
          {capabilities?.regexSelection ? (
            <FieldItem>
              <FieldLabel className="flex items-center gap-2 text-xs font-normal">
                <Radio value="REGEX" disabled={Boolean(regexReason)} />
                使用正则选表
              </FieldLabel>
            </FieldItem>
          ) : null}
        </RadioGroup>
        {capabilities?.regexSelection && regexReason ? (
          <span className="mt-1 block text-xs text-[var(--yak-components-muted-text)]">
            {regexReason}
          </span>
        ) : null}
      </ContentField>
      {form.selection && form.mappingTemplate && capabilities ? (
        <RegexTableSelection
          dataSourceId={form.source.dataSourceId}
          selection={form.selection}
          template={form.mappingTemplate}
          mappings={mappings}
          capabilities={capabilities}
          resolution={resolution}
          disabled={disabled}
          initialLoad={initialLoad}
          resolved={Boolean(form.selectionResolved)}
          onRulesChange={(selection, template) => onRulesChange(selection, template, false)}
          onResolved={(next) => {
            onMappingsChange(next);
            onRulesChange(form.selection, form.mappingTemplate, true);
          }}
        />
      ) : (
        <div className="grid min-w-0 grid-cols-[minmax(0,1fr)_44px_minmax(0,1fr)] gap-3 max-lg:grid-cols-1">
          <TableTransferPanel
            title="源端库表"
            tables={available}
            checked={sourceChecked}
            onCheckedChange={setSourceChecked}
            filters={sourceFilters}
            onFiltersChange={setSourceFilters}
            onSearch={() => {
              setSourceChecked([]);
              setQueryFilters(sourceFilters);
            }}
            loading={catalog.isFetching}
            onRefresh={() => {
              setSourceChecked([]);
              if (JSON.stringify(sourceFilters) === JSON.stringify(queryFilters)) {
                void catalog.refetch();
                return;
              }
              setQueryFilters(sourceFilters);
            }}
            disabled={(table) =>
              included.has(tableKey(table)) ||
              mappings.some((mapping) => mapping.writeMode === "RETRACT") ||
              (!included.has(tableKey(table)) && mappings.length >= maxTables)
            }
            actions={
              <Button
                size="small"
                disabled={!form.source.dataSourceId}
                onClick={() => setPaste("source")}
              >
                批量粘贴选择表
              </Button>
            }
            footer={
              <div className="flex items-center gap-2">
                {catalog.hasNextPage ? (
                  <Button
                    size="small"
                    loading={catalog.isFetchingNextPage}
                    onClick={() => void catalog.fetchNextPage()}
                  >
                    加载更多
                  </Button>
                ) : null}
                <Button size="small" onClick={() => void copy(available)}>
                  复制已加载表名称
                </Button>
              </div>
            }
          />
          <div className="flex flex-col items-center justify-center gap-2 max-lg:flex-row">
            <Button
              size="small"
              aria-label="加入已选库表"
              disabled={!sourceChecked.length}
              onClick={() => {
                try {
                  add(available.filter((table) => sourceChecked.includes(tableKey(table))));
                } catch (failure) {
                  setError(failure instanceof Error ? failure.message : "添加库表失败");
                }
              }}
            >
              <ChevronRight size={16} />
            </Button>
            <Button
              size="small"
              aria-label="移除已选库表"
              disabled={!targetChecked.length}
              onClick={remove}
            >
              <ChevronLeft size={16} />
            </Button>
          </div>
          <TableTransferPanel
            title="已选库表"
            tables={selected}
            checked={targetChecked}
            onCheckedChange={setTargetChecked}
            filters={targetFilters}
            onFiltersChange={(filters) => {
              setTargetChecked([]);
              setTargetFilters(filters);
            }}
            actions={
              <Button size="small" disabled={!mappings.length} onClick={() => setPaste("selected")}>
                批量粘贴选择表
              </Button>
            }
            footer={
              <>
                <span>最多 {maxTables} 张表</span>
                <Button
                  size="small"
                  onClick={() => void copy(mappings.map((mapping) => mapping.source))}
                >
                  复制所有表名称
                </Button>
              </>
            }
          />
        </div>
      )}
      {error || filterError || catalog.isError ? (
        <Alert>{error || filterError || "来源库表加载失败"}</Alert>
      ) : null}
      {paste ? (
        <PasteTablesDialog
          title={paste === "source" ? "批量选择源端库表" : "批量选择已选库表"}
          onClose={() => setPaste(undefined)}
          onSubmit={async (names, signal) => {
            if (paste === "selected") {
              const found = names.map((name) => {
                const matches = mappings.filter(
                  ({ source }) => tablePathLabel(source) === name || source.table === name,
                );
                if (matches.length !== 1) throw new Error(`已选库表不存在或名称不唯一：${name}`);
                return tableKey(matches[0].source);
              });
              setTargetChecked(found);
              return;
            }
            const resolved = await resolvePastedDataSourceTables(
              form.source.dataSourceId,
              names,
              maxTables,
              signal,
            );
            if (!signal.aborted) add(resolved.map(catalogPath));
          }}
        />
      ) : null}
    </ContentSection>
  );
}
