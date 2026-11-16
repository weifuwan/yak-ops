import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import type { IndicatorKind, IndicatorTreeNode } from "../types";
import { collectExpandedKeys, filterTree } from "../utils/indicator-tree";

type UseIndicatorTreeViewOptions = {
  projectCode: string;
  kind: IndicatorKind;
  queryKeyParts: unknown[];
  queryFn: () => Promise<IndicatorTreeNode[]>;
  enabled: boolean;
};

export function useIndicatorTreeView({
  projectCode,
  kind,
  queryKeyParts,
  queryFn,
  enabled,
}: UseIndicatorTreeViewOptions) {
  const [treeKeyword, setTreeKeyword] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [selectedTreeKey, setSelectedTreeKey] = useState("");
  const initializedTreeKeyRef = useRef("");
  const queryKeySuffix = JSON.stringify(queryKeyParts);

  const treeQuery = useQuery<IndicatorTreeNode[]>({
    queryKey: ["modeling", "indicator-tree", projectCode, kind, ...queryKeyParts],
    queryFn,
    enabled: Boolean(projectCode && enabled),
    placeholderData: (previousData) => previousData,
  });

  const visibleTree = useMemo(
    () => filterTree(treeQuery.data || [], treeKeyword),
    [treeQuery.data, treeKeyword],
  );
  const visibleExpandedKeys = useMemo(() => {
    const next = new Set(expandedKeys);
    if (treeKeyword.trim()) collectExpandedKeys(visibleTree).forEach((key) => next.add(key));
    return next;
  }, [expandedKeys, treeKeyword, visibleTree]);

  useEffect(() => {
    if (!treeQuery.data || treeQuery.isPlaceholderData) return;
    const treeIdentity = [projectCode, kind, queryKeySuffix].join(":");
    if (initializedTreeKeyRef.current === treeIdentity) return;

    const root = treeQuery.data.find((node) => node.nodeType === "root");
    setExpandedKeys(new Set(root ? [root.key] : []));
    initializedTreeKeyRef.current = treeIdentity;
  }, [projectCode, kind, queryKeySuffix, treeQuery.data, treeQuery.isPlaceholderData]);

  useEffect(() => {
    setTreeKeyword("");
    setSelectedTreeKey("");
  }, [projectCode, kind, queryKeySuffix]);

  return {
    treeKeyword,
    setTreeKeyword,
    expandedKeys,
    setExpandedKeys,
    selectedTreeKey,
    setSelectedTreeKey,
    visibleTree,
    visibleExpandedKeys,
    treeQuery,
  };
}
