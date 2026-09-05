import {
  listAllDataSources,
  type DataSourceRecord,
} from '@/services/data-source';
import { useIntl } from '@umijs/max';
import { message } from 'antd';
import {
  useCallback,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from 'react';

import {
  QUALITY_SOURCE_TREE_MIN_WIDTH,
  QUALITY_SOURCE_TREE_WIDTH_STORAGE_KEY,
} from '../constants';
import type {
  QualityDataSourceNode,
  QualityDataSourceTreeKey,
} from '../types';
import {
  buildQualityDataSourceNodes,
  clampQualitySourceTreeWidth,
  parseQualitySourceTreeWidth,
} from '../utils';

const initialTreeWidth = () => {
  if (typeof window === 'undefined') {
    return parseQualitySourceTreeWidth();
  }
  return parseQualitySourceTreeWidth(
    window.localStorage.getItem(QUALITY_SOURCE_TREE_WIDTH_STORAGE_KEY),
  );
};

export const useDataSourceTree = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const requestSequenceRef = useRef(0);
  const dragRef = useRef<{ x: number; width: number }>();
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [sourceNodes, setSourceNodes] = useState<QualityDataSourceNode[]>([]);
  const [selectedNodeKey, setSelectedNodeKey] =
    useState<QualityDataSourceTreeKey>();
  const [dataSourceId, setDataSourceId] = useState<number>();
  const [treeLoading, setTreeLoading] = useState(false);
  const [leftWidth, setLeftWidth] = useState(initialTreeWidth);
  const [collapsed, setCollapsed] = useState(false);

  const selectedDataSource = useMemo(
    () => dataSources.find((item) => Number(item.id) === dataSourceId),
    [dataSourceId, dataSources],
  );

  const selectedSourceNode = useMemo(
    () => sourceNodes.find((node) => node.key === selectedNodeKey),
    [selectedNodeKey, sourceNodes],
  );

  const loadSourceTree = useCallback(async (preferredKey?: string) => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setTreeLoading(true);

    try {
      const result = await listAllDataSources();
      if (requestSequence !== requestSequenceRef.current) return undefined;

      const records = result?.bizData || [];
      const nodes = buildQualityDataSourceNodes(records);
      setDataSources(records);
      setSourceNodes(nodes);

      const selected =
        nodes.find((node) => node.key === preferredKey) || nodes[0];
      setSelectedNodeKey(selected?.key);
      setDataSourceId(selected?.dataSourceId);
      return selected;
    } catch (error) {
      if (requestSequence === requestSequenceRef.current) {
        setDataSources([]);
        setSourceNodes([]);
        setSelectedNodeKey(undefined);
        setDataSourceId(undefined);
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({
                id: 'pages.dataQuality.tableConfig.message.sourceLoadFailed',
              }),
        );
      }
      return undefined;
    } finally {
      if (requestSequence === requestSequenceRef.current) {
        setTreeLoading(false);
      }
    }
  }, []);

  const selectNode = useCallback(
    (key: string) => {
      const node = sourceNodes.find((item) => item.key === key);
      if (!node) return undefined;
      setSelectedNodeKey(node.key);
      setDataSourceId(node.dataSourceId);
      return node;
    },
    [sourceNodes],
  );

  const startResize = useCallback(
    (event: ReactPointerEvent<HTMLDivElement>) => {
      if (event.button !== 0) return;
      event.preventDefault();

      const initialWidth = collapsed
        ? QUALITY_SOURCE_TREE_MIN_WIDTH
        : leftWidth;
      if (collapsed) setCollapsed(false);
      dragRef.current = { x: event.clientX, width: initialWidth };

      const move = (current: PointerEvent) => {
        const drag = dragRef.current;
        if (!drag) return;
        setLeftWidth(
          clampQualitySourceTreeWidth(drag.width + current.clientX - drag.x),
        );
      };

      const end = (current: PointerEvent) => {
        const drag = dragRef.current;
        if (drag) {
          const width = clampQualitySourceTreeWidth(
            drag.width + current.clientX - drag.x,
          );
          setLeftWidth(width);
          window.localStorage.setItem(
            QUALITY_SOURCE_TREE_WIDTH_STORAGE_KEY,
            String(width),
          );
        }
        dragRef.current = undefined;
        window.removeEventListener('pointermove', move);
        window.removeEventListener('pointerup', end);
        window.removeEventListener('pointercancel', end);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      };

      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
      window.addEventListener('pointermove', move);
      window.addEventListener('pointerup', end);
      window.addEventListener('pointercancel', end);
    },
    [collapsed, leftWidth],
  );

  return {
    dataSourceId,
    selectedDataSource,
    selectedSourceNode,
    selectedNodeKey,
    sourceNodes,
    treeLoading,
    leftWidth,
    collapsed,
    setCollapsed,
    loadSourceTree,
    selectNode,
    startResize,
  };
};
