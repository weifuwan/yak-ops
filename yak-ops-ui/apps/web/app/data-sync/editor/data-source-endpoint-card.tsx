import { Button, Field, FieldLabel } from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useMemo } from "react";
import { getDataSourceTypeLabel } from "@/app/datasource/constants";
import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import type { DataSourceRecord } from "@/service/datasource";

interface DataSourceEndpointCardProps {
  title: string;
  dataSourceType: string;
  dataSources: DataSourceRecord[];
  dataSourceId: string;
  refreshing: boolean;
  onRefresh: () => void | Promise<void>;
  onCreateDataSource: () => void;
  onDataSourceChange: (value: string) => void;
}

export function DataSourceEndpointCard({
  title,
  dataSourceType,
  dataSources,
  dataSourceId,
  refreshing,
  onRefresh,
  onCreateDataSource,
  onDataSourceChange,
}: DataSourceEndpointCardProps) {
  const dataSourceOptions = useMemo(
    () =>
      dataSources.flatMap((item) =>
        item.id
          ? [
              {
                value: item.id,
                label: item.name || item.id,
                searchText: [item.dbType, item.database, item.schema].filter(Boolean).join(" "),
              },
            ]
          : [],
      ),
    [dataSources],
  );
  return (
    <div className="rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
      <div className="text-sm font-semibold text-[var(--yak-components-field-label)]">{title}</div>
      <div className="mt-4 space-y-3">
        <Field className="grid grid-cols-[90px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>类型</FieldLabel>
          <div className="flex items-center gap-2 text-[13px] text-[var(--yak-components-field-label)]">
            <DatabaseIcons dbType={dataSourceType} width="16px" height="16px" />
            <span>{getDataSourceTypeLabel(dataSourceType)}</span>
          </div>
        </Field>

        <Field className="grid grid-cols-[90px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>数据源</FieldLabel>
          <DataSyncSearchableSelect
            value={dataSourceId || null}
            options={dataSourceOptions}
            placeholder="请选择数据源"
            searchPlaceholder="搜索数据源"
            emptyText="暂无数据源"
            refreshing={refreshing}
            onRefresh={onRefresh}
            footer={
              <Button
                size="small"
                variant="ghost"
                className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
                onClick={onCreateDataSource}
              >
                <Plus size={14} />
                新增数据源
              </Button>
            }
            onValueChange={onDataSourceChange}
          />
        </Field>
      </div>
    </div>
  );
}
