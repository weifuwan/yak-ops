import { YakButton } from '@/components/ui';
import { useIntl } from '@umijs/max';
import { Modal, Tree } from 'antd';
import { FolderInput } from 'lucide-react';
import { useMemo, useState } from 'react';

import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentTreeNode,
} from '../types';

interface MoveResourceModalProps {
  open: boolean;
  resourceLabel: string;
  resourceName: string;
  directories: DevelopmentDirectory[];
  resourceId: DevelopmentId;
  resourceType: 'node' | 'directory';
  loading: boolean;
  onCancel: () => void;
  onConfirm: (targetDirectoryId: DevelopmentId | null) => void;
}

const collectDescendantIds = (
  directories: DevelopmentDirectory[],
  rootId: DevelopmentId,
): Set<DevelopmentId> => {
  const childMap = new Map<DevelopmentId | null, DevelopmentId[]>();
  for (const d of directories) {
    const pid = d.parentId || undefined;
    const list = childMap.get(pid as DevelopmentId | null) || [];
    list.push(d.id);
    childMap.set(d.parentId, list);
  }
  const excluded = new Set<DevelopmentId>([rootId]);
  const queue = [rootId];
  while (queue.length) {
    const current = queue.pop()!;
    const kids = childMap.get(current) || childMap.get(null) || [];
    for (const kidId of kids) {
      if (!excluded.has(kidId)) {
        excluded.add(kidId);
        queue.push(kidId);
      }
    }
  }
  return excluded;
};

const buildDirectoryTreeData = (
  directories: DevelopmentDirectory[],
  locale: string,
  excludeId?: DevelopmentId,
): DevelopmentTreeNode[] => {
  const excludedIds = excludeId ? collectDescendantIds(directories, excludeId) : undefined;
  const normalizeParentId = (id: DevelopmentId | null | undefined) => id || undefined;

  const childrenOf = (parentId?: DevelopmentId | null): DevelopmentTreeNode[] =>
    directories
      .filter((d) => normalizeParentId(d.parentId) === normalizeParentId(parentId))
      .filter((d) => !excludedIds || !excludedIds.has(d.id))
      .sort((a, b) => a.name.localeCompare(b.name, locale))
      .map((d) => ({
        key: `directory:${d.id}`,
        title: d.name,
        nodeType: 'directory' as const,
        resourceId: d.id,
        resourcePath: d.path || '',
        children: childrenOf(d.id),
      }));

  return childrenOf();
};

const MoveResourceModal = ({
  open,
  resourceLabel,
  resourceName,
  directories,
  resourceId,
  resourceType,
  loading,
  onCancel,
  onConfirm,
}: MoveResourceModalProps) => {
  const intl = useIntl();
  const [selectedKey, setSelectedKey] = useState<DevelopmentId | null>(null);

  const treeData = useMemo(
    () =>
      buildDirectoryTreeData(
        directories,
        intl.locale,
        resourceType === 'directory' ? resourceId : undefined,
      ),
    [directories, intl.locale, resourceId, resourceType],
  );

  const handleSelect = (keys: React.Key[]) => {
    const key = keys[0] as string | undefined;
    if (!key) {
      setSelectedKey(null);
      return;
    }
    if (key.startsWith('directory:')) {
      setSelectedKey(key.substring('directory:'.length));
    } else {
      setSelectedKey(null);
    }
  };

  const handleReset = () => setSelectedKey(null);

  return (
    <Modal
      title={
        <div className="flex items-center gap-2">
          <FolderInput size={16} className="text-[#667085]" />
          <span>
            {intl.formatMessage(
              { id: 'pages.dataDevelopment.modal.move.title' },
              { resource: resourceLabel },
            )}
          </span>
        </div>
      }
      open={open}
      onCancel={() => {
        handleReset();
        onCancel();
      }}
      afterClose={handleReset}
      width={440}
      footer={
        <div className="flex items-center justify-end gap-2">
          <YakButton
            type="default"
            onClick={() => {
              handleReset();
              onCancel();
            }}
          >
            {intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' })}
          </YakButton>
          <YakButton type="primary" loading={loading} onClick={() => onConfirm(selectedKey)}>
            {intl.formatMessage({ id: 'pages.dataDevelopment.modal.move.here' })}
          </YakButton>
        </div>
      }
    >
      <div className="mb-3 text-[13px] text-[#344054]">
        {intl.formatMessage(
          { id: 'pages.dataDevelopment.modal.move.description' },
          { name: resourceName },
        )}
      </div>

      <div className="mb-3">
        <YakButton
          type="text"
          className={`!h-8 w-full !justify-start !rounded !px-3 text-[12px] ${
            selectedKey === null
              ? '!bg-[#eff8ff] !text-[#1570ef] !font-medium'
              : '!text-[#344054] hover:!bg-[#f5f5f6]'
          }`}
          onClick={() => setSelectedKey(null)}
        >
          <span className="mr-1.5 text-[#98a2b3]">/</span>
          {intl.formatMessage({ id: 'pages.dataDevelopment.common.root' })}
        </YakButton>
      </div>

      {treeData.length > 0 ? (
        <div className="max-h-[280px] overflow-auto rounded border border-[#e4e7ec]">
          <Tree
            blockNode
            defaultExpandAll
            selectedKeys={selectedKey ? [`directory:${selectedKey}`] : []}
            treeData={treeData}
            onSelect={handleSelect}
            className="move-directory-tree"
          />
        </div>
      ) : (
        <div className="py-6 text-center text-[12px] text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.dataDevelopment.modal.move.empty' })}
        </div>
      )}

      <style>{`
        .move-directory-tree.ant-tree { color: #344054; }
        .move-directory-tree .ant-tree-treenode {
          box-sizing: border-box; width: 100%; min-height: 30px; padding: 0 6px !important;
          align-items: center; border-radius: 0; transition: background-color 0.15s ease;
        }
        .move-directory-tree .ant-tree-treenode:hover { background: #f5f5f5; }
        .move-directory-tree .ant-tree-treenode:has(.ant-tree-node-selected) { background: #eff8ff; }
        .move-directory-tree .ant-tree-node-content-wrapper {
          display: flex; min-width: 0; height: 30px; flex: 1; align-items: center;
          padding: 0 !important; border-radius: 0 !important; background: transparent !important;
        }
        .move-directory-tree .ant-tree-title { display: flex; min-width: 0; flex: 1; }
        .move-directory-tree .ant-tree-switcher {
          width: 18px; min-width: 18px; height: 30px; line-height: 30px; color: #98a2b3;
        }
      `}</style>
    </Modal>
  );
};

export default MoveResourceModal;
