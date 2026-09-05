import { YakButton, YakEmpty } from '@/components/ui';
import { useIntl } from '@umijs/max';
import type { MenuProps, TreeProps } from 'antd';
import { Dropdown, Input, Spin, Tooltip, Tree } from 'antd';
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Code2,
  Copy,
  Database,
  FileText,
  FolderInput,
  FolderPlus,
  Network,
  Pencil,
  Plus,
  Search,
  TerminalSquare,
  Trash2,
  Upload,
} from 'lucide-react';
import type { PointerEvent as ReactPointerEvent, ReactNode } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';

import JavaIcon from '../icon/JavaIcon';
import PythonIcon from '../icon/PythonIcon';
import type {
  DevelopmentNodeCreateType,
  DevelopmentTreeAction,
  DevelopmentTreeNode,
} from '../types';

export type {
  DevelopmentNodeCreateType,
  DevelopmentTreeAction,
  DevelopmentTreeNode,
  DevelopmentTreeNodeType,
} from '../types';

interface DevelopmentTreePaneProps {
  treeData: DevelopmentTreeNode[];
  treeLoading: boolean;
  selectedNodeKey?: string;
  searchValue: string;
  leftWidth: number;
  collapsed: boolean;
  onCreateDirectory: () => void;
  onCreateNode: (type: DevelopmentNodeCreateType) => void;
  onResourceAction: (
    action: DevelopmentTreeAction,
    node: DevelopmentTreeNode,
  ) => void;
  onSearchChange: (value: string) => void;
  onSelect: TreeProps['onSelect'];
  onResizeStart: (event: ReactPointerEvent) => void;
  onCollapsedChange: (collapsed: boolean) => void;
}

const nodeTypeIconClassName = (taskType?: string) => {
  if (taskType === 'SHELL') return 'text-[#6172f3]';
  if (taskType === 'SQL') return 'text-[#f79009]';
  if (taskType === 'DATASET') return 'text-[#667085]';
  if (taskType === 'DATA_SERVICE') return 'text-[#475467]';
  return 'text-[#667085]';
};

const nodeIcon = (taskType?: string): ReactNode => {
  const className = `shrink-0 ${nodeTypeIconClassName(taskType)}`;
  if (taskType === 'SHELL') {
    return <TerminalSquare size={13} strokeWidth={1.8} className={className} />;
  }
  if (taskType === 'PYTHON') return <PythonIcon size={13} />;
  if (taskType === 'JAVA') return <JavaIcon size={13} />;
  if (taskType === 'DATASET') {
    return <Database size={13} strokeWidth={1.8} className={className} />;
  }
  if (taskType === 'DATA_SERVICE') {
    return <Network size={13} strokeWidth={1.8} className={className} />;
  }
  return <Code2 size={13} strokeWidth={1.8} className={className} />;
};

const DevelopmentFolderIcon = ({ expanded }: { expanded: boolean }) => (
  <svg
    aria-hidden="true"
    className="shrink-0"
    width="18"
    height="18"
    viewBox="0 0 20 20"
    fill="none"
  >
    {expanded ? (
      <>
        <path
          d="M2.8 6.05V5.6c0-.66.54-1.2 1.2-1.2h4.15l1.55 1.75h6.3c.66 0 1.2.54 1.2 1.2v5.7H3.95c-.64 0-1.15-.52-1.15-1.15V6.05Z"
          fill="#FFF8DE"
          stroke="#F5A000"
          strokeWidth="1.25"
          strokeLinejoin="round"
        />
        <path
          d="M4.55 8.25h12.38c.73 0 1.2.78.85 1.42l-2.6 4.78c-.2.38-.6.62-1.03.62H3.27c-.74 0-1.21-.8-.84-1.44l2.12-4.76c.2-.38.59-.62 1.02-.62Z"
          fill="#FFF1B8"
          stroke="#F5A000"
          strokeWidth="1.25"
          strokeLinejoin="round"
        />
      </>
    ) : (
      <>
        <path
          d="M2.8 5.55c0-.66.54-1.2 1.2-1.2h4.15L9.7 6.1H16c.66 0 1.2.54 1.2 1.2v7.15c0 .66-.54 1.2-1.2 1.2H4c-.66 0-1.2-.54-1.2-1.2v-8.9Z"
          fill="#FFF8DE"
          stroke="#F5A000"
          strokeWidth="1.25"
          strokeLinejoin="round"
        />
        <path
          d="M3.15 7.55h13.7"
          stroke="#F5A000"
          strokeWidth="1.05"
          strokeLinecap="round"
          opacity="0.72"
        />
      </>
    )}
  </svg>
);

