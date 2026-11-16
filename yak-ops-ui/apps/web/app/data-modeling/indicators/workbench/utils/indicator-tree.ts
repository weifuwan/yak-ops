import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorGroupBy, IndicatorKind, IndicatorTreeNode, ModifierScopeType } from "../types";
import { recordName } from "./indicator-form";

type GroupedIndicatorKind = Extract<IndicatorKind, "derivativeIndicator" | "composeIndicator">;

function findGroupNode(
  nodes: IndicatorTreeNode[],
  id: string,
  nodeType: "businessType" | "dataDomain" | "dataMart" | "subjectDomain",
): IndicatorTreeNode | undefined {
  for (const node of nodes) {
    if (node.kind === "group" && node.nodeType === nodeType && node.id === id) return node;
    const child = findGroupNode(node.children, id, nodeType);
    if (child) return child;
  }
  return undefined;
}

export function buildGroupedTree(
  groups: ModelingRecord[],
  records: ModelingRecord[],
  groupBy: IndicatorGroupBy,
  kind: GroupedIndicatorKind,
  scopeValue = "",
): IndicatorTreeNode[] {
  const uniqueRecordMap = new Map<string, ModelingRecord>();
  records.forEach((record) => {
    if (record.id) uniqueRecordMap.set(String(record.id), record);
  });
  const uniqueRecords = Array.from(uniqueRecordMap.values());
  const groupNodeType = groupBy === "BUSINESS_TYPE" ? "businessType" : "dataDomain";
  const groupMap = new Map<string, IndicatorTreeNode>();
  groups.forEach((record) => {
    if (!record.id) return;
    const id = String(record.id);
    groupMap.set(id, {
      key: "group:" + id,
      id,
      title: recordName(record),
      kind: "group",
      nodeType: groupNodeType,
      record,
      children: [],
    });
  });

  const roots: IndicatorTreeNode[] = [];
  groups.forEach((record) => {
    if (!record.id) return;
    const id = String(record.id);
    const node = groupMap.get(id);
    if (!node) return;
    const parentId = record.parentId ? String(record.parentId) : "";
    const parent = parentId ? groupMap.get(parentId) : undefined;
    if (parent && parent !== node) parent.children.push(node);
    else roots.push(node);
  });

  const relationKey = groupBy === "BUSINESS_TYPE" ? "busTypeId" : "domainId";
  const ungrouped: IndicatorTreeNode[] = [];
  uniqueRecords.forEach((record) => {
    const id = String(record.id);
    const leaf: IndicatorTreeNode = {
      key: "asset:" + id,
      id,
      title: recordName(record),
      kind: "asset",
      nodeType: "asset",
      record,
      children: [],
    };
    const parentId = record[relationKey] ? String(record[relationKey]) : "";
    const parent = parentId ? groupMap.get(parentId) : undefined;
    if (parent) parent.children.push(leaf);
    else ungrouped.push(leaf);
  });

  if (ungrouped.length) {
    roots.push({
      key: "group:uncategorized",
      id: "uncategorized",
      title: "未分类",
      kind: "group",
      nodeType: "group",
      count: ungrouped.length,
      children: ungrouped.map((node) => node),
    });
  }

  const countAssets = (node: IndicatorTreeNode): number => {
    if (node.kind === "asset") return 1;
    const count = node.children.reduce((total, child) => total + countAssets(child), 0);
    node.count = count;
    return count;
  };

  const root: IndicatorTreeNode = {
    key: "root:" + kind,
    id: "root",
    title: kind === "derivativeIndicator" ? "派生指标" : "复合指标",
    kind: "group",
    nodeType: "root",
    children: roots,
  };
  root.count = countAssets(root);

  if (scopeValue) {
    const selectedNode = findGroupNode(root.children, scopeValue, groupNodeType);
    root.children = selectedNode ? [selectedNode] : [];
    root.count = selectedNode?.count || 0;
  }

  return [root];
}

