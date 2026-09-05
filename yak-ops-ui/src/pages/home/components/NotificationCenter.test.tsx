import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import {
  markMessageRead,
  notifyMessageCountChanged,
  pageMessages,
} from '@/services/security/messages';

import NotificationCenter from './NotificationCenter';

let mockProjects: Array<{ id: number; projectName: string }> = [
  { id: 7, projectName: 'Project A' },
];
let mockCurrentProject: { id: number; projectName: string } | undefined =
  mockProjects[0];

const mockHistoryPush = jest.fn();

jest.mock('@/contexts/SecurityProjectContext', () => ({
  useSecurityProject: () => ({
    projects: mockProjects,
    currentProject: mockCurrentProject,
  }),
}));

jest.mock('@/services/security/messages', () => ({
  pageMessages: jest.fn(),
  markMessageRead: jest.fn(),
  notifyMessageCountChanged: jest.fn(),
  safeMessageActionPath: jest.fn((value?: string) =>
    value && value.startsWith('/') && !value.startsWith('//') ? value : undefined,
  ),
}));

jest.mock('@umijs/max', () => ({
  history: { push: mockHistoryPush },
  useIntl: () => ({
    locale: 'zh-CN',
    formatMessage: ({ id }: { id: string }) => id,
  }),
}));

describe('home NotificationCenter', () => {
  const mockPageMessages = jest.mocked(pageMessages);
  const mockMarkMessageRead = jest.mocked(markMessageRead);
  const mockNotifyMessageCountChanged = jest.mocked(notifyMessageCountChanged);

  beforeEach(() => {
    mockProjects = [{ id: 7, projectName: 'Project A' }];
    mockCurrentProject = mockProjects[0];
    mockHistoryPush.mockReset();
    mockPageMessages.mockReset();
    mockMarkMessageRead.mockReset();
    mockNotifyMessageCountChanged.mockReset();
    mockMarkMessageRead.mockResolvedValue(undefined);
  });

  it('loads only unread messages for the active workspace and marks them read before navigation', async () => {
    mockPageMessages.mockResolvedValue({
      records: [
        {
          id: 11,
          title: '离线同步失败',
          summary: 'ODS 用户表 → DWD 用户表',
          status: 'UNREAD',
          actionPath: '/batch-link-up',
        },
      ],
      total: 4,
    });

    render(<NotificationCenter />);

    await waitFor(() =>
      expect(mockPageMessages).toHaveBeenCalledWith({
        pageNum: 1,
        pageSize: 3,
        status: 'UNREAD',
        projectId: 7,
      }),
    );

    expect(await screen.findByText('离线同步失败')).toBeTruthy();
    expect(screen.getByText('4')).toBeTruthy();

    fireEvent.click(screen.getByText('离线同步失败'));

    await waitFor(() => expect(mockMarkMessageRead).toHaveBeenCalledWith(11));
    expect(mockNotifyMessageCountChanged).toHaveBeenCalledTimes(1);
    expect(mockHistoryPush).toHaveBeenCalledWith('/batch-link-up');
  });

  it('uses the hardened backend system-only inbox when the user has no workspace', async () => {
    mockProjects = [];
    mockCurrentProject = undefined;
    mockPageMessages.mockResolvedValue({ records: [], total: 0 });

    render(<NotificationCenter />);

    await waitFor(() =>
      expect(mockPageMessages).toHaveBeenCalledWith({
        pageNum: 1,
        pageSize: 3,
        status: 'UNREAD',
        projectId: undefined,
      }),
    );
  });

  it('waits for project selection instead of issuing a transient unscoped request', async () => {
    mockProjects = [{ id: 7, projectName: 'Project A' }];
    mockCurrentProject = undefined;

    render(<NotificationCenter />);

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(mockPageMessages).not.toHaveBeenCalled();
  });
});