const collectExpandedDirectoryKeys = (
  nodes: DevelopmentTreeNode[],
): string[] =>
  nodes.flatMap((node) => {
    const childKeys = node.children?.length
      ? collectExpandedDirectoryKeys(node.children)
      : [];
    return node.nodeType === 'directory' && node.children?.length
      ? [node.key, ...childKeys]
      : childKeys;
  });

const createTypeForMenuKey = (
  key: string,
): DevelopmentNodeCreateType | undefined => {
  if (key === 'node-sql' || key === 'create-sql') return 'SQL';
  if (key === 'node-shell' || key === 'create-shell') return 'SHELL';
  if (key === 'node-python' || key === 'create-python') return 'PYTHON';
  if (key === 'node-java' || key === 'create-java') return 'JAVA';
  if (key === 'node-dataset' || key === 'create-dataset') return 'DATASET';
  if (key === 'node-data-service' || key === 'create-data-service') return 'DATA_SERVICE';
  return undefined;
};

const padTimePart = (value: number) => String(value).padStart(2, '0');
const formatUpdateTime = (value?: string) => {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return [
    `${date.getFullYear()}-${padTimePart(date.getMonth() + 1)}-${padTimePart(date.getDate())}`,
    `${padTimePart(date.getHours())}:${padTimePart(date.getMinutes())}:${padTimePart(date.getSeconds())}`,
  ].join(' ');
};