export function buildAtomIndicatorTree(
  businessTypes: ModelingRecord[],
  dataDomains: ModelingRecord[],
  businessProcesses: ModelingRecord[],
  records: ModelingRecord[],
  groupBy: IndicatorGroupBy,
  relatedDomainIdsByBusinessType: Map<string, string[]>,
  scopeValue = "",
): IndicatorTreeNode[] {
  const uniqueRecordMap = new Map<string, ModelingRecord>();
  records.forEach((record) => {
    if (record.id) uniqueRecordMap.set(String(record.id), record);
  });
  const uniqueRecords = Array.from(uniqueRecordMap.values());

  const domainMap = new Map<string, ModelingRecord>();
  dataDomains.forEach((record) => {
    if (record.id) domainMap.set(String(record.id), record);
  });
  const businessTypeMap = new Map<string, ModelingRecord>();
  businessTypes.forEach((record) => {
    if (record.id) businessTypeMap.set(String(record.id), record);
  });
  const processMap = new Map<string, ModelingRecord>();
  businessProcesses.forEach((record) => {
    if (record.id) processMap.set(String(record.id), record);
  });

  const assetNode = (record: ModelingRecord): IndicatorTreeNode => ({
    key: "asset:" + String(record.id),
    id: String(record.id),
    title: recordName(record),
    kind: "asset",
    nodeType: "asset",
    record,
    children: [],
  });

  const buildDomainNode = (
    domainId: string,
    scopedRecords: ModelingRecord[],
    keyPrefix: string,
  ): IndicatorTreeNode => {
    const domain = domainMap.get(domainId);
    const domainProcesses = domain
      ? businessProcesses.filter((process) => String(process.dataDomainId || "") === domainId)
      : [];
    const processIds = new Set(domainProcesses.map((process) => String(process.id)));
    const children: IndicatorTreeNode[] = domainProcesses.map((process) => {
      const processId = String(process.id);
      const processRecords = scopedRecords.filter((record) => String(record.busProcessId || "") === processId);
      return {
        key: keyPrefix + ":process:" + processId,
        id: processId,
        title: recordName(process),
        kind: "group",
        nodeType: "businessProcess",
        record: process,
        count: processRecords.length,
        children: processRecords.map(assetNode),
      };
    });

    const unassignedProcessRecords = scopedRecords.filter((record) => {
      const processId = String(record.busProcessId || "");
      const process = processMap.get(processId);
      return !process || String(process.dataDomainId || "") !== domainId || !processIds.has(processId);
    });
    if (unassignedProcessRecords.length) {
      children.push({
        key: keyPrefix + ":domain:" + domainId + ":unassigned-process",
        id: "unassigned-process",
        title: "未关联业务过程",
        kind: "group",
        nodeType: "group",
        count: unassignedProcessRecords.length,
        children: unassignedProcessRecords.map(assetNode),
      });
    }

    return {
      key: keyPrefix + ":domain:" + domainId,
      id: domainId,
      title: domain ? recordName(domain) : "未关联数据域",
      kind: "group",
      nodeType: domain ? "dataDomain" : "group",
      record: domain,
      count: scopedRecords.length,
      children,
    };
  };

  const buildUnassignedDomainNode = (
    scopedRecords: ModelingRecord[],
    key: string,
  ): IndicatorTreeNode => ({
    key,
    id: "unassigned-domain",
    title: "未关联数据域",
    kind: "group",
    nodeType: "group",
    count: scopedRecords.length,
    children: scopedRecords.map(assetNode),
  });

  const root: IndicatorTreeNode = {
    key: "root:atom-indicator",
    id: "root",
    title: "原子指标",
    kind: "group",
    nodeType: "root",
    count: uniqueRecords.length,
    children: [],
  };

  if (groupBy === "DATA_DOMAIN") {
    root.children = dataDomains.filter((domain) => domain.id).map((domain) => {
      const domainId = String(domain.id);
      const domainRecords = uniqueRecords.filter((record) => String(record.domainId || "") === domainId);
      return buildDomainNode(domainId, domainRecords, "data-domain:" + domainId);
    });

    const unassignedDomainRecords = uniqueRecords.filter((record) => {
      const domainId = String(record.domainId || "");
      return !domainId || !domainMap.has(domainId);
    });
    if (unassignedDomainRecords.length) {
      root.children.push(buildUnassignedDomainNode(unassignedDomainRecords, "data-domain:unassigned"));
    }

    if (scopeValue) {
      const selectedNode = findGroupNode(root.children, scopeValue, "dataDomain");
      root.children = selectedNode ? [selectedNode] : [];
      root.count = selectedNode?.count || 0;
    }
    return [root];
  }

  const childrenByParent = new Map<string, ModelingRecord[]>();
  businessTypes.forEach((businessType) => {
    const id = String(businessType.id || "");
    const parentId = String(businessType.parentId || "");
    if (!id || !parentId || !businessTypeMap.has(parentId)) return;
    const children = childrenByParent.get(parentId) || [];
    children.push(businessType);
    childrenByParent.set(parentId, children);
  });

  const visitedBusinessTypeIds = new Set<string>();
  const buildBusinessTypeNode = (businessType: ModelingRecord): IndicatorTreeNode | null => {
    const id = String(businessType.id || "");
    if (!id || visitedBusinessTypeIds.has(id)) return null;
    visitedBusinessTypeIds.add(id);

    const directRecords = uniqueRecords.filter((record) => String(record.busTypeId || "") === id);
    const childNodes = (childrenByParent.get(id) || [])
      .map(buildBusinessTypeNode)
      .filter((node): node is IndicatorTreeNode => Boolean(node));

    const relatedDomainIds = relatedDomainIdsByBusinessType.get(id) || [];
    const directDomainIds = directRecords
      .map((record) => String(record.domainId || ""))
      .filter((domainId) => domainId && domainMap.has(domainId));
    const domainIds = Array.from(new Set([...relatedDomainIds, ...directDomainIds]))
      .filter((domainId) => domainMap.has(domainId));
    const domainNodes = domainIds.map((domainId) => buildDomainNode(
      domainId,
      directRecords.filter((record) => String(record.domainId || "") === domainId),
      "business-type:" + id,
    ));

    const unassignedDomainRecords = directRecords.filter((record) => {
      const domainId = String(record.domainId || "");
      return !domainId || !domainMap.has(domainId);
    });
    if (unassignedDomainRecords.length) {
      domainNodes.push(buildUnassignedDomainNode(
        unassignedDomainRecords,
        "business-type:" + id + ":unassigned-domain",
      ));
    }

    return {
      key: "business-type:" + id,
      id,
      title: recordName(businessType),
      kind: "group",
      nodeType: "businessType",
      record: businessType,
      count: directRecords.length + childNodes.reduce((total, child) => total + (child.count || 0), 0),
      children: [...childNodes, ...domainNodes],
    };
  };

  const businessTypeRoots = businessTypes.filter((businessType) => {
    const parentId = String(businessType.parentId || "");
    return !parentId || !businessTypeMap.has(parentId);
  }).map(buildBusinessTypeNode).filter((node): node is IndicatorTreeNode => Boolean(node));

  businessTypes.forEach((businessType) => {
    const id = String(businessType.id || "");
    if (id && !visitedBusinessTypeIds.has(id)) {
      const node = buildBusinessTypeNode(businessType);
      if (node) businessTypeRoots.push(node);
    }
  });

  root.children = businessTypeRoots;

  const unassignedBusinessTypeRecords = uniqueRecords.filter((record) => {
    const businessTypeId = String(record.busTypeId || "");
    return !businessTypeId || !businessTypeMap.has(businessTypeId);
  });
  if (unassignedBusinessTypeRecords.length) {
    const domainIds = Array.from(new Set(unassignedBusinessTypeRecords
      .map((record) => String(record.domainId || ""))
      .filter((domainId) => domainId && domainMap.has(domainId))));
    const domainNodes = domainIds.map((domainId) => buildDomainNode(
      domainId,
      unassignedBusinessTypeRecords.filter((record) => String(record.domainId || "") === domainId),
      "business-type:unassigned",
    ));
    const unassignedDomainRecords = unassignedBusinessTypeRecords.filter((record) => {
      const domainId = String(record.domainId || "");
      return !domainId || !domainMap.has(domainId);
    });
    if (unassignedDomainRecords.length) {
      domainNodes.push(buildUnassignedDomainNode(
        unassignedDomainRecords,
        "business-type:unassigned:unassigned-domain",
      ));
    }
    root.children.push({
      key: "business-type:unassigned",
      id: "unassigned-business-type",
      title: "未关联业务分类",
      kind: "group",
      nodeType: "group",
      count: unassignedBusinessTypeRecords.length,
      children: domainNodes,
    });
  }

  if (scopeValue) {
    const selectedNode = findGroupNode(root.children, scopeValue, "businessType");
    root.children = selectedNode ? [selectedNode] : [];
    root.count = selectedNode?.count || 0;
  }

  return [root];
}

