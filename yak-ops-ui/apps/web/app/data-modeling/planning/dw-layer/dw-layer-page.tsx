import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Layers3, LoaderCircle, Plus, RefreshCw, Search } from "lucide-react";
import { Button, Input, Modal, toast } from "@yak-ops/yak-ui";

import { modelingEntityApi } from "../../service";
import DwLayerCard from "./dw-layer-card";
import DwLayerDetail from "./dw-layer-detail";
import DwLayerFormDialog from "./dw-layer-form-dialog";
import {
  DW_LAYER_TYPES,
  getDwTypeLabel,
  getEnumCode,
  type DwLayerFormValues,
  type DwLayerRecord,
} from "./constants";

interface DataModelingDwLayerPageProps {
  projectCode: string;
}

export function DataModelingDwLayerPage({ projectCode }: DataModelingDwLayerPageProps) {
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState("");
  const [viewMode, setViewMode] = useState<"layer" | "flat">("layer");
  const [selectedId, setSelectedId] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DwLayerRecord>();
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<DwLayerRecord>();
  const [deleting, setDeleting] = useState(false);

  const listQueryKey = ["modeling", "dw-layer", "list", projectCode];
  const listQuery = useQuery({
    queryKey: listQueryKey,
    queryFn: () => modelingEntityApi.all<DwLayerRecord>(projectCode, "dwLayer"),
    enabled: Boolean(projectCode),
  });

  const detailQuery = useQuery({
    queryKey: ["modeling", "dw-layer", "detail", projectCode, selectedId],
    queryFn: () => modelingEntityApi.getById<DwLayerRecord>(projectCode, "dwLayer", selectedId),
    enabled: Boolean(projectCode && selectedId),
  });

  const records = listQuery.data || [];
  const filteredRecords = useMemo(() => {
    const value = keyword.trim().toLocaleLowerCase();
    if (!value) return records;
    return records.filter((record) => [
      record.cnName,
      record.enShortName,
      record.enName,
      record.director,
      record.remark,
      getDwTypeLabel(record.dwType, record.dwTypeName),
    ].some((item) => String(item || "").toLocaleLowerCase().includes(value)));
  }, [keyword, records]);

  const groups = useMemo(() => {
    const knownGroups = DW_LAYER_TYPES.map((type) => ({
      ...type,
      records: filteredRecords.filter((record) => getEnumCode(record.dwType) === type.value),
    })).filter((group) => group.records.length > 0);

    const knownCodes = new Set(DW_LAYER_TYPES.map((type) => type.value));
    const otherRecords = filteredRecords.filter((record) => !knownCodes.has(getEnumCode(record.dwType)));
    if (otherRecords.length > 0) {
      knownGroups.push({
        value: "OTHER_LAYER",
        label: "其他",
        modelKeys: [],
        records: otherRecords,
      });
    }
    return knownGroups;
  }, [filteredRecords]);

  const openCreate = () => {
    setEditing(undefined);
    setFormOpen(true);
  };

  const openEdit = async (record: DwLayerRecord) => {
    if (!record.id || openingId) return;
    setOpeningId(record.id);
    try {
      const detail = await modelingEntityApi.getById<DwLayerRecord>(projectCode, "dwLayer", record.id);
      setEditing(detail);
      setFormOpen(true);
    } catch {
      toast.warning("获取分层详情失败，请刷新列表后重试");
    } finally {
      setOpeningId("");
    }
  };

  const closeForm = () => {
    if (saving) return;
    setFormOpen(false);
    setEditing(undefined);
  };

  const save = async (values: DwLayerFormValues) => {
    if (saving) return;
    const payload: Record<string, unknown> = { ...values };
    if (editing?.id) payload.id = editing.id;

    setSaving(true);
    try {
      if (editing?.id) {
        await modelingEntityApi.update(projectCode, "dwLayer", payload);
        toast.success("数仓分层已更新");
      } else {
        await modelingEntityApi.create(projectCode, "dwLayer", payload);
        toast.success("数仓分层已创建");
      }
      setFormOpen(false);
      setEditing(undefined);
      await queryClient.invalidateQueries({ queryKey: listQueryKey });
      if (selectedId) {
        await queryClient.invalidateQueries({
          queryKey: ["modeling", "dw-layer", "detail", projectCode, selectedId],
        });
      }
    } catch {
      toast.warning("保存失败，请检查填写内容后重试");
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!pendingDelete?.id || deleting) return;
    setDeleting(true);
    try {
      await modelingEntityApi.remove(projectCode, "dwLayer", pendingDelete.id);
      toast.success("数仓分层已删除");
      if (selectedId === pendingDelete.id) setSelectedId("");
      setPendingDelete(undefined);
      await queryClient.invalidateQueries({ queryKey: listQueryKey });
    } catch {
      toast.warning("删除失败，请确认该分层没有被其他建模对象引用");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-white text-[var(--yak-color-primary)]">
            <Layers3 size={16} />
          </span>
          <div className="min-w-0">
            <p className="text-xs leading-5 text-[var(--yak-components-muted-text)]">
              维护贴源层、公共层和应用层的分层定义，为后续模型设计提供统一的分层与模型类型。
            </p>
            <p className="mt-0.5 text-[11px] text-[var(--yak-components-muted-text)]">
              共 {records.length} 个分层定义
            </p>
          </div>
        </div>

        {!selectedId ? (
          <Button
            size="sm"
            variant="secondary"
            isDisabled={listQuery.isFetching}
            onClick={() => void listQuery.refetch()}
          >
            <RefreshCw size={13} className={listQuery.isFetching ? "animate-spin" : ""} />
            刷新
          </Button>
        ) : null}
      </div>

      {selectedId ? (
        <DwLayerDetail
          record={detailQuery.data}
          loading={detailQuery.isPending}
          error={detailQuery.isError}
          onBack={() => setSelectedId("")}
          onEdit={openEdit}
          onRetry={() => void detailQuery.refetch()}
        />
      ) : (
        <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
          <div className="flex shrink-0 flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 flex-wrap items-center gap-2">
              <Button size="sm" variant="primary" onClick={openCreate}>
                <Plus size={14} />
                新建分层
              </Button>
              <div className="relative w-[260px] max-w-full">
                <Search
                  size={14}
                  className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
                />
                <Input
                  size="small"
                  variant="outlined"
                  value={keyword}
                  className="pl-8"
                  placeholder="搜索分层名称、英文缩写或负责人"
                  onChange={(event) => setKeyword(event.target.value)}
                />
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <div className="flex items-center rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] p-0.5">
                <Button
                  size="sm"
                  variant={viewMode === "layer" ? "primary" : "ghost"}
                  className="h-7 px-2.5 text-xs"
                  onClick={() => setViewMode("layer")}
                >
                  层级结构展示
                </Button>
                <Button
                  size="sm"
                  variant={viewMode === "flat" ? "primary" : "ghost"}
                  className="h-7 px-2.5 text-xs"
                  onClick={() => setViewMode("flat")}
                >
                  平铺展示
                </Button>
              </div>
            </div>
          </div>

          {listQuery.isError ? (
            <div
              role="alert"
              className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2.5 text-xs text-amber-800"
            >
              <span>数仓分层加载失败，请检查建模服务或当前工作空间权限。</span>
              <Button size="sm" variant="secondary" onClick={() => void listQuery.refetch()}>
                重试
              </Button>
            </div>
          ) : null}

          <div className="mt-4 min-h-0 flex-1 overflow-y-auto">
            {listQuery.isPending ? (
              <div className="flex min-h-[220px] items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
                <LoaderCircle size={15} className="animate-spin" />
                正在加载数仓分层...
              </div>
            ) : listQuery.isError ? null : filteredRecords.length === 0 ? (
              <div className="flex min-h-[220px] flex-col items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
                <Layers3 size={24} strokeWidth={1.4} />
                <span>{keyword ? "没有找到匹配的分层" : "暂无数仓分层，点击“新建分层”开始配置"}</span>
              </div>
            ) : viewMode === "flat" ? (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
                {filteredRecords.map((record) => (
                  <DwLayerCard
                    key={record.id}
                    record={record}
                    opening={openingId === record.id}
                    onOpen={(item) => setSelectedId(item.id || "")}
                    onEdit={openEdit}
                    onDelete={setPendingDelete}
                  />
                ))}
              </div>
            ) : (
              <div className="space-y-5 pb-2">
                {groups.map((group) => (
                  <section key={group.value}>
                    <div className="mb-3 flex items-center gap-2">
                      <span className="h-5 w-1 rounded-full bg-[var(--yak-color-primary)]" />
                      <h3 className="text-sm font-semibold text-[var(--yak-components-panel-text)]">
                        {group.label}
                      </h3>
                      <span className="text-[11px] text-[var(--yak-components-muted-text)]">
                        {group.records.length} 项
                      </span>
                    </div>
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
                      {group.records.map((record) => (
                        <DwLayerCard
                          key={record.id}
                          record={record}
                          opening={openingId === record.id}
                          onOpen={(item) => setSelectedId(item.id || "")}
                          onEdit={openEdit}
                          onDelete={setPendingDelete}
                        />
                      ))}
                    </div>
                  </section>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      <DwLayerFormDialog
        open={formOpen}
        editing={editing}
        saving={saving}
        onClose={closeForm}
        onSubmit={(values) => void save(values)}
      />

      <Modal
        open={Boolean(pendingDelete)}
        centered
        width={420}
        title="删除数仓分层"
        onClose={() => {
          if (!deleting) setPendingDelete(undefined);
        }}
        footer={
          <>
            <Button
              size="sm"
              variant="secondary"
              isDisabled={deleting}
              onClick={() => setPendingDelete(undefined)}
            >
              取消
            </Button>
            <Button size="sm" variant="danger" isPending={deleting} onClick={() => void remove()}>
              删除
            </Button>
          </>
        }
      >
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
          确定删除“{pendingDelete?.cnName || pendingDelete?.enShortName || "当前分层"}”吗？
          如果该分层已被其他建模对象引用，删除可能会失败。
        </p>
      </Modal>
    </div>
  );
}

export default DataModelingDwLayerPage;
