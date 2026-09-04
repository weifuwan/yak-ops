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
  { label: 'MYSQL', value: 'MYSQL' },
  { label: 'ORACLE', value: 'ORACLE' },
  { label: 'POSTGRE_SQL', value: 'POSTGRE_SQL' },
  { label: 'DORIS', value: 'DORIS' },
  { label: 'KINGBASE', value: 'KINGBASE' },
  { label: 'DAMENG', value: 'DAMENG' },
];

export const ENVIRONMENT_OPTIONS: DataSourceOptionItem[] = [
  { label: 'DEVELOP', value: 'DEVELOP' },
  { label: 'TEST', value: 'TEST' },
  { label: 'PROD', value: 'PROD' },
];

export const getDataSourceGroupList = (intl: IntlFormatter): DataSourceGroup[] => [
  {
    groupKey: 'relational',
    groupName: intl.formatMessage({ id: 'pages.datasource.group.relational' }),
    datasourceList: [
      {
        onlyDiScript: false,
        dbType: 'MYSQL',
        type: 'MYSQL',
        connectorType: 'Jdbc',
      },
      {
        onlyDiScript: false,
        dbType: 'ORACLE',
        type: 'ORACLE',
        connectorType: 'Jdbc',
      },
      {
        onlyDiScript: false,
        dbType: 'POSTGRE_SQL',
        type: 'POSTGRE_SQL',
        connectorType: 'Jdbc',
      },
      {
        onlyDiScript: false,
        dbType: 'KINGBASE',
        type: 'KINGBASE',
        connectorType: 'Jdbc',
      },
      {
        onlyDiScript: false,
        dbType: 'DAMENG',
        type: 'DAMENG',
        connectorType: 'Jdbc',
      },
    ],
  },
  {
    groupKey: 'olap',
    groupName: intl.formatMessage({ id: 'pages.datasource.group.olap' }),
    datasourceList: [
      {
        onlyDiScript: false,
        dbType: 'DORIS',
        type: 'DORIS',
        connectorType: 'Doris',
      },
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

export const PAGE_ANIMATION = {
  fadeUp: {
    hidden: { opacity: 0, y: 18 },
    visible: {
      opacity: 1,
      y: 0,
      transition: {
        duration: 0.45,
        ease: [0.22, 1, 0.36, 1],
      },
    },
  },
  sectionStagger: {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: 0.08,
        delayChildren: 0.06,
      },
    },
  },
  cardStagger: {
    hidden: {},
    visible: {
      transition: {
        staggerChildren: 0.06,
      },
    },
  },
};
