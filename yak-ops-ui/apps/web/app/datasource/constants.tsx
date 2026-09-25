import { Code2, FlaskConical, ShieldCheck } from 'lucide-react';
import type { ReactNode } from 'react';

import type {
  DataSourceGroup,
  DataSourceOptionItem,
  DataSourceSummary,
  PaginationInfo,
} from './types';

interface IntlFormatter {
  formatMessage: (descriptor: { id: string }) => string;
}

export const PAGE_DEFAULT_PAGINATION: PaginationInfo = {
  pageNo: 1,
  pageSize: 10,
  total: 0,
};

export const DATA_SOURCE_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

export const EMPTY_DATA_SOURCE_SUMMARY: DataSourceSummary = {
  total: 0,
  connected: 0,
  disconnected: 0,
  unknown: 0,
  environmentCount: 0,
};

export const COMMON_DB_OPTIONS: DataSourceOptionItem[] = [
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'Oracle', value: 'ORACLE' },
  { label: 'PostgreSQL', value: 'POSTGRE_SQL' },
];

export const ENVIRONMENT_OPTIONS: DataSourceOptionItem[] = [
  { label: 'DEVELOP', value: 'DEVELOP' },
  { label: 'TEST', value: 'TEST' },
  { label: 'PROD', value: 'PROD' },
];

const relationalDataSource = (dbType: string) => ({
  onlyDiScript: false,
  dbType,
  type: dbType,
  connectorType: 'Jdbc',
});

export const getDataSourceGroupList = (intl: IntlFormatter): DataSourceGroup[] => [
  {
    groupKey: 'relational',
    groupName: intl.formatMessage({ id: 'pages.datasource.group.relational' }),
    datasourceList: [
      relationalDataSource('MYSQL'),
      relationalDataSource('ORACLE'),
      relationalDataSource('POSTGRE_SQL'),
    ],
  },
];

interface EnvironmentTagConfig {
  text: string;
  color: string;
  backgroundColor: string;
  icon: ReactNode;
}

export const getEnvironmentTagConfigMap = (
  intl: IntlFormatter,
): Record<string, EnvironmentTagConfig> => ({
  PROD: {
    text: intl.formatMessage({ id: 'pages.datasource.environment.prod' }),
    color: '#ff4d4f',
    backgroundColor: '#fff2f0',
    icon: <ShieldCheck size={12} />,
  },
  TEST: {
    text: intl.formatMessage({ id: 'pages.datasource.environment.test' }),
    color: '#52c41a',
    backgroundColor: '#f6ffed',
    icon: <FlaskConical size={12} />,
  },
  DEVELOP: {
    text: intl.formatMessage({ id: 'pages.datasource.environment.develop' }),
    color: '#1677ff',
    backgroundColor: '#e6f4ff',
    icon: <Code2 size={12} />,
  },
});

export const getDataSourceEnvironmentTabs = (intl: IntlFormatter) => {
  const environmentTagConfigMap = getEnvironmentTagConfigMap(intl);
  return [
    {
      key: 'all',
      label: intl.formatMessage({ id: 'pages.datasource.environment.all' }),
      value: undefined,
    },
    ...ENVIRONMENT_OPTIONS.map((item) => ({
      key: item.value,
      label: environmentTagConfigMap[item.value]?.text || item.label,
      value: item.value,
    })),
  ];
};