export function buildModifierTree(
  groups: ModelingRecord[],
  records: ModelingRecord[],
  scopeType: ModifierScopeType,
  scopeValue = "",
): IndicatorTreeNode[] {
  const uniqueRecordMap = new Map<string, ModelingRecord>();
  records.forEach((record) => {
    if (record.id) uniqueRecordMap.set(String(record.id), record);
  });
  const uniqueRecords = Array.from(uniqueRecordMap.values());
  const root: IndicatorTreeNode = {
    key: "root:modifier",
    id: "root",
    title: "修饰词",
    kind: "group",
    nodeType: "root",
    count: uniqueRecords.length,
    children: [],
  };
  const assetNode = (record: ModelingRecord): IndicatorTreeNode => ({
    key: "asset:" + String(record.id),
    id: String(record.id),
    title: recordName(record),
    kind: "asset",
    nodeType: "asset",
    record,
    children: [],
  });

  if (scopeType === "DATA_DOMAIN") {
    const domainMap = new Map<string, ModelingRecord>();
    groups.forEach((group) => {
      if (group.id) domainMap.set(String(group.id), group);
    });
    const domainNodes = Array.from(domainMap.values()).map((domain) => {
      const id = String(domain.id);
      const scopedRecords = uniqueRecords.filter((record) => String(record.domainId || "") === id);
      return {
        key: "modifier-domain:" + id,
        id,
        title: recordName(domain),
        kind: "group" as const,
        nodeType: "dataDomain" as const,
        record: domain,
        count: scopedRecords.length,
        children: scopedRecords.map(assetNode),
      };
    });
    const unassignedRecords = uniqueRecords.filter((record) => {
      const id = String(record.domainId || "");
      return !id || !domainMap.has(id);
    });
    const defaultNode: IndicatorTreeNode = {
      key: "modifier-default",
      id: "default",
      title: "默认",
      kind: "group",
      nodeType: "dataDomain",
      count: unassignedRecords.length,
      children: unassignedRecords.map(assetNode),
    };
    if (scopeValue) {
      const selected = domainNodes.find((node) => node.id === scopeValue);
      root.children = selected ? [selected] : [];
      root.count = selected?.count || 0;
    } else {
      root.children = [defaultNode, ...domainNodes];
    }
    return [root];
  }

  const groupMap = new Map<string, ModelingRecord>();
  groups.forEach((group) => {
    if (group.id) groupMap.set(String(group.id), group);
  });
  const childrenByParent = new Map<string, ModelingRecord[]>();
  groups.forEach((group) => {
    const id = String(group.id || "");
    const parentId = String(group.parentId || "");
    if (!id || !parentId || !groupMap.has(parentId)) return;
    const children = childrenByParent.get(parentId) || [];
    children.push(group);
    childrenByParent.set(parentId, children);
  });

  const visited = new Set<string>();
  const buildBusinessTypeNode = (group: ModelingRecord): IndicatorTreeNode | null => {
    const id = String(group.id || "");
    if (!id || visited.has(id)) return null;
    visited.add(id);
    const directRecords = uniqueRecords.filter((record) => String(record.busTypeId || "") === id);
    const childNodes = (childrenByParent.get(id) || [])
      .map(buildBusinessTypeNode)
      .filter((node): node is IndicatorTreeNode => Boolean(node));
    const count = directRecords.length + childNodes.reduce((total, child) => total + (child.count || 0), 0);
    return {
      key: "modifier-business-type:" + id,
      id,
      title: recordName(group),
      kind: "group",
      nodeType: "businessType",
      record: group,
      count,
      children: [...childNodes, ...directRecords.map(assetNode)],
    };
  };

  const roots = groups.filter((group) => {
    const parentId = String(group.parentId || "");
    return !parentId || !groupMap.has(parentId);
  }).map(buildBusinessTypeNode).filter((node): node is IndicatorTreeNode => Boolean(node));
  groups.forEach((group) => {
    const id = String(group.id || "");
    if (id && !visited.has(id)) {
      const node = buildBusinessTypeNode(group);
      if (node) roots.push(node);
    }
  });

  const unassignedRecords = uniqueRecords.filter((record) => {
    const id = String(record.busTypeId || "");
    return !id || !groupMap.has(id);
  });
  const defaultNode: IndicatorTreeNode = {
    key: "modifier-default",
    id: "default",
    title: "默认",
    kind: "group",
    nodeType: "businessType",
    count: unassignedRecords.length,
    children: unassignedRecords.map(assetNode),
  };

  if (scopeValue) {
    const selectedNode = roots.reduce<IndicatorTreeNode | undefined>((found, node) => {
      if (found) return found;
      if (node.id === scopeValue) return node;
      const pending = [...node.children];
      while (pending.length) {
        const current = pending.shift();
        if (!current || current.kind !== "group") continue;
        if (current.id === scopeValue) return current;
        pending.push(...current.children.filter((child) => child.kind === "group"));
      }
      return undefined;
    }, undefined);
    if (!selectedNode) {
      root.children = [];
      root.count = 0;
    } else {
      const selectedIds = new Set<string>();
      const collectGroupIds = (node: IndicatorTreeNode) => {
        selectedIds.add(node.id);
        node.children.filter((child) => child.kind === "group").forEach(collectGroupIds);
      };
      collectGroupIds(selectedNode);
      root.children = [selectedNode];
      root.count = uniqueRecords.filter((record) => selectedIds.has(String(record.busTypeId || ""))).length;
    }
  } else {
    root.children = [defaultNode, ...roots];
  }
  return [root];
}

