import {
  BarChart3,
  Braces,
  Database,
  FileCode2,
  LayoutDashboard,
  Rows3,
  TableProperties,
} from 'lucide-react';
import { Handle, Position, type NodeProps } from 'reactflow';
import { assetTypeLabel, type LineageAsset } from './types';
import { lineageAssetVisual } from './visual';

export interface LineageNodeData {
  asset: LineageAsset;
  root: boolean;
}

const iconByType = {
  TABLE: TableProperties,
  COLUMN: Braces,
  SQL_TASK: FileCode2,
  DATASET: Database,
  DATASET_FIELD: Rows3,
  CHART: BarChart3,
  DASHBOARD: LayoutDashboard,
};

const subtitle = (asset: LineageAsset) => {
  if (asset.assetType === 'COLUMN') {
    return [asset.tableName, asset.columnName].filter(Boolean).join('.') || asset.sourceType || '字段';
  }
  if (asset.assetType === 'TABLE') {
    return [asset.databaseName, asset.schemaName, asset.tableName]
      .filter(Boolean)
      .join('.') || asset.sourceType || '数据表';
  }
  if (asset.assetType === 'DATASET_FIELD') return asset.sourceType || 'Dataset 字段';
  if (asset.assetType === 'SQL_TASK') return '数据开发';
  if (asset.assetType === 'CHART') return 'Analysis';
  if (asset.assetType === 'DASHBOARD') return '数据消费';
  return asset.sourceType || assetTypeLabel[asset.assetType];
};

export default function LineageNode({ data, selected }: NodeProps<LineageNodeData>) {
  const { asset, root } = data;
  const Icon = iconByType[asset.assetType];
  const visual = lineageAssetVisual[asset.assetType];
  const active = root || selected;

  return (
    <div
      className="relative flex h-[78px] w-[252px] items-center gap-3 border bg-white px-3"
      style={{
        borderColor: active ? '#4C78C9' : '#D8DDE5',
        borderRadius: 6,
        boxShadow: 'none',
      }}
    >
      {root ? (
        <div className="absolute -top-[18px] left-0 flex h-[18px] items-center border border-b-0 border-[#4C78C9] bg-white px-2 text-[10px] font-medium text-[#3F6FBF]">
          中心资产
        </div>
      ) : null}

      <Handle
        type="target"
        position={Position.Left}
        style={{
          width: 8,
          height: 8,
          left: -5,
          border: '1.5px solid #5F88C9',
          background: '#fff',
        }}
      />

      <div className="flex h-8 w-8 shrink-0 items-center justify-center border border-[#E4E7EC] bg-[#F8F9FB]">
        <Icon size={17} strokeWidth={1.7} style={{ color: visual.accent }} />
      </div>

      <div className="min-w-0 flex-1">
        <div
          className="truncate text-[13px] font-semibold leading-5 text-[#1F2937]"
          title={asset.name}
        >
          {asset.name}
        </div>
        <div
          className="mt-0.5 truncate text-[11px] leading-4 text-[#8A94A3]"
          title={subtitle(asset)}
        >
          {subtitle(asset)}
        </div>
        <div className="mt-1 flex items-center gap-1.5 text-[10px] text-[#667085]">
          <span className="h-1.5 w-1.5 rounded-full" style={{ background: visual.accent }} />
          <span>{assetTypeLabel[asset.assetType]}</span>
        </div>
      </div>

      <Handle
        type="source"
        position={Position.Right}
        style={{
          width: 8,
          height: 8,
          right: -5,
          border: '1.5px solid #5F88C9',
          background: '#fff',
        }}
      />
    </div>
  );
}
