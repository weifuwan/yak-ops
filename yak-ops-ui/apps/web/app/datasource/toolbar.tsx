import {
  Button,
  Combobox,
  ComboboxContent,
  ComboboxEmptyState,
  ComboboxInput,
  ComboboxItem,
  Input,
  Tabs,
  TabsList,
  TabsTab,
} from "@yak-ops/yak-ui";
import { motion } from "framer-motion";
import { Grid2X2, LayoutList, Search, X } from "lucide-react";

import { useIntl } from "./i18n";
import {
  COMMON_DB_OPTIONS,
  getDataSourceEnvironmentTabs,
  PAGE_ANIMATION,
} from "./constants";
import DatabaseIcons from "./icons/DatabaseIcons";
import type { DataSourceViewMode } from "./types";

interface DataSourceToolbarProps {
  environment?: string;
  dbType?: string;
  keyword: string;
  viewMode: DataSourceViewMode;
  hasActiveFilters: boolean;
  onEnvironmentChange: (value?: string) => void;
  onDbTypeChange: (value?: string) => void;
  onKeywordChange: (value: string) => void;
  onViewModeChange: (value: DataSourceViewMode) => void;
  onReset: () => void;
}

const DB_TYPE_LABELS: Record<string, string> = {
  MYSQL: "MYSQL",
  TIDB: "TiDB",
  GOLDENDB: "GoldenDB",
  GBASE8C: "GBase 8c",
  GBASE8A: "GBase 8a",
  GBASE8S: "GBase 8s",
  HANA: "SAP HANA",
  ORACLE: "ORACLE",
  POSTGRE_SQL: "PostgreSQL",
  DB2: "IBM Db2",
  OPEN_GAUSS: "openGauss",
  SQL_SERVER: "SQL Server",
  OCEANBASE: "OceanBase",
  YASHAN_DB: "YashanDB",
  HIGHGO: "HighGo",
  IRIS: "InterSystems IRIS",
  XUGU: "XuguDB",
  DUCKDB: "DuckDB",
  DORIS: "Doris",
  STARROCKS: "StarRocks",
  CLICKHOUSE: "ClickHouse",
  ELASTICSEARCH7: "Elasticsearch 7",
  ELASTICSEARCH8: "Elasticsearch 8",
  MONGODB: "MongoDB",
  KINGBASE: "KINGBASE",
  DAMENG: "DAMENG",
};

const dbTypeLabel = (value: string) => DB_TYPE_LABELS[value] || value;

const DbTypeLabel = ({ value }: { value: string }) => (
  <span className="flex min-w-0 items-center gap-2">
    <span className="flex h-[18px] w-[18px] shrink-0 items-center justify-center">
      <DatabaseIcons dbType={value} width="18px" height="18px" />
    </span>
    <span className="truncate">{dbTypeLabel(value)}</span>
  </span>
);

