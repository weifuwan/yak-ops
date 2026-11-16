import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Modal, toast } from "@yak-ops/yak-ui";

import { businessTypeApi, type BusinessTypeTreeNode } from "@/service/data-modeling/planning/business-type";
import { PlanningIcon } from "../components/icons";
import { planningQueryKeys } from "../query-keys";
import BusinessTypeTree from "./business-type-tree";
import BusinessTypeDetail from "./business-type-detail";
import BusinessTypeFormDialog from "./business-type-form-dialog";
import type { BusinessTypeFormValues, BusinessTypeRecord } from "./types";

interface Props {
  projectCode: string;
}

function collectExpandedBusinessTypeKeys(nodes: BusinessTypeTreeNode[]): Set<string> {
  const keys = new Set<string>();
  const visit = (items: BusinessTypeTreeNode[]) => {
    items.forEach((node) => {
      const key = node.key || node.value;
      if (node.children?.length) {
        keys.add(key);
        visit(node.children);
      }
    });
  };
  visit(nodes);
  return keys;
}

function findBusinessTypePathKeys(
  nodes: BusinessTypeTreeNode[],
  targetId: string,
  ancestors: string[] = [],
): string[] {
  for (const node of nodes) {
    const key = node.key || node.value;
    const path = key ? [...ancestors, key] : ancestors;
    if (node.value === targetId || node.key === targetId) return path;
    const found = findBusinessTypePathKeys(node.children || [], targetId, path);
    if (found.length) return found;
  }
  return [];
}

