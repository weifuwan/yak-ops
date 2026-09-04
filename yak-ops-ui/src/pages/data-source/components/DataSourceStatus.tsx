import type { DataSourceConnectionStatus } from '@/services/data-source';
import {
  CheckCircleFilled,
  CloseCircleFilled,
  LoadingOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Tag, Tooltip } from 'antd';
import type { ReactNode } from 'react';

interface DataSourceStatusProps {
  status?: DataSourceConnectionStatus;
}

interface StatusConfigItem {
  color: 'success' | 'error' | 'processing' | 'default' | 'warning';
  icon: ReactNode;
  text: string;
  tooltip?: string;
}

const DataSourceStatus = ({ status }: DataSourceStatusProps) => {
  const intl = useIntl();
  const connectedConfig: StatusConfigItem = {
    color: 'success',
    icon: <CheckCircleFilled />,
    text: intl.formatMessage({ id: 'pages.datasource.status.connected' }),
    tooltip: intl.formatMessage({
      id: 'pages.datasource.status.connectedTooltip',
    }),
  };
  const disconnectedConfig: StatusConfigItem = {
    color: 'error',
    icon: <CloseCircleFilled />,
    text: intl.formatMessage({ id: 'pages.datasource.status.disconnected' }),
    tooltip: intl.formatMessage({
      id: 'pages.datasource.status.disconnectedTooltip',
    }),
  };
  const unknownConfig: StatusConfigItem = {
    color: 'default',
    icon: <MinusCircleOutlined />,
    text: intl.formatMessage({ id: 'pages.datasource.status.unknown' }),
    tooltip: intl.formatMessage({ id: 'pages.datasource.status.unknownTooltip' }),
  };
  const statusConfigMap: Record<string, StatusConfigItem> = {
    CONNECTED: connectedConfig,
    CONNECTED_SUCCESS: connectedConfig,
    DISCONNECTED: disconnectedConfig,
    CONNECTED_FAILED: disconnectedConfig,
    UNKNOWN: unknownConfig,
    CONNECTED_NONE: unknownConfig,
    CONNECTING: {
      color: 'processing',
      icon: <LoadingOutlined spin />,
      text: intl.formatMessage({ id: 'pages.datasource.status.connecting' }),
      tooltip: intl.formatMessage({
        id: 'pages.datasource.status.connectingTooltip',
      }),
    },
  };

  const normalized = String(status || 'UNKNOWN').trim().toUpperCase();
  const currentConfig = statusConfigMap[normalized] || unknownConfig;

  return (
    <Tooltip title={currentConfig.tooltip}>
      <Tag
        color={currentConfig.color}
        icon={currentConfig.icon}
        style={{
          marginInlineEnd: 0,
          borderRadius: 999,
          paddingInline: 10,
          fontSize: 12,
          width: '80px',
          lineHeight: '20px',
        }}
      >
        {currentConfig.text}
      </Tag>
    </Tooltip>
  );
};

export default DataSourceStatus;