const DataSourceToolbar = ({
  environment,
  dbType,
  keyword,
  viewMode,
  hasActiveFilters,
  onEnvironmentChange,
  onDbTypeChange,
  onKeywordChange,
  onViewModeChange,
  onReset,
}: DataSourceToolbarProps) => {
  const intl = useIntl();
  const environmentTabs = getDataSourceEnvironmentTabs(intl);

  return (
    <motion.section
      variants={PAGE_ANIMATION.fadeUp}
      className="flex min-h-9 items-end justify-between gap-6 border-b border-solid border-[#eceef2] max-xl:flex-col max-xl:items-stretch max-xl:gap-3"
    >
      <div className="flex items-end">
        <Tabs
          value={environment || "all"}
          onValueChange={(key) => {
            const target = environmentTabs.find((item) => item.key === key);
            onEnvironmentChange(target?.value);
          }}
        >
          <TabsList className="gap-8 border-b-0">
            {environmentTabs.map((item) => (
              <TabsTab
                key={item.key}
                value={item.key}
                className="px-0 pb-[10px] pt-0 text-[13px]"
              >
                {item.label}
              </TabsTab>
            ))}
          </TabsList>
        </Tabs>
      </div>

      <div className="flex flex-wrap items-center justify-end gap-2 pb-[5px] max-xl:justify-start">
        <div className="relative w-[188px]">
          <Combobox<string>
            value={dbType ?? null}
            itemToStringLabel={(value) => dbTypeLabel(value)}
            onValueChange={(value) => onDbTypeChange(value ?? undefined)}
          >
            <ComboboxInput
              placeholder={intl.formatMessage({
                id: "pages.datasource.toolbar.typePlaceholder",
              })}
              className={dbType ? "pr-9 text-xs" : "text-xs"}
            />
            {dbType ? (
              <Button
                variant="ghost"
                size="small"
                type="button"
                aria-label="Clear datasource type"
                className="absolute right-1 top-1/2 z-10 h-7 w-7 -translate-y-1/2 p-0 text-[#98a2b3]"
                onClick={() => onDbTypeChange(undefined)}
              >
                <X size={13} />
              </Button>
            ) : null}
            <ComboboxContent className="min-w-[188px]">
              {COMMON_DB_OPTIONS.map((option) => (
                <ComboboxItem key={option.value} value={option.value}>
                  <DbTypeLabel value={option.value} />
                </ComboboxItem>
              ))}
              <ComboboxEmptyState>
                {intl.formatMessage({
                  id: "pages.datasource.typeSelector.empty",
                })}
              </ComboboxEmptyState>
            </ComboboxContent>
          </Combobox>
        </div>

        <div className="relative w-[292px] max-md:w-[220px]">
          <Search
            size={15}
            strokeWidth={1.8}
            className="pointer-events-none absolute left-3 top-1/2 z-10 -translate-y-1/2 text-[#8f949e]"
          />
          <Input
            value={keyword}
            className="pl-9 pr-9 text-xs"
            placeholder={intl.formatMessage({
              id: "pages.datasource.toolbar.searchPlaceholder",
            })}
            onChange={(event) => onKeywordChange(event.target.value)}
          />
          {keyword ? (
            <Button
              variant="ghost"
              size="small"
              type="button"
              aria-label="Clear search"
              className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2 p-0 text-[#98a2b3]"
              onClick={() => onKeywordChange("")}
            >
              <X size={13} />
            </Button>
          ) : null}
        </div>

        {hasActiveFilters ? (
          <Button
            variant="ghost"
            size="small"
            className="h-9 px-2.5 text-[12px] text-[#777c86]"
            onClick={onReset}
          >
            {intl.formatMessage({ id: "pages.datasource.toolbar.reset" })}
          </Button>
        ) : null}

        <div className="flex h-9 items-center gap-0.5 rounded-[10px] bg-[#f4f5f7] p-[3px]">
          <Button
            variant="ghost"
            size="small"
            title={intl.formatMessage({
              id: "pages.datasource.toolbar.gridView",
            })}
            aria-label={intl.formatMessage({
              id: "pages.datasource.toolbar.gridView",
            })}
            className={[
              "h-[30px] w-[30px] rounded-[7px] border-0 p-0",
              viewMode === "grid"
                ? "bg-white text-[#2d313a] shadow-[0_1px_4px_rgba(31,35,41,0.10)]"
                : "bg-transparent text-[#92969f] hover:text-[#555b66]",
            ].join(" ")}
            onClick={() => onViewModeChange("grid")}
          >
            <Grid2X2 size={15} strokeWidth={1.8} />
          </Button>

          <Button
            variant="ghost"
            size="small"
            title={intl.formatMessage({
              id: "pages.datasource.toolbar.listView",
            })}
            aria-label={intl.formatMessage({
              id: "pages.datasource.toolbar.listView",
            })}
            className={[
              "h-[30px] w-[30px] rounded-[7px] border-0 p-0",
              viewMode === "list"
                ? "bg-white text-[#2d313a] shadow-[0_1px_4px_rgba(31,35,41,0.10)]"
                : "bg-transparent text-[#92969f] hover:text-[#555b66]",
            ].join(" ")}
            onClick={() => onViewModeChange("list")}
          >
            <LayoutList size={16} strokeWidth={1.8} />
          </Button>
        </div>
      </div>
    </motion.section>
  );
};

export default DataSourceToolbar;
