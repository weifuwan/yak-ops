import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button, Input, Modal, Select, SelectContent, SelectItem, SelectItemIndicator, SelectItemText,
  SelectTrigger, SelectValue, Table, Textarea, toast, type TableColumns,
} from "@yak-ops/yak-ui";

import { dataMartApi } from "@/service/data-modeling/planning/application-layer/data-mart";
import { planningQueryKeys } from "../query-keys";
import { MART_TYPE_OPTIONS, withSelectedOption } from "../select-options";
import type { DataMartRecord } from "./types";

const CARD = "mb-4 rounded-md bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.08)]";
const TITLE = "mb-4 flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";

export function BusinessTypeDataMartManagement({ projectCode, businessTypeId }: { projectCode: string; businessTypeId: string }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<DataMartRecord>();
  const [values, setValues] = useState({ martName: "", enShortName: "", martType: "", director: "", remark: "" });
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<DataMartRecord>();
  const queryKey = planningQueryKeys.businessTypeDataMarts(projectCode, businessTypeId);
  const martsQuery = useQuery({
    queryKey,
    queryFn: async () => (await dataMartApi.all<DataMartRecord>(projectCode))
      .filter((item) => item.businessTypeId === businessTypeId),
    enabled: Boolean(projectCode && businessTypeId),
  });
  const martTypeSelectItems = withSelectedOption(
    MART_TYPE_OPTIONS,
    values.martType,
    editing?.martTypeName || "其他集市类型",
  );
  const openEdit = (record: DataMartRecord) => {
    setEditing(record);
    const martType = typeof record.martType === "string" ? record.martType : record.martType?.code || "";
    setValues({
      martName: record.martName || "", enShortName: record.enShortName || "",
      martType, director: record.director || "", remark: record.remark || "",
    });
  };
  const save = async () => {
    if (!editing?.id || !values.martName.trim()) { toast.warning("请输入集市名称"); return; }
    setSaving(true);
    try {
      await dataMartApi.update(projectCode, {
        id: editing.id, businessTypeId, ...values,
        martName: values.martName.trim(), enShortName: values.enShortName.trim(),
        director: values.director.trim(), remark: values.remark.trim(),
      });
      toast.success("数据集市已更新");
      setEditing(undefined);
      await queryClient.invalidateQueries({ queryKey });
    } catch { toast.warning("数据集市更新失败，请重试"); }
    finally { setSaving(false); }
  };
  const remove = async () => {
    if (!pendingDelete?.id) return;
    try {
      await dataMartApi.remove(projectCode, pendingDelete.id);
      toast.success("数据集市已删除");
      setPendingDelete(undefined);
      await queryClient.invalidateQueries({ queryKey });
    } catch { toast.warning("数据集市删除失败，请先删除下级数据集市或关联主题域"); }
  };
  const columns: TableColumns<DataMartRecord> = [
    { key: "martName", title: "集市名称", dataIndex: "martName", width: 220 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 160 },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 190 },
    { key: "remark", title: "备注" },
    { key: "actions", title: "操作", width: 112, align: "center", render: (_v, record) => (
      <div className="flex items-center justify-center gap-1">
        <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => openEdit(record)}>编辑</Button>
        <span className="h-3 w-px bg-[#e4e7ec]" />
        <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]" onClick={() => setPendingDelete(record)}>删除</Button>
      </div>
    )},
  ];
  return (
    <>
      <section className={CARD}>
        <h3 className={TITLE}><span className="h-4 w-1 rounded-full bg-[var(--yak-color-primary)]" />数据集市管理</h3>
        <Table<DataMartRecord> columns={columns} dataSource={martsQuery.data || []}
          rowKey={(record) => record.id || JSON.stringify(record)} loading={martsQuery.isPending || martsQuery.isFetching}
          bordered size="small" emptyText="暂无数据集市" />
      </section>
      <Modal open={Boolean(editing)} centered width={620} title="编辑数据集市"
        onClose={() => setEditing(undefined)}
        footer={<>
          <Button size="sm" variant="secondary" isDisabled={saving} onClick={() => setEditing(undefined)}>取消</Button>
          <Button size="sm" variant="primary" isPending={saving} onClick={() => void save()}>保存</Button>
        </>}>
        <div className="space-y-3">
          {([
            ["集市名称", "martName"], ["英文缩写", "enShortName"], ["集市类型", "martType"], ["负责人", "director"],
          ] as const).map(([label, key]) => (
            <label key={key} className="grid grid-cols-[100px_minmax(0,1fr)] items-center gap-3">
              <span className="text-right text-xs text-[var(--yak-components-muted-text)]">{label}</span>
              {key === "martType" ? (
                <Select size="small" items={martTypeSelectItems} value={values.martType || undefined}
                  onValueChange={(value) => setValues((current) => ({ ...current, martType: String(value || "") }))}>
                  <SelectTrigger variant="outlined"><SelectValue placeholder="请选择集市类型" /></SelectTrigger>
                  <SelectContent>
                    {MART_TYPE_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        <SelectItemText>{option.label}</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <Input size="small" variant="outlined" value={values[key]}
                  onValueChange={(value) => setValues((current) => ({ ...current, [key]: String(value) }))} />
              )}
            </label>
          ))}
          <label className="grid grid-cols-[100px_minmax(0,1fr)] items-start gap-3">
            <span className="pt-2 text-right text-xs text-[var(--yak-components-muted-text)]">备注</span>
            <Textarea size="small" variant="outlined" rows={3} value={values.remark}
              onValueChange={(value) => setValues((current) => ({ ...current, remark: String(value) }))} />
          </label>
        </div>
      </Modal>
      <Modal open={Boolean(pendingDelete)} centered width={420} title="删除数据集市"
        onClose={() => setPendingDelete(undefined)}
        footer={<>
          <Button size="sm" variant="secondary" onClick={() => setPendingDelete(undefined)}>取消</Button>
          <Button size="sm" variant="danger" onClick={() => void remove()}>删除</Button>
        </>}>
        确定删除“{pendingDelete?.martName || "当前数据集市"}”吗？
      </Modal>
    </>
  );
}

export default BusinessTypeDataMartManagement;