const DevelopmentTreePane = ({
  treeData,
  treeLoading,
  selectedNodeKey,
  searchValue,
  leftWidth,
  collapsed,
  onCreateDirectory,
  onCreateNode,
  onResourceAction,
  onSearchChange,
  onSelect,
  onResizeStart,
  onCollapsedChange,
}: DevelopmentTreePaneProps) => {
  const intl = useIntl();
  const [expandedDirectoryKeys, setExpandedDirectoryKeys] = useState<string[]>([]);
  const expansionInitializedRef = useRef(false);

  useEffect(() => {
    if (!treeData.length) {
      expansionInitializedRef.current = false;
      setExpandedDirectoryKeys([]);
      return;
    }
    if (expansionInitializedRef.current) return;
    expansionInitializedRef.current = true;
    setExpandedDirectoryKeys(collectExpandedDirectoryKeys(treeData));
  }, [treeData]);

  const nodeCreateItems = useMemo<NonNullable<MenuProps['items']>>(
    () => [
      {
        key: 'node-sql',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.sqlNode' }),
        icon: <Code2 size={14} strokeWidth={1.8} className="text-[#f79009]" />,
      },
      {
        key: 'node-shell',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.shellNode' }),
        icon: <TerminalSquare size={14} strokeWidth={1.8} className="text-[#6172f3]" />,
      },
      {
        key: 'node-python',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.pythonNode' }),
        icon: <PythonIcon size={14} />,
      },
      {
        key: 'node-java',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.javaNode' }),
        icon: <JavaIcon size={14} />,
      },
      { type: 'divider' },
      {
        key: 'node-dataset',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.datasetNode' }),
        icon: <Database size={14} strokeWidth={1.8} className="text-[#667085]" />,
      },
      {
        key: 'node-data-service',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.dataServiceNode' }),
        icon: <Network size={14} strokeWidth={1.8} className="text-[#475467]" />,
      },
    ],
    [intl],
  );

  const createMenuItems: MenuProps['items'] = [
    {
      key: 'node',
      label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.createNode' }),
      icon: <Code2 size={14} strokeWidth={1.8} />,
      children: nodeCreateItems,
    },
    {
      key: 'directory',
      label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.createDirectory' }),
      icon: <FolderPlus size={14} strokeWidth={1.8} />,
    },
  ];

  const contextMenuItems = (node: DevelopmentTreeNode): MenuProps['items'] => {
    const commonItems: MenuProps['items'] = [
      {
        key: 'copy-name',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.copyName' }),
        icon: <Copy size={14} strokeWidth={1.8} />,
      },
      {
        key: 'copy-path',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.copyPath' }),
        icon: <FileText size={14} strokeWidth={1.8} />,
      },
      { type: 'divider' },
      {
        key: 'rename',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.rename' }),
        icon: <Pencil size={14} strokeWidth={1.8} />,
      },
      {
        key: 'move',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.move' }),
        icon: <FolderInput size={14} strokeWidth={1.8} />,
      },
      {
        key: 'delete',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.common.delete' }),
        icon: <Trash2 size={14} strokeWidth={1.8} />,
        danger: true,
      },
    ];

    if (node.nodeType !== 'directory') return commonItems;
    return [
      {
        key: 'create-node',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.createNode' }),
        icon: <Code2 size={14} strokeWidth={1.8} />,
        children: nodeCreateItems.map((item) => {
          if (!item || item.type === 'divider') return item;
          return { ...item, key: String(item.key).replace('node-', 'create-') };
        }),
      },
      {
        key: 'create-directory',
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.createDirectory' }),
        icon: <FolderPlus size={14} strokeWidth={1.8} />,
      },
      { type: 'divider' },
      ...commonItems,
    ];
  };

  const renderTitle: TreeProps['titleRender'] = (rawNode) => {
    const node = rawNode as DevelopmentTreeNode;
    const isNode = node.nodeType === 'node';
    const updateTime = formatUpdateTime(node.updateTime);
    const modifier = node.updatedBy && node.updatedBy !== 'unknown' ? node.updatedBy : '';
    const updateMeta = [
      modifier
        ? intl.formatMessage(
            { id: 'pages.dataDevelopment.workspace.modifiedBy' },
            { name: modifier },
          )
        : '',
      updateTime,
    ]
      .filter(Boolean)
      .join(' ');

    return (
      <Dropdown
        trigger={['contextMenu']}
        menu={{
          items: contextMenuItems(node),
          triggerSubMenuAction: 'hover',
          subMenuOpenDelay: 0.05,
          subMenuCloseDelay: 0.1,
          onClick: ({ key }) => onResourceAction(key as DevelopmentTreeAction, node),
        }}
      >
        <div
          className="flex min-w-0 flex-1 items-center gap-1.5"
          title={[node.title, updateMeta].filter(Boolean).join('  ')}
        >
          {isNode && node.pendingPublish ? (
            <Tooltip title={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.pendingPublish' })}>
              <span className="inline-flex shrink-0 items-center text-[#98a2b3]">
                <Upload size={12} strokeWidth={1.8} />
              </span>
            </Tooltip>
          ) : null}
          {isNode ? (
            nodeIcon(node.taskType)
          ) : (
            <DevelopmentFolderIcon expanded={expandedDirectoryKeys.includes(node.key)} />
          )}
          <span
            className={[
              'min-w-[36px] truncate text-[13px] leading-8',
              isNode ? 'font-normal text-[#344054]' : 'flex-1 font-medium text-[#1f2937]',
            ].join(' ')}
          >
            {node.title}
          </span>
          {isNode && updateMeta ? (
            <span className="min-w-0 flex-1 truncate text-[11px] text-[#98a2b3]">{updateMeta}</span>
          ) : null}
          {isNode && node.taskType ? (
            <span className="shrink-0 text-[10px] text-[#98a2b3]">{node.taskType}</span>
          ) : null}
        </div>
      </Dropdown>
    );
  };

  return (
    <>
      <aside
        className="group relative shrink-0 overflow-hidden bg-white transition-[width] duration-200 ease-out"
        style={{ width: collapsed ? 0 : leftWidth }}
      >
        <div className="flex h-full flex-col overflow-hidden" style={{ width: leftWidth }}>
          <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e5e7eb] bg-[#f7f7f8] px-3">
            <span className="text-[13px] font-semibold text-[#30323b]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.workspace.catalog' })}
            </span>
            <Dropdown
              trigger={['click']}
              placement="bottomRight"
              menu={{
                items: createMenuItems,
                triggerSubMenuAction: 'hover',
                subMenuOpenDelay: 0.05,
                subMenuCloseDelay: 0.1,
                onClick: ({ key }) => {
                  if (key === 'directory') {
                    onCreateDirectory();
                    return;
                  }
                  const type = createTypeForMenuKey(key);
                  if (type) onCreateNode(type);
                },
              }}
            >
              <Tooltip
                title={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.create' })}
                placement="right"
              >
                <YakButton
                  type="text"
                  size="small"
                  iconOnly
                  aria-label={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.createAria' })}
                  icon={<Plus size={15} strokeWidth={1.8} />}
                  className="!h-7 !w-7 !p-0 hover:!bg-white"
                />
              </Tooltip>
            </Dropdown>
          </div>

          <div className="flex h-9 shrink-0 items-center border-b border-[#e8e9ec] px-2.5">
            <Input
              allowClear
              size="small"
              variant="filled"
              value={searchValue}
              prefix={<Search size={13} className="text-[#98a2b3]" />}
              placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.searchPlaceholder' })}
              onChange={(event) => onSearchChange(event.target.value)}
              className="!h-7"
            />
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto px-3 py-2">
            <Spin spinning={treeLoading} wrapperClassName="block min-h-full">
              {treeData.length ? (
                <Tree
                  blockNode
                  defaultExpandAll
                  autoExpandParent={Boolean(searchValue.trim())}
                  selectedKeys={selectedNodeKey ? [selectedNodeKey] : []}
                  treeData={treeData}
                  titleRender={renderTitle}
                  switcherIcon={<ChevronDown size={12} strokeWidth={1.8} />}
                  onExpand={(keys) => setExpandedDirectoryKeys(keys.map(String))}
                  onSelect={onSelect}
                  className="development-tree bg-transparent"
                />
              ) : (
                <YakEmpty
                  compact
                  title={intl.formatMessage({
                    id: searchValue.trim()
                      ? 'pages.dataDevelopment.workspace.noMatch'
                      : 'pages.dataDevelopment.workspace.empty',
                  })}
                  description={intl.formatMessage({
                    id: searchValue.trim()
                      ? 'pages.dataDevelopment.workspace.tryAnotherKeyword'
                      : 'pages.dataDevelopment.workspace.emptyHint',
                  })}
                />
              )}
            </Spin>
          </div>
        </div>
      </aside>

      <div
        role="separator"
        aria-label={intl.formatMessage({ id: 'pages.dataDevelopment.workspace.resize' })}
        aria-orientation="vertical"
        onPointerDown={collapsed ? undefined : onResizeStart}
        className={[
          'group relative z-20 w-px shrink-0 touch-none',
          collapsed ? 'cursor-default' : 'cursor-col-resize',
        ].join(' ')}
      >
        <div
          className={[
            'absolute inset-y-0 left-1/2 z-10 w-3 -translate-x-1/2',
            collapsed ? 'cursor-default' : 'cursor-col-resize',
          ].join(' ')}
        />
        <div
          className={[
            'pointer-events-none absolute inset-y-0 left-0 w-px bg-[#dfe3e8]',
            'transition-[width,background-color] duration-150',
            !collapsed
              ? 'group-hover:w-[2px] group-hover:bg-[rgba(254,44,85,.55)] group-active:bg-[rgba(254,44,85,1)]'
              : '',
          ].join(' ')}
        />
        <button
          type="button"
          aria-label={intl.formatMessage({
            id: collapsed
              ? 'pages.dataDevelopment.workspace.expand'
              : 'pages.dataDevelopment.workspace.collapse',
          })}
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => onCollapsedChange(!collapsed)}
          className={[
            'absolute left-px top-1/2 z-20 flex h-7 w-3 -translate-y-1/2 items-center justify-center rounded-r-[3px]',
            'border border-l-0 border-[#dfe3e8] bg-white text-[#7b808a] shadow-[0_1px_2px_rgba(16,24,40,0.04)]',
            'opacity-0 transition-[opacity,color,border-color,box-shadow] duration-150 group-hover:opacity-100 focus:opacity-100',
            'hover:border-[#cfd4dc] hover:text-[#344054] focus:outline-none focus-visible:ring-2 focus-visible:ring-[rgba(254,44,85,.16)]',
          ].join(' ')}
        >
          {collapsed ? <ChevronRight size={11} /> : <ChevronLeft size={11} />}
        </button>
      </div>

      <style>{`
        .development-tree.ant-tree { color: #344054; }
        .development-tree .ant-tree-list-holder-inner { gap: 1px; }
        .development-tree .ant-tree-treenode {
          box-sizing: border-box; width: 100%; min-height: 30px; padding: 0 6px !important;
          align-items: center; border-radius: 0; transition: background-color 0.15s ease;
        }
        .development-tree .ant-tree-treenode:hover,
        .development-tree .ant-tree-treenode:has(.ant-tree-node-selected) { background: #f5f5f5; }
        .development-tree .ant-tree-node-content-wrapper {
          display: flex; min-width: 0; height: 30px; flex: 1; align-items: center;
          padding: 0 !important; border-radius: 0 !important; background: transparent !important;
        }
        .development-tree .ant-tree-title { display: flex; min-width: 0; flex: 1; }
        .development-tree .ant-tree-switcher {
          width: 18px; min-width: 18px; height: 30px; line-height: 30px; color: #98a2b3;
        }
      `}</style>
    </>
  );
};

export default DevelopmentTreePane;
