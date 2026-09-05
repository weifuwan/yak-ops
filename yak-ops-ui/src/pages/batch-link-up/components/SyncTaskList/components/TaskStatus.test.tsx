import { render, screen } from '@testing-library/react';

import TaskStatus from './TaskStatus';

jest.mock('@umijs/max', () => ({
  useIntl: () => ({
    formatMessage: ({ id }: { id: string }) =>
      ({
        'pages.batchLinkUp.status.idle': '未运行',
        'pages.batchLinkUp.status.created': '已创建',
        'pages.batchLinkUp.status.submitted': '提交中',
        'pages.batchLinkUp.status.queued': '排队中',
        'pages.batchLinkUp.status.running': '运行中',
        'pages.batchLinkUp.status.succeeded': '已完成',
        'pages.batchLinkUp.status.failed': '失败',
        'pages.batchLinkUp.status.paused': '已暂停',
        'pages.batchLinkUp.status.canceled': '已取消',
        'pages.batchLinkUp.status.lost': '状态丢失',
        'pages.batchLinkUp.status.copyError': '复制错误',
        'pages.batchLinkUp.status.copyErrorSuccess': '错误信息已复制',
        'pages.batchLinkUp.status.copyErrorFailed': '复制失败，请手动复制',
      } as Record<string, string>)[id] || id,
  }),
}));

describe('offline sync TaskStatus', () => {
  it.each([
    ['CREATED', 'pending', '已创建', 'false'],
    ['SUBMITTED', 'pending', '提交中', 'true'],
    ['QUEUED', 'pending', '排队中', 'true'],
    ['RUNNING', 'running', '运行中', 'true'],
    ['SUCCEEDED', 'success', '已完成', 'false'],
    ['FAILED', 'failed', '失败', 'false'],
    ['PAUSED', 'paused', '已暂停', 'false'],
    ['CANCELED', 'canceled', '已取消', 'false'],
    ['LOST', 'warning', '状态丢失', 'false'],
  ])(
    'maps %s to the %s Yak status',
    (businessStatus, yakStatus, label, animated) => {
      const { container } = render(<TaskStatus status={businessStatus} />);

      expect(screen.getByText(label)).toBeInTheDocument();
      expect(container.querySelector('svg')).toHaveAttribute(
        'data-status',
        yakStatus,
      );
      expect(container.querySelector('svg')).toHaveAttribute(
        'data-animated',
        animated,
      );
    },
  );

  it.each([
    ['COMPLETED', 'success', '已完成'],
    ['SUCCESS', 'success', '已完成'],
    ['WAITING', 'pending', '排队中'],
    ['CANCELLED', 'canceled', '已取消'],
    ['STOPPED', 'canceled', '已取消'],
    ['NOT_STARTED', 'pending', '未运行'],
  ])('normalizes legacy status %s', (businessStatus, yakStatus, label) => {
    const { container } = render(<TaskStatus status={businessStatus} />);

    expect(screen.getByText(label)).toBeInTheDocument();
    expect(container.querySelector('svg')).toHaveAttribute(
      'data-status',
      yakStatus,
    );
  });

  it('uses a static pending state when the task has not run yet', () => {
    const { container } = render(<TaskStatus />);

    expect(screen.getByText('未运行')).toBeInTheDocument();
    expect(container.querySelector('svg')).toHaveAttribute(
      'data-status',
      'pending',
    );
    expect(container.querySelector('svg')).toHaveAttribute(
      'data-animated',
      'false',
    );
  });

  it('falls back to the unknown Yak status without hiding the backend value', () => {
    const { container } = render(<TaskStatus status="CUSTOM_STATE" />);

    expect(screen.getByText('CUSTOM_STATE')).toBeInTheDocument();
    expect(container.querySelector('svg')).toHaveAttribute(
      'data-status',
      'unknown',
    );
  });
});