export function DataModelingBusinessTypePage({ projectCode }: Props) {
  const queryClient = useQueryClient();
  const splitRef = useRef<HTMLDivElement | null>(null);
  const [leftWidth, setLeftWidth] = useState(280);
  const [resizing, setResizing] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [formOpen, setFormOpen] = useState(false);
  const [formRecord, setFormRecord] = useState<BusinessTypeRecord>();
  const [formParentId, setFormParentId] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<BusinessTypeRecord>();

  const treeQuery = useQuery({
    queryKey: planningQueryKeys.businessTypeTree(projectCode, keyword),
    queryFn: () => businessTypeApi.tree(projectCode, keyword),
    enabled: Boolean(projectCode),
  });
  const detailQuery = useQuery({
    queryKey: planningQueryKeys.businessTypeDetail(projectCode, selectedId),
    queryFn: () => businessTypeApi.getById<BusinessTypeRecord>(projectCode, selectedId),
    enabled: Boolean(projectCode && selectedId),
  });
  const treeData = treeQuery.data || [];

  const visibleExpandedKeys = useMemo(() => {
    const next = new Set(expandedKeys);
    if (keyword.trim()) {
      collectExpandedBusinessTypeKeys(treeData).forEach((key) => next.add(key));
    }
    return next;
  }, [expandedKeys, keyword, treeData]);

  useEffect(() => {
    if (!resizing) return;
    const onMove = (event: PointerEvent) => {
      const rect = splitRef.current?.getBoundingClientRect();
      if (rect) setLeftWidth(Math.min(480, Math.max(220, event.clientX - rect.left)));
    };
    const onUp = () => setResizing(false);
    window.addEventListener("pointermove", onMove);
    window.addEventListener("pointerup", onUp);
    return () => {
      window.removeEventListener("pointermove", onMove);
      window.removeEventListener("pointerup", onUp);
    };
  }, [resizing]);

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessTypeTreePrefix(projectCode) }),
      queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessTypeDetailPrefix(projectCode) }),
      queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessTypeChildrenPrefix(projectCode) }),
      queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessTypeRelatedDomainsPrefix(projectCode) }),
      queryClient.invalidateQueries({ queryKey: planningQueryKeys.businessTypeDataMartsPrefix(projectCode) }),
      queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataDomainAll(projectCode) }),
      queryClient.invalidateQueries({ queryKey: planningQueryKeys.dataMartBusinessTypes(projectCode) }),
    ]);
  };

  const toggleExpanded = (key: string) => setExpandedKeys((current) => {
    const next = new Set(current);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    return next;
  });

  const openCreate = (parentId?: string) => {
    setFormRecord(undefined);
    setFormParentId(parentId);
    if (parentId) {
      const path = findBusinessTypePathKeys(treeData, parentId);
      setExpandedKeys((current) => new Set([...current, ...(path.length ? path : [parentId])]));
    }
    setFormOpen(true);
  };

  const openEdit = async (id: string) => {
    try {
      const record = await businessTypeApi.getById<BusinessTypeRecord>(projectCode, id);
      setFormRecord(record);
      setFormParentId(record.parentId);
      setFormOpen(true);
    } catch {
      toast.warning("获取业务分类详情失败，请刷新后重试");
    }
  };

  const saveBusinessType = async (values: BusinessTypeFormValues) => {
    if (saving) return;
    setSaving(true);
    const payload: Record<string, unknown> = {
      ...values,
      ...(formParentId ? { parentId: formParentId } : {}),
    };
    try {
      if (formRecord?.id) {
        await businessTypeApi.update(projectCode, { ...payload, id: formRecord.id });
        toast.success("业务分类已更新");
      } else {
        await businessTypeApi.create(projectCode, payload);
        toast.success("业务分类已创建");
      }
      setFormOpen(false);
      setFormRecord(undefined);
      await queryClient.invalidateQueries({
        queryKey: planningQueryKeys.businessTypeTreePrefix(projectCode),
      });
      if (selectedId) {
        await Promise.all([
          queryClient.invalidateQueries({
            queryKey: planningQueryKeys.businessTypeDetail(projectCode, selectedId),
          }),
          queryClient.invalidateQueries({
            queryKey: planningQueryKeys.businessTypeChildrenPrefix(projectCode),
          }),
        ]);
      }
    } catch {
      toast.warning("保存业务分类失败，请检查填写内容后重试");
    } finally {
      setSaving(false);
    }
  };

  const removeBusinessType = async () => {
    if (!pendingDelete?.id) return;
    try {
      await businessTypeApi.remove(projectCode, pendingDelete.id);
      toast.success("业务分类已删除");
      if (selectedId === pendingDelete.id) setSelectedId("");
      setPendingDelete(undefined);
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: planningQueryKeys.businessTypeTreePrefix(projectCode),
        }),
        queryClient.invalidateQueries({
          queryKey: planningQueryKeys.businessTypeChildrenPrefix(projectCode),
        }),
      ]);
    } catch {
      toast.warning("删除业务分类失败，请先检查子级分类、关联数据域和数据集市");
    }
  };

  const selectedRecord = detailQuery.data;

  return (
    <div ref={splitRef}
      className="relative flex h-full min-h-0 min-w-0 flex-1 overflow-hidden border-t border-[var(--yak-components-panel-border)] bg-white">
      <BusinessTypeTree
        width={leftWidth}
        treeData={treeData}
        selectedId={selectedId}
        expandedKeys={visibleExpandedKeys}
        keyword={keyword}
        loading={treeQuery.isPending}
        error={treeQuery.isError}
        refreshing={treeQuery.isFetching}
        onKeywordChange={setKeyword}
        onToggleExpanded={toggleExpanded}
        onRefresh={() => void refresh()}
        onRetry={() => void treeQuery.refetch()}
        onCreateRoot={() => openCreate()}
        onSelect={(node) => setSelectedId(node.key || node.value)}
        onCreateChild={(node) => openCreate(node.key || node.value)}
        onEdit={(node) => void openEdit(node.key || node.value)}
        onDelete={(node) => setPendingDelete({
          id: node.key || node.value,
          businessName: node.title,
        })}
      />

      <div role="separator" aria-orientation="vertical" aria-label="调整业务分类树宽度"
        className={[
          "relative z-10 w-1 shrink-0 cursor-col-resize touch-none bg-[var(--yak-components-panel-border)] transition-colors hover:bg-[var(--yak-color-primary)]",
          resizing ? "bg-[var(--yak-color-primary)]" : "",
        ].join(" ")}
        onPointerDown={(event) => {
          event.preventDefault();
          setResizing(true);
        }}>
        <span className="absolute inset-y-0 -left-1 -right-1" />
      </div>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#f6f6f6]">
        {selectedId ? (
          detailQuery.isPending ? (
            <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">
              正在加载业务分类详情...
            </div>
          ) : detailQuery.isError ? (
            <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 bg-white text-xs text-[var(--yak-components-muted-text)]">
              <span>业务分类详情加载失败</span>
              <Button size="sm" variant="secondary" onClick={() => void detailQuery.refetch()}>重试</Button>
            </div>
          ) : selectedRecord ? (
            <BusinessTypeDetail
              projectCode={projectCode}
              record={selectedRecord}
              onEdit={() => void openEdit(selectedRecord.id || "")}
              onAddChild={() => openCreate(selectedRecord.id)}
              onEditChild={(record) => void openEdit(record.id || "")}
              onDeleteChild={setPendingDelete}
            />
          ) : null
        ) : (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
            <div className="flex flex-col items-center gap-2 text-center">
              <PlanningIcon name="business-type" size={32} className="text-[#c5cbd3]" />
              <span className="text-sm text-[var(--yak-components-muted-text)]">请选择左侧业务分类查看详情</span>
            </div>
          </div>
        )}
      </main>

      <BusinessTypeFormDialog
        open={formOpen}
        record={formRecord}
        parentId={formParentId}
        saving={saving}
        onClose={() => {
          if (!saving) {
            setFormOpen(false);
            setFormRecord(undefined);
          }
        }}
        onSubmit={(values) => void saveBusinessType(values)}
      />
      <Modal open={Boolean(pendingDelete)} centered width={420} title="删除业务分类"
        onClose={() => setPendingDelete(undefined)}
        footer={<>
          <Button size="sm" variant="secondary" onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" onClick={() => void removeBusinessType()}>删除</Button>
        </>}>
        确定删除“{pendingDelete?.businessName || "当前业务分类"}”吗？
      </Modal>
    </div>
  );
}

export default DataModelingBusinessTypePage;