export function buildTimePeriodTree(categories: ModelingRecord[], records: ModelingRecord[]): IndicatorTreeNode[] {
  const nodeMap = new Map<string, IndicatorTreeNode>();
  categories.forEach((record) => {
    if (!record.id) return;
    const id = String(record.id);
    nodeMap.set(id, {
      key: "category:" + id,
      id,
      title: recordName(record),
      kind: "group",
      children: [],
    });
  });
  const roots: IndicatorTreeNode[] = [];
  categories.forEach((record) => {
    if (!record.id) return;
    const node = nodeMap.get(String(record.id));
    if (!node) return;
    const parent = record.parentId ? nodeMap.get(String(record.parentId)) : undefined;
    if (parent && parent !== node) parent.children.push(node);
    else roots.push(node);
  });
  const ungrouped: IndicatorTreeNode[] = [];
  records.forEach((record) => {
    if (!record.id) return;
    const leaf: IndicatorTreeNode = {
      key: "asset:" + String(record.id),
      id: String(record.id),
      title: recordName(record),
      kind: "asset",
      record,
      children: [],
    };
    const parent = record.categoryId ? nodeMap.get(String(record.categoryId)) : undefined;
    if (parent) parent.children.push(leaf);
    else ungrouped.push(leaf);
  });
  if (ungrouped.length) {
    roots.push({
      key: "group:uncategorized",
      id: "uncategorized",
      title: "未归类周期",
      kind: "group",
      children: ungrouped,
    });
  }
  return roots;
}

