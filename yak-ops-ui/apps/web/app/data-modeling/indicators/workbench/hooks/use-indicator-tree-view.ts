import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import type { IndicatorKind, IndicatorTreeNode } from "../types";
import { collectExpandedKeys, filterTree } from "../utils/indicator-tree";

type UseIndicatorTreeViewOptions = {
  projectCode: string;
  kind: IndicatorKind;
  queryKeyParts: unknown[];
  queryFn: () => Promise<IndicatorTreeNode[]>;
  enabled: boolean;
  expandRootOnly?: boolean;
};

export function useIndicatorTreeView({
  projectCode,
  kind,
  queryKeyParts,
  queryFn,
  enabled,
  expandRootOnly = false,
}: UseIndicatorTreeViewOptions) {
  const [treeKeyword, setTreeKeyword] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [selectedTreeKey, setSelectedTreeKey] = useState("");
  const queryKeySuffix = JSON.stringify(queryKeyParts);

  const treeQuery = useQuery<IndicatorTreeNode[]>({
    queryKey: ["modeling", "indicator-tree", projectCode, kind, ...queryKeyParts],
    queryFn,
    enabled: Boolean(projectCode && enabled),
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
    const nodes = treeQuery.data || [];
    if (expandRootOnly) {
      const root = nodes.find((node) => node.nodeType === "root");
      setExpandedKeys(new Set(root ? [root.key] : []));
      return;
    }
    setExpandedKeys(new Set(collectExpandedKeys(nodes)));
  }, [treeQuery.data, expandRootOnly]);

  useEffect(() => {
    setTreeKeyword("");
    setSelectedTreeKey("");
  }, [kind, queryKeySuffix]);

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
