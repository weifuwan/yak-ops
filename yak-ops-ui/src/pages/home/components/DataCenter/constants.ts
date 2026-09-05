import type {
  HomeDataCenterPeriodKey,
  HomeDataCenterTabKey,
} from '../../types';

export const OVERVIEW_TABS: Array<{
  key: HomeDataCenterTabKey;
  messageId: string;
}> = [
  { key: 'overview', messageId: 'pages.home.dataCenter.tab.overview' },
  { key: 'recent', messageId: 'pages.home.dataCenter.tab.recent' },
  { key: 'schedule', messageId: 'pages.home.dataCenter.tab.schedule' },
];

export const PERIOD_OPTIONS: Array<{
  key: HomeDataCenterPeriodKey;
  messageId: string;
}> = [
  { key: 'yesterday', messageId: 'pages.home.dataCenter.period.yesterday' },
  { key: '7d', messageId: 'pages.home.dataCenter.period.7d' },
  { key: '30d', messageId: 'pages.home.dataCenter.period.30d' },
];