export function filterTree(nodes: IndicatorTreeNode[], keyword: string): IndicatorTreeNode[] {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) return nodes;
  return nodes.reduce<IndicatorTreeNode[]>((result, node) => {
    const children = filterTree(node.children, normalized);
    const searchableText = [
      node.title,
      node.record?.cnName,
      node.record?.enName,
      node.record?.enShortName,
    ].filter(Boolean).join(" ").toLowerCase();
    if (searchableText.includes(normalized) || children.length) {
      result.push({ ...node, children });
    }
    return result;
  }, []);
}

export function collectExpandedKeys(nodes: IndicatorTreeNode[]): string[] {
  const result: string[] = [];
  nodes.forEach((node) => {
    if (node.children.length) {
      result.push(node.key);
      result.push(...collectExpandedKeys(node.children));
    }
  });
  return result;
}

export type DerivativeIndicatorTreeInput = {
  businessTypes: ModelingRecord[];
  dataDomains: ModelingRecord[];
  businessProcesses: ModelingRecord[];
  dataMarts: ModelingRecord[];
  subjectDomains: ModelingRecord[];
  records: ModelingRecord[];
  groupBy: IndicatorGroupBy;
  scopeValue?: string;
  dwType: string;
};

export function buildDerivativeIndicatorTree(input: DerivativeIndicatorTreeInput): IndicatorTreeNode[] {
  const {
    businessTypes, dataDomains, businessProcesses, dataMarts, subjectDomains,
    groupBy, scopeValue = "", dwType,
  } = input;
  const recordMap = new Map<string, ModelingRecord>();
  input.records.forEach((record) => {
    if (record.id) recordMap.set(String(record.id), record);
  });
  const records = Array.from(recordMap.values());
  const assetNode = (record: ModelingRecord): IndicatorTreeNode => ({
    key: "asset:" + String(record.id),
    id: String(record.id),
    title: recordName(record),
    kind: "asset",
    nodeType: "asset",
    record,
    children: [],
  });
  const countAssets = (node: IndicatorTreeNode): number => {
    if (node.kind === "asset") return 1;
    node.count = node.children.reduce((total, child) => total + countAssets(child), 0);
    return node.count;
  };
  const root: IndicatorTreeNode = {
    key: "root:derivative-indicator",
    id: "root",
    title: "派生指标",
    kind: "group",
    nodeType: "root",
    children: [],
  };

  if (dwType === "UNASSIGNED") {
    const unassigned = records.map(assetNode);
    if (unassigned.length) {
      root.children = [{
        key: "group:unassigned-warehouse-layer",
        id: "unassigned-warehouse-layer",
        title: "未设置数仓分层",
        kind: "group",
        nodeType: "group",
        count: unassigned.length,
        children: unassigned,
      }];
    }
    root.count = records.length;
    return [root];
  }

  if (dwType === "PUBLIC_LAYER") {
    const domainMap = new Map<string, ModelingRecord>();
    dataDomains.forEach((record) => {
      if (record.id) domainMap.set(String(record.id), record);
    });
    const processMap = new Map<string, ModelingRecord>();
    businessProcesses.forEach((record) => {
      if (record.id) processMap.set(String(record.id), record);
    });

    const buildDomainNode = (domainId: string, scopedRecords: ModelingRecord[], keyPrefix: string): IndicatorTreeNode => {
      const domain = domainMap.get(domainId);
      const domainProcesses = businessProcesses.filter((process) =>
        String(process.dataDomainId || "") === domainId && Boolean(process.id),
      );
      const processIds = new Set(domainProcesses.map((process) => String(process.id)));
      const children: IndicatorTreeNode[] = domainProcesses.map((process) => {
        const processId = String(process.id);
        const processRecords = scopedRecords.filter((record) => String(record.busProcessId || "") === processId);
        return {
          key: keyPrefix + ":process:" + processId,
          id: processId,
          title: recordName(process),
          kind: "group",
          nodeType: "businessProcess",
          record: process,
          count: processRecords.length,
          children: processRecords.map(assetNode),
        };
      });
      const unassignedProcessRecords = scopedRecords.filter((record) => {
        const processId = String(record.busProcessId || "");
        const process = processMap.get(processId);
        return !process || String(process.dataDomainId || "") !== domainId || !processIds.has(processId);
      });
      if (unassignedProcessRecords.length) {
        children.push({
          key: keyPrefix + ":unassigned-process",
          id: "unassigned-process",
          title: "未关联业务过程",
          kind: "group",
          nodeType: "group",
          count: unassignedProcessRecords.length,
          children: unassignedProcessRecords.map(assetNode),
        });
      }
      return {
        key: keyPrefix + ":domain:" + domainId,
        id: domainId,
        title: domain ? recordName(domain) : "未关联数据域",
        kind: "group",
        nodeType: domain ? "dataDomain" : "group",
        record: domain,
        count: children.reduce((total, child) => total + (child.count || 0), 0),
        children,
      };
    };

    if (groupBy === "DATA_DOMAIN") {
      root.children = dataDomains.filter((domain) => domain.id).map((domain) => {
        const id = String(domain.id);
        return buildDomainNode(id, records.filter((record) => String(record.domainId || "") === id), "data-domain:" + id);
      });
      const unassignedDomainRecords = records.filter((record) => {
        const id = String(record.domainId || "");
        return !id || !domainMap.has(id);
      });
      if (unassignedDomainRecords.length) {
        root.children.push({
          key: "data-domain:unassigned",
          id: "unassigned-domain",
          title: "未关联数据域",
          kind: "group",
          nodeType: "group",
          count: unassignedDomainRecords.length,
          children: unassignedDomainRecords.map(assetNode),
        });
      }
    } else {
      const businessTypeMap = new Map<string, ModelingRecord>();
      businessTypes.forEach((record) => {
        if (record.id) businessTypeMap.set(String(record.id), record);
      });
      const childrenByParent = new Map<string, ModelingRecord[]>();
      businessTypes.forEach((record) => {
        const id = String(record.id || "");
        const parentId = String(record.parentId || "");
        if (!id || !parentId || !businessTypeMap.has(parentId)) return;
        const children = childrenByParent.get(parentId) || [];
        children.push(record);
        childrenByParent.set(parentId, children);
      });
      const visited = new Set<string>();
      const buildBusinessTypeNode = (businessType: ModelingRecord): IndicatorTreeNode | null => {
        const id = String(businessType.id || "");
        if (!id || visited.has(id)) return null;
        visited.add(id);
        const directRecords = records.filter((record) => String(record.busTypeId || "") === id);
        const childNodes = (childrenByParent.get(id) || [])
          .map(buildBusinessTypeNode)
          .filter((node): node is IndicatorTreeNode => Boolean(node));
        const domainIds = Array.from(new Set(directRecords
          .map((record) => String(record.domainId || ""))
          .filter((domainId) => domainId && domainMap.has(domainId))));
        const domainNodes = domainIds.map((domainId) => buildDomainNode(
          domainId,
          directRecords.filter((record) => String(record.domainId || "") === domainId),
          "business-type:" + id,
        ));
        const unassignedDomainRecords = directRecords.filter((record) => {
          const domainId = String(record.domainId || "");
          return !domainId || !domainMap.has(domainId);
        });
        if (unassignedDomainRecords.length) {
          domainNodes.push({
            key: "business-type:" + id + ":unassigned-domain",
            id: "unassigned-domain",
            title: "未关联数据域",
            kind: "group",
            nodeType: "group",
            count: unassignedDomainRecords.length,
            children: unassignedDomainRecords.map(assetNode),
          });
        }
        return {
          key: "business-type:" + id,
          id,
          title: recordName(businessType),
          kind: "group",
          nodeType: "businessType",
          record: businessType,
          count: childNodes.reduce((total, child) => total + (child.count || 0), 0)
            + domainNodes.reduce((total, child) => total + (child.count || 0), 0),
          children: [...childNodes, ...domainNodes],
        };
      };
      root.children = businessTypes.filter((record) => {
        const parentId = String(record.parentId || "");
        return record.id && (!parentId || !businessTypeMap.has(parentId));
      }).map(buildBusinessTypeNode).filter((node): node is IndicatorTreeNode => Boolean(node));
      businessTypes.forEach((record) => {
        const id = String(record.id || "");
        if (id && !visited.has(id)) {
          const node = buildBusinessTypeNode(record);
          if (node) root.children.push(node);
        }
      });
      const unassignedBusinessTypeRecords = records.filter((record) => {
        const id = String(record.busTypeId || "");
        return !id || !businessTypeMap.has(id);
      });
      if (unassignedBusinessTypeRecords.length) {
        const domainIds = Array.from(new Set(unassignedBusinessTypeRecords
          .map((record) => String(record.domainId || ""))
          .filter((domainId) => domainId && domainMap.has(domainId))));
        const domainNodes = domainIds.map((domainId) => buildDomainNode(
          domainId,
          unassignedBusinessTypeRecords.filter((record) => String(record.domainId || "") === domainId),
          "business-type:unassigned",
        ));
        const noDomain = unassignedBusinessTypeRecords.filter((record) => {
          const domainId = String(record.domainId || "");
          return !domainId || !domainMap.has(domainId);
        });
        if (noDomain.length) {
          domainNodes.push({
            key: "business-type:unassigned:unassigned-domain",
            id: "unassigned-domain",
            title: "未关联数据域",
            kind: "group",
            nodeType: "group",
            count: noDomain.length,
            children: noDomain.map(assetNode),
          });
        }
        root.children.push({
          key: "business-type:unassigned",
          id: "unassigned-business-type",
          title: "未关联业务分类",
          kind: "group",
          nodeType: "group",
          count: domainNodes.reduce((total, child) => total + (child.count || 0), 0),
          children: domainNodes,
        });
      }
    }
  } else if (dwType === "APPLICATION_LAYER") {
    const martMap = new Map<string, ModelingRecord>();
    dataMarts.forEach((record) => {
      if (record.id) martMap.set(String(record.id), record);
    });
    const subjectMap = new Map<string, ModelingRecord>();
    subjectDomains.forEach((record) => {
      if (record.id) subjectMap.set(String(record.id), record);
    });
    const subjectChildren = new Map<string, ModelingRecord[]>();
    subjectDomains.forEach((record) => {
      const id = String(record.id || "");
      const parentId = String(record.parentId || "");
      if (!id || !parentId || !subjectMap.has(parentId)) return;
      const parent = subjectMap.get(parentId);
      if (String(parent?.dataMartId || "") !== String(record.dataMartId || "")) return;
      const children = subjectChildren.get(parentId) || [];
      children.push(record);
      subjectChildren.set(parentId, children);
    });
    const visitedSubjects = new Set<string>();
    const buildSubjectNode = (subject: ModelingRecord, martId: string): IndicatorTreeNode | null => {
      const id = String(subject.id || "");
      if (!id || visitedSubjects.has(id)) return null;
      visitedSubjects.add(id);
      const directRecords = records.filter((record) =>
        String(record.subjectId || "") === id && String(record.martId || "") === martId,
      );
      const childNodes = (subjectChildren.get(id) || [])
        .map((child) => buildSubjectNode(child, martId))
        .filter((node): node is IndicatorTreeNode => Boolean(node));
      return {
        key: "subject-domain:" + id,
        id,
        title: recordName(subject),
        kind: "group",
        nodeType: "subjectDomain",
        record: subject,
        count: directRecords.length + childNodes.reduce((total, child) => total + (child.count || 0), 0),
        children: [...childNodes, ...directRecords.map(assetNode)],
      };
    };
    const martChildren = new Map<string, ModelingRecord[]>();
    dataMarts.forEach((record) => {
      const id = String(record.id || "");
      const parentId = String(record.parentId || "");
      if (!id || !parentId || !martMap.has(parentId)) return;
      const children = martChildren.get(parentId) || [];
      children.push(record);
      martChildren.set(parentId, children);
    });
    const visitedMarts = new Set<string>();
    const buildMartNode = (mart: ModelingRecord): IndicatorTreeNode | null => {
      const id = String(mart.id || "");
      if (!id || visitedMarts.has(id)) return null;
      visitedMarts.add(id);
      const martSubjects = subjectDomains.filter((subject) => {
        if (String(subject.dataMartId || "") !== id) return false;
        const parentId = String(subject.parentId || "");
        return !parentId || !subjectMap.has(parentId)
          || String(subjectMap.get(parentId)?.dataMartId || "") !== id;
      });
      const subjectNodes = martSubjects
        .map((subject) => buildSubjectNode(subject, id))
        .filter((node): node is IndicatorTreeNode => Boolean(node));
      const unassignedSubjectRecords = records.filter((record) => {
        if (String(record.martId || "") !== id) return false;
        const subjectId = String(record.subjectId || "");
        return !subjectId || !subjectMap.has(subjectId)
          || String(subjectMap.get(subjectId)?.dataMartId || "") !== id;
      });
      if (unassignedSubjectRecords.length) {
        subjectNodes.push({
          key: "data-mart:" + id + ":unassigned-subject",
          id: "unassigned-subject",
          title: "未关联主题域",
          kind: "group",
          nodeType: "group",
          count: unassignedSubjectRecords.length,
          children: unassignedSubjectRecords.map(assetNode),
        });
      }
      const childMarts = (martChildren.get(id) || [])
        .map(buildMartNode)
        .filter((node): node is IndicatorTreeNode => Boolean(node));
      return {
        key: "data-mart:" + id,
        id,
        title: recordName(mart),
        kind: "group",
        nodeType: "dataMart",
        record: mart,
        count: subjectNodes.reduce((total, child) => total + (child.count || 0), 0)
          + childMarts.reduce((total, child) => total + (child.count || 0), 0),
        children: [...childMarts, ...subjectNodes],
      };
    };
    root.children = dataMarts.filter((mart) => {
      const parentId = String(mart.parentId || "");
      return mart.id && (!parentId || !martMap.has(parentId));
    }).map(buildMartNode).filter((node): node is IndicatorTreeNode => Boolean(node));
    dataMarts.forEach((mart) => {
      const id = String(mart.id || "");
      if (id && !visitedMarts.has(id)) {
        const node = buildMartNode(mart);
        if (node) root.children.push(node);
      }
    });
    const unassignedMartRecords = records.filter((record) => {
      const id = String(record.martId || "");
      return !id || !martMap.has(id);
    });
    if (unassignedMartRecords.length) {
      root.children.push({
        key: "data-mart:unassigned",
        id: "unassigned-mart",
        title: "未关联数据集市",
        kind: "group",
        nodeType: "group",
        count: unassignedMartRecords.length,
        children: unassignedMartRecords.map(assetNode),
      });
    }
  }

  root.count = countAssets(root);
  if (scopeValue) {
    const nodeType = groupBy === "BUSINESS_TYPE" ? "businessType"
      : groupBy === "DATA_DOMAIN" ? "dataDomain"
      : groupBy === "DATA_MART" ? "dataMart"
      : "subjectDomain";
    const selectedNode = findGroupNode(root.children, scopeValue, nodeType);
    root.children = selectedNode ? [selectedNode] : [];
    root.count = selectedNode?.count || 0;
  }
  return [root];
}
