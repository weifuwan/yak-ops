import { render, screen } from '@testing-library/react';

import DataSourceStatus from './DataSourceStatus';

jest.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }) =>
      ({
        'pages.datasource.status.connected': '已连接',
        'pages.datasource.status.connectedTooltip': '数据源连接正常',
        'pages.datasource.status.disconnected': '连接失败',
        'pages.datasource.status.disconnectedTooltip': '最近一次连接测试失败',
        'pages.datasource.status.unknown': '待检测',
        'pages.datasource.status.unknownTooltip':
          '尚未进行连接测试，或连接参数刚刚发生变化',
        'pages.datasource.status.connecting': '连接中',
        'pages.datasource.status.connectingTooltip': '正在进行连接测试',
      } as Record<string, string>)[id] || id,
  }),
}));

describe('DataSourceStatus', () => {
  it.each([
    ['CONNECTED', '已连接'],
    ['DISCONNECTED', '连接失败'],
    ['UNKNOWN', '待检测'],
  ])('renders backend status %s', (status, expectedText) => {
    const { unmount } = render(<DataSourceStatus status={status} />);

    expect(screen.getByText(expectedText)).toBeInTheDocument();
    unmount();
  });

  it('keeps legacy connected status compatible', () => {
    render(<DataSourceStatus status="CONNECTED_SUCCESS" />);

    expect(screen.getByText('已连接')).toBeInTheDocument();
  });
});
