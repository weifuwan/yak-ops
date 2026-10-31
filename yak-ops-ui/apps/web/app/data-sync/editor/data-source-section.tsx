import { CollapseSection } from "@yak-ops/yak-ui";
import { useNavigate } from "react-router-dom";
import { DataSourceEndpointCard } from "./data-source-endpoint-card";
import type { DataSourceRecord } from "@/service/datasource";

interface Props {
  id?: string;
  sourceType: string;
  targetType: string;
  sourceDataSources: DataSourceRecord[];
  targetDataSources: DataSourceRecord[];
  sourceDataSourceId: string;
  targetDataSourceId: string;
  loading: boolean;
  onRefresh: () => void | Promise<void>;
  onSourceChange: (value: string) => void;
  onTargetChange: (value: string) => void;
}

export function DataSourceSection({
  id,
  sourceType,
  targetType,
  sourceDataSources,
  targetDataSources,
  sourceDataSourceId,
  targetDataSourceId,
  loading,
  onRefresh,
  onSourceChange,
  onTargetChange,
}: Props) {
  const navigate = useNavigate();
  const createDataSource = () =>
    navigate(
      "/data-source?create=1&returnTo=" +
        encodeURIComponent(id ? `/data-sync/${id}/edit` : "/data-sync/new"),
    );

  return (
    <CollapseSection id="datasource" title="数据源">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <DataSourceEndpointCard
          title="来源"
          dataSourceType={sourceType}
          dataSources={sourceDataSources}
          dataSourceId={sourceDataSourceId}
          refreshing={loading}
          onRefresh={onRefresh}
          onCreateDataSource={createDataSource}
          onDataSourceChange={onSourceChange}
        />
        <DataSourceEndpointCard
          title="去向"
          dataSourceType={targetType}
          dataSources={targetDataSources}
          dataSourceId={targetDataSourceId}
          refreshing={loading}
          onRefresh={onRefresh}
          onCreateDataSource={createDataSource}
          onDataSourceChange={onTargetChange}
        />
      </div>
    </CollapseSection>
  );
}
