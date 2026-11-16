import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Layers3, LoaderCircle, Plus } from "lucide-react";
import { Button, Modal, PageHeader, Radio, RadioGroup, toast } from "@yak-ops/yak-ui";

import { modelingEntityApi } from "../../service";
import DwLayerCard from "./dw-layer-card";
import DwLayerDetail from "./dw-layer-detail";
import DwLayerFormDialog from "./dw-layer-form-dialog";
import {
  DW_LAYER_TYPES,
  getEnumCode,
  type DwLayerFormValues,
  type DwLayerRecord,
} from "./constants";

interface DataModelingDwLayerPageProps {
  projectCode: string;
}

const LAYER_DISPLAY_ORDER: Record<string, number> = {
  ODS: 0,
  DIM: 1,
  DWS: 2,
  DWD: 3,
  ADS: 4,
};

type DwLayerDisplayMode = "hierarchy" | "flat";

const DW_LAYER_DISPLAY_OPTIONS = [
  { value: "hierarchy", label: "层级结构展示" },
  { value: "flat", label: "平铺展示" },
] as const;

export function DataModelingDwLayerPage({ projectCode }: DataModelingDwLayerPageProps) {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DwLayerRecord>();
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState("");
  const [pendingDelete, setPendingDelete] = useState<DwLayerRecord>();
  const [viewMode, setViewMode] = useState<DwLayerDisplayMode>("hierarchy");
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
  const groups = useMemo(() => {
    const knownGroups = DW_LAYER_TYPES.map((type) => ({
      ...type,
      label: type.value === "NONE_LAYER" ? "其他" : type.label,
      records: records
        .filter((record) => getEnumCode(record.dwType) === type.value)
        .sort((left, right) =>
          (LAYER_DISPLAY_ORDER[left.enShortName || ""] ?? 99)
          - (LAYER_DISPLAY_ORDER[right.enShortName || ""] ?? 99),
        ),
    })).filter((group) => group.records.length > 0);

    const knownCodes = new Set(DW_LAYER_TYPES.map((type) => type.value));
    const otherRecords = records.filter((record) => !knownCodes.has(getEnumCode(record.dwType)));
    if (otherRecords.length > 0) {
      const otherGroup = knownGroups.find((group) => group.value === "NONE_LAYER");
      if (otherGroup) {
        otherGroup.records.push(...otherRecords);
      } else {
        knownGroups.push({
          value: "OTHER_LAYER",
          label: "其他",
          modelKeys: [],
          records: otherRecords,
        });
      }
    }
    return knownGroups;
  }, [records]);
  const flatRecords = useMemo(() => groups.flatMap((group) => group.records), [groups]);

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
    const payload: Record<string, unknown> = {
      ...values,
      dwModelType: values.dwModelType || undefined,
    };
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
    <div className="flex min-h-0 flex-1 flex-col bg-[#f5f6f8]">
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
        <>
          <PageHeader
            title="数仓分层"
            description={
              <span className="text-xs leading-5">
                按数据处理链路组织标准数仓分层，统一管理各层的模型类型与负责人。
              </span>
            }
            bordered
            className="shrink-0 bg-white px-6 max-md:px-4"
          />

          <main className="min-h-0 flex-1 overflow-y-auto px-6 pb-6 pt-4 max-md:px-4">
            <div className="mb-4 flex min-h-8 flex-wrap items-center gap-3">
              <Button size="sm" variant="primary" onClick={openCreate}>
                <Plus size={14} />
                新建分层
              </Button>
              <RadioGroup
                aria-label="数仓分层展示方式"
                value={viewMode}
                onValueChange={(value) => setViewMode(value as DwLayerDisplayMode)}
                className="flex items-center gap-4"
              >
                {DW_LAYER_DISPLAY_OPTIONS.map((option) => (
                  <label
                    key={option.value}
                    htmlFor={`dw-layer-view-${option.value}`}
                    className="inline-flex cursor-pointer items-center gap-2 text-xs text-[var(--yak-components-panel-text)]"
                  >
                    <Radio
                      id={`dw-layer-view-${option.value}`}
                      value={option.value}
                    />
                    <span>{option.label}</span>
                  </label>
                ))}
              </RadioGroup>
            </div>

            {listQuery.isPending ? (
              <div className="flex min-h-[220px] items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
                <LoaderCircle size={15} className="animate-spin" />
                正在加载数仓分层...
              </div>
            ) : listQuery.isError ? (
              <div className="flex min-h-[220px] flex-col items-center justify-center gap-3 text-xs text-[var(--yak-components-muted-text)]">
                <span>数仓分层加载失败，请检查建模服务或当前工作空间权限。</span>
                <Button size="sm" variant="secondary" onClick={() => void listQuery.refetch()}>
                  重试
                </Button>
              </div>
            ) : records.length === 0 ? (
              <div className="flex min-h-[220px] flex-col items-center justify-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
                <Layers3 size={26} strokeWidth={1.4} />
                <span>暂无数仓分层，点击“新建分层”开始配置</span>
              </div>
            ) : viewMode === "flat" ? (
              <div
                className="grid gap-5 pb-2"
                style={{ gridTemplateColumns: "repeat(auto-fill, minmax(220px, 240px))" }}
              >
                {flatRecords.map((record) => (
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
              <div className="space-y-1 pb-2">
                {groups.map((group, groupIndex) => (
                  <section key={group.value}>
                    <div className={`mb-3 flex h-6 items-center ${groupIndex === 0 ? "mt-2" : "mt-5"}`}>
                      <span className="h-5 w-1 shrink-0 bg-[#5664ff]" />
                      <h2 className="min-w-0 px-2 text-sm font-medium leading-6 text-[var(--yak-components-panel-text)]">
                        {group.label}
                      </h2>
                    </div>
                    <div
                      className="grid gap-5"
                      style={{ gridTemplateColumns: "repeat(auto-fill, minmax(220px, 240px))" }}
                    >
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
          </main>
        </>
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
