import {
  createDevelopmentDirectory,
  createDevelopmentNode,
  deleteDevelopmentDirectory,
  deleteDevelopmentNode,
  listDevelopmentDirectories,
  listDevelopmentNodes,
  moveDevelopmentDirectory,
  moveDevelopmentNode,
  renameDevelopmentDirectory,
  renameDevelopmentNode,
  type DevelopmentDirectory,
  type DevelopmentId,
  type DevelopmentNodeType,
  type DevelopmentResourceNode,
} from '@/services/data-development';
import { useIntl } from '@umijs/max';
import { message } from 'antd';
import type { Key, PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { DATA_DEVELOPMENT_TREE_WIDTH_STORAGE_KEY } from '../constants';
import type {
  DevelopmentTreeAction,
  DevelopmentTreeNode,
  DevelopmentTreeNodeKey,
} from '../types';
import {
  buildDevelopmentTreeData,
  clampDevelopmentTreeWidth,
  copyDevelopmentText,
  developmentDirectoryKey,
  developmentIdFromTreeKey,
  developmentNodeKey,
  developmentNodeTypeForAction,
  filterDevelopmentTreeData,
  parseDevelopmentTreeWidth,
} from '../utils';

const initialTreeWidth = () => {
  if (typeof window === 'undefined') return parseDevelopmentTreeWidth(null);
  return parseDevelopmentTreeWidth(
    window.localStorage.getItem(DATA_DEVELOPMENT_TREE_WIDTH_STORAGE_KEY),
  );
};

export const useDataDevelopmentPage = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const requestSequenceRef = useRef(0);
  const [directories, setDirectories] = useState<DevelopmentDirectory[]>([]);
  const [nodes, setNodes] = useState<DevelopmentResourceNode[]>([]);
  const [treeLoading, setTreeLoading] = useState(false);
  const [treeKeyword, setTreeKeyword] = useState('');
  const [selectedNodeKey, setSelectedNodeKey] =
    useState<DevelopmentTreeNodeKey>();
  const [treeWidth, setTreeWidth] = useState(initialTreeWidth);
  const [treeCollapsed, setTreeCollapsed] = useState(false);

  const [createNodeOpen, setCreateNodeOpen] = useState(false);
  const [createNodeType, setCreateNodeType] =
    useState<DevelopmentNodeType>('SQL');
  const [nodeSaving, setNodeSaving] = useState(false);
  const [createDirectoryOpen, setCreateDirectoryOpen] = useState(false);
  const [directorySaving, setDirectorySaving] = useState(false);
  const [renameTarget, setRenameTarget] = useState<DevelopmentTreeNode>();
  const [renameSaving, setRenameSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<DevelopmentTreeNode>();
  const [deleteSaving, setDeleteSaving] = useState(false);
  const [moveTarget, setMoveTarget] = useState<DevelopmentTreeNode>();
  const [moveSaving, setMoveSaving] = useState(false);

  const text = useCallback(
    (id: string, values?: Record<string, string | number>) =>
      intlRef.current.formatMessage({ id }, values),
    [],
  );

  const loadTree = useCallback(async () => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setTreeLoading(true);

    try {
      const [nextDirectories, nextNodes] = await Promise.all([
        listDevelopmentDirectories(),
        listDevelopmentNodes(),
      ]);
      if (requestSequence !== requestSequenceRef.current) return;
      setDirectories(nextDirectories || []);
      setNodes(nextNodes || []);
    } catch (error) {
      if (requestSequence !== requestSequenceRef.current) return;
      message.error(
        error instanceof Error
          ? error.message
          : text('pages.dataDevelopment.workspace.treeLoadFailed'),
      );
      setDirectories([]);
      setNodes([]);
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setTreeLoading(false);
      }
    }
  }, [text]);

  useEffect(() => {
    void loadTree();
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadTree]);

  const nodeMap = useMemo(
    () => new Map(nodes.map((node) => [node.id, node])),
    [nodes],
  );
  const selectedResourceNodeId = useMemo(
    () => developmentIdFromTreeKey(selectedNodeKey, 'node:'),
    [selectedNodeKey],
  );
  const directoryIdForSelection = useMemo(() => {
    const selectedDirectoryId = developmentIdFromTreeKey(
      selectedNodeKey,
      'directory:',
    );
    if (selectedDirectoryId) return selectedDirectoryId;
    if (!selectedResourceNodeId) return undefined;
    return nodeMap.get(selectedResourceNodeId)?.directoryId || undefined;
  }, [nodeMap, selectedNodeKey, selectedResourceNodeId]);

  const fullTreeData = useMemo(
    () => buildDevelopmentTreeData(directories, nodes),
    [directories, nodes],
  );
  const treeData = useMemo(
    () => filterDevelopmentTreeData(fullTreeData, treeKeyword),
    [fullTreeData, treeKeyword],
  );

  const handleResizeStart = useCallback(
    (event: ReactPointerEvent) => {
      if (treeCollapsed) return;
      event.preventDefault();
      const startX = event.clientX;
      const startWidth = treeWidth;
      const previousCursor = document.body.style.cursor;
      const previousUserSelect = document.body.style.userSelect;
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';

      const handlePointerMove = (moveEvent: PointerEvent) => {
        setTreeWidth(
          clampDevelopmentTreeWidth(
            startWidth + moveEvent.clientX - startX,
          ),
        );
      };
      const finish = (upEvent: PointerEvent) => {
        const width = clampDevelopmentTreeWidth(
          startWidth + upEvent.clientX - startX,
        );
        setTreeWidth(width);
        window.localStorage.setItem(
          DATA_DEVELOPMENT_TREE_WIDTH_STORAGE_KEY,
          String(width),
        );
        document.body.style.cursor = previousCursor;
        document.body.style.userSelect = previousUserSelect;
        window.removeEventListener('pointermove', handlePointerMove);
        window.removeEventListener('pointerup', finish);
        window.removeEventListener('pointercancel', finish);
      };

      window.addEventListener('pointermove', handlePointerMove);
      window.addEventListener('pointerup', finish);
      window.addEventListener('pointercancel', finish);
    },
    [treeCollapsed, treeWidth],
  );

  const openCreateNode = useCallback((type: DevelopmentNodeType) => {
    setCreateNodeType(type);
    setCreateNodeOpen(true);
  }, []);

  const closeCreateNode = useCallback(() => {
    if (!nodeSaving) setCreateNodeOpen(false);
  }, [nodeSaving]);

  const openCreateDirectory = useCallback(() => {
    setCreateDirectoryOpen(true);
  }, []);

  const closeCreateDirectory = useCallback(() => {
    if (!directorySaving) setCreateDirectoryOpen(false);
  }, [directorySaving]);

  const submitDirectory = useCallback(
    async (parentId: DevelopmentId | undefined, name: string) => {
      setDirectorySaving(true);
      try {
        const created = await createDevelopmentDirectory({ parentId, name });
        setCreateDirectoryOpen(false);
        setTreeKeyword('');
        await loadTree();
        setSelectedNodeKey(developmentDirectoryKey(created.id));
        message.success(text('pages.dataDevelopment.workspace.directoryCreated'));
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : text('pages.dataDevelopment.workspace.directoryCreateFailed'),
        );
      } finally {
        setDirectorySaving(false);
      }
    },
    [loadTree, text],
  );

  const submitNode = useCallback(
    async (
      type: DevelopmentNodeType,
      directoryId: DevelopmentId | undefined,
      name: string,
    ) => {
      setNodeSaving(true);
      try {
        const created = await createDevelopmentNode({
          name,
          type,
          directoryId,
        });
        setCreateNodeOpen(false);
        setTreeKeyword('');
        await loadTree();
        setSelectedNodeKey(developmentNodeKey(created.id));
        message.success(text('pages.dataDevelopment.workspace.nodeCreated'));
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : text('pages.dataDevelopment.workspace.nodeCreateFailed'),
        );
      } finally {
        setNodeSaving(false);
      }
    },
    [loadTree, text],
  );

  const copyResourceText = useCallback(
    async (value: string, successMessageId: string) => {
      try {
        await copyDevelopmentText(value);
        message.success(text(successMessageId));
      } catch {
        message.error(text('pages.dataDevelopment.workspace.copyFailed'));
      }
    },
    [text],
  );

  const handleResourceAction = useCallback(
    (action: DevelopmentTreeAction, resource: DevelopmentTreeNode) => {
      setSelectedNodeKey(resource.key);
      if (action === 'create-directory') {
        setCreateDirectoryOpen(true);
        return;
      }

      const type = developmentNodeTypeForAction(action);
      if (type) {
        openCreateNode(type);
        return;
      }
      if (action === 'copy-name') {
        void copyResourceText(
          resource.title,
          'pages.dataDevelopment.workspace.nameCopied',
        );
        return;
      }
      if (action === 'copy-path') {
        void copyResourceText(
          resource.resourcePath,
          'pages.dataDevelopment.workspace.pathCopied',
        );
        return;
      }
      if (action === 'rename') {
        setRenameTarget(resource);
        return;
      }
      if (action === 'move') {
        setMoveTarget(resource);
        return;
      }
      if (action === 'delete') setDeleteTarget(resource);
    },
    [copyResourceText, openCreateNode],
  );

  const submitRename = useCallback(
    async (name: string) => {
      if (!renameTarget) return;
      setRenameSaving(true);
      try {
        if (renameTarget.nodeType === 'directory') {
          await renameDevelopmentDirectory(renameTarget.resourceId, name);
        } else {
          await renameDevelopmentNode(renameTarget.resourceId, name);
        }
        setRenameTarget(undefined);
        setTreeKeyword('');
        await loadTree();
        message.success(text('pages.dataDevelopment.workspace.renamed'));
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : text('pages.dataDevelopment.workspace.renameFailed'),
        );
      } finally {
        setRenameSaving(false);
      }
    },
    [loadTree, renameTarget, text],
  );

  const closeRename = useCallback(() => {
    if (!renameSaving) setRenameTarget(undefined);
  }, [renameSaving]);

  const submitDelete = useCallback(async () => {
    if (!deleteTarget) return;
    setDeleteSaving(true);
    try {
      if (deleteTarget.nodeType === 'directory') {
        await deleteDevelopmentDirectory(deleteTarget.resourceId);
      } else {
        await deleteDevelopmentNode(deleteTarget.resourceId);
      }
      setDeleteTarget(undefined);
      setSelectedNodeKey(undefined);
      setTreeKeyword('');
      await loadTree();
      message.success(text('pages.dataDevelopment.workspace.deleted'));
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : text('pages.dataDevelopment.workspace.deleteFailed'),
      );
    } finally {
      setDeleteSaving(false);
    }
  }, [deleteTarget, loadTree, text]);

  const closeDelete = useCallback(() => {
    if (!deleteSaving) setDeleteTarget(undefined);
  }, [deleteSaving]);

  const submitMove = useCallback(
    async (targetDirectoryId: DevelopmentId | null) => {
      if (!moveTarget) return;
      setMoveSaving(true);
      try {
        if (moveTarget.nodeType === 'directory') {
          await moveDevelopmentDirectory(moveTarget.resourceId, targetDirectoryId);
        } else {
          await moveDevelopmentNode(moveTarget.resourceId, targetDirectoryId);
        }
        setMoveTarget(undefined);
        setTreeKeyword('');
        await loadTree();
        message.success(text('pages.dataDevelopment.workspace.moved'));
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : text('pages.dataDevelopment.workspace.moveFailed'),
        );
      } finally {
        setMoveSaving(false);
      }
    },
    [loadTree, moveTarget, text],
  );

  const closeMove = useCallback(() => {
    if (!moveSaving) setMoveTarget(undefined);
  }, [moveSaving]);

  const selectTreeNodes = useCallback((keys: Key[]) => {
    const key = keys[0];
    setSelectedNodeKey(
      key ? (String(key) as DevelopmentTreeNodeKey) : undefined,
    );
  }, []);

  const focusNode = useCallback((nodeId?: DevelopmentId) => {
    setSelectedNodeKey(nodeId ? developmentNodeKey(nodeId) : undefined);
  }, []);

  return {
    directories,
    nodes,
    treeData,
    treeLoading,
    treeKeyword,
    selectedNodeKey,
    selectedResourceNodeId,
    directoryIdForSelection,
    treeWidth,
    treeCollapsed,
    createNodeOpen,
    createNodeType,
    nodeSaving,
    createDirectoryOpen,
    directorySaving,
    renameTarget,
    renameSaving,
    deleteTarget,
    deleteSaving,
    moveTarget,
    moveSaving,
    setTreeKeyword,
    setTreeCollapsed,
    openCreateNode,
    closeCreateNode,
    openCreateDirectory,
    closeCreateDirectory,
    handleResizeStart,
    handleResourceAction,
    selectTreeNodes,
    focusNode,
    submitDirectory,
    submitNode,
    submitRename,
    closeRename,
    submitDelete,
    closeDelete,
    submitMove,
    closeMove,
    loadTree,
  };
};
