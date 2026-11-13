import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button, Field, FieldLabel, Input, Modal, Select, SelectContent, SelectItem,
  SelectItemIndicator, SelectItemText, SelectTrigger, SelectValue, Table, Textarea,
  toast, type TableColumns,
} from "@yak-ops/yak-ui";
import { Plus, RefreshCw, Search } from "lucide-react";

import { modelingEntityApi, type ModelingRecord, type ModelingResourceKey } from "../service";

export interface ModelingFieldOption { value: string; label: string; }

export interface ModelingFieldDefinition {
  key: string;
  label: string;
  placeholder?: string;
  required?: boolean;
  multiline?: boolean;
  options?: ModelingFieldOption[];
  optionsSource?: ModelingResourceKey;
  optionLabelField?: string;
  optionValueField?: string;
}

export interface ModelingColumnDefinition {
  key: string;
  title: string;
  width?: number;
  render?: (value: unknown, record: ModelingRecord) => ReactNode;
}

export interface ModelingEntityDefinition {
  key: ModelingResourceKey;
  title: string;
  description: string;
  searchField: string;
  searchPlaceholder: string;
  fields: ModelingFieldDefinition[];
  columns: ModelingColumnDefinition[];
}

type Props = { projectCode: string; definition: ModelingEntityDefinition };
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

const readValue = (record: ModelingRecord | undefined, key: string) => {
  const value = record?.[key];
  return value === undefined || value === null ? "" : String(value);
};

export function ModelingEntityPage({ projectCode, definition }: Props) {
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ModelingRecord>();
  const [formValues, setFormValues] = useState<Record<string, string>>({});
  const [openingId, setOpeningId] = useState("");
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<ModelingRecord>();
  const [deleting, setDeleting] = useState(false);
  const [formError, setFormError] = useState("");

  const pageQueryKey = useMemo(
    () => ["modeling", "planning-page", projectCode, definition.key, pageNo, pageSize, keyword],
    [projectCode, definition.key, pageNo, pageSize, keyword],
  );
  const optionSources = useMemo(
    () => [...new Set(definition.fields.flatMap((field) => field.optionsSource ? [field.optionsSource] : []))],
    [definition.fields],
  );
  const optionQueries = useQueries({
    queries: optionSources.map((resource) => ({
      queryKey: ["modeling", "options", projectCode, resource],
      queryFn: () => modelingEntityApi.all<ModelingRecord>(projectCode, resource),
      enabled: Boolean(projectCode && formOpen),
    })),
  });

  const pageQuery = useQuery({
    queryKey: pageQueryKey,
    queryFn: () => modelingEntityApi.page<ModelingRecord>(projectCode, definition.key, {
      pageNo,
      pageSize,
      [definition.searchField]: keyword.trim() || undefined,
    }),
    enabled: Boolean(projectCode),
  });

  useEffect(() => setPageNo(1), [keyword, definition.key]);

  const records = pageQuery.data?.bizData ?? [];
  const total = pageQuery.data?.pagination?.total ?? 0;

  const getFieldOptions = (field: ModelingFieldDefinition): ModelingFieldOption[] => {
    if (field.options) return field.options;
    if (!field.optionsSource) return [];
    const sourceIndex = optionSources.indexOf(field.optionsSource);
    const sourceRecords = optionQueries[sourceIndex]?.data ?? [];
    return sourceRecords.map((record) => ({
      value: String(record[field.optionValueField || "id"] ?? ""),
      label: String(record[field.optionLabelField || "cnName"] ?? record[field.optionValueField || "id"] ?? ""),
    })).filter((option) => option.value
      && !(field.optionsSource === definition.key && editing?.id === option.value));
  };

  const openCreate = () => {
    setEditing(undefined);
    setFormValues({});
    setFormError("");
    setFormOpen(true);
  };

  const openEdit = async (record: ModelingRecord) => {
    if (!record.id || openingId) return;
    setOpeningId(record.id);
    try {
      const detail = await modelingEntityApi.getById<ModelingRecord>(projectCode, definition.key, record.id);
      setEditing(detail);
      setFormValues(Object.fromEntries(
        definition.fields.map((field) => [field.key, readValue(detail, field.key)]),
      ));
      setFormError("");
      setFormOpen(true);
    } catch {
      toast.warning("获取详情失败，请刷新列表后重试");
    } finally {
      setOpeningId("");
    }
  };

  const closeForm = () => {
    if (saving) return;
    setFormOpen(false);
    setEditing(undefined);
    setFormError("");
  };

  const save = async () => {
    if (saving) return;
    const required = definition.fields.find(
      (field) => field.required && !String(formValues[field.key] || "").trim(),
    );
    if (required) {
      setFormError(`请填写${required.label}`);
      return;
    }
    const payload: Record<string, unknown> = {};
    definition.fields.forEach((field) => {
      const value = String(formValues[field.key] || "").trim();
      if (value) payload[field.key] = value;
    });
    if (editing?.id) payload.id = editing.id;

    setSaving(true);
    try {
      if (editing?.id) {
        await modelingEntityApi.update(projectCode, definition.key, payload);
        toast.success(`${definition.title}已更新`);
      } else {
        await modelingEntityApi.create(projectCode, definition.key, payload);
        toast.success(`${definition.title}已创建`);
      }
      setFormOpen(false);
      setEditing(undefined);
      await queryClient.invalidateQueries({
        queryKey: ["modeling", "planning-page", projectCode, definition.key],
      });
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
      await modelingEntityApi.remove(projectCode, definition.key, pendingDelete.id);
      toast.success(`${definition.title}已删除`);
      setPendingDelete(undefined);
      await queryClient.invalidateQueries({
        queryKey: ["modeling", "planning-page", projectCode, definition.key],
      });
    } catch {
      toast.warning("删除失败，请确认该记录没有被其他建模对象引用");
    } finally {
      setDeleting(false);
    }
  };

  const columns: TableColumns<ModelingRecord> = [
    {
      key: "sequence", title: "序号", width: 72, align: "center",
      render: (_value, _record, index) => String((pageNo - 1) * pageSize + index + 1).padStart(2, "0"),
    },
    ...definition.columns.map((column) => ({
      key: column.key, title: column.title, dataIndex: column.key, width: column.width, ellipsis: true,
      render: (value: unknown, record: ModelingRecord) =>
        column.render ? column.render(value, record) : value !== undefined && value !== null && value !== "" ? String(value) : "-",
    })),
    {
      key: "updateTime", title: "更新时间", width: 170,
      render: (_value, record) => record.updateTime ? String(record.updateTime) : "-",
    },
    {
      key: "actions", title: "操作", width: 120, align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost"
            className="px-1 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]"
            isDisabled={Boolean(openingId) || saving || deleting}
            onClick={() => void openEdit(record)}>
            {openingId === record.id ? "加载中" : "编辑"}
          </Button>
          <span aria-hidden="true" className="h-3 w-px bg-[#e4e7ec]" />
          <Button size="sm" variant="ghost"
            className="px-1 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[#d92d20]"
            isDisabled={saving || deleting} onClick={() => setPendingDelete(record)}>
            删除
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
        <div className="flex shrink-0 flex-wrap items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-2">
            <Button size="sm" variant="primary" onClick={openCreate}><Plus size={14} />新建{definition.title}</Button>
            <div className="relative w-[280px] max-w-full">
              <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
              <Input size="small" variant="outlined" value={keyword} className="pl-8"
                placeholder={definition.searchPlaceholder} onChange={(event) => setKeyword(event.target.value)} />
            </div>
          </div>
          <Button size="sm" variant="secondary" isDisabled={pageQuery.isFetching}
            onClick={() => void pageQuery.refetch()}>
            <RefreshCw size={14} className={pageQuery.isFetching ? "animate-spin" : ""} />刷新
          </Button>
        </div>

        {pageQuery.isError ? (
          <div role="alert" className="mt-3 flex items-center gap-3 text-xs text-[var(--yak-components-muted-text)]">
            列表加载失败，请检查建模服务和当前工作空间权限。
            <Button size="sm" variant="secondary" onClick={() => void pageQuery.refetch()}>重试</Button>
          </div>
        ) : null}

        <section className="mt-4 min-h-0 flex-1">
          <Table<ModelingRecord>
            className="min-h-full" columns={columns} dataSource={records}
            rowKey={(record) => record.id || JSON.stringify(record)}
            loading={pageQuery.isPending || pageQuery.isFetching} bordered size="medium" scroll={{ x: 1000 }}
            emptyText={keyword ? "没有找到匹配的记录" : "暂无数据，点击“新建”添加第一条记录"}
            pagination={total > 0 ? {
              current: pageNo, pageSize, total, pageSizeOptions: PAGE_SIZE_OPTIONS,
              pageSizeLabel: "每页显示：", showSizeChanger: true,
              disabled: pageQuery.isFetching || saving || deleting,
              onChange: (page, size) => { setPageNo(page); setPageSize(size); },
            } : false}
          />
        </section>
        <div className="mt-2 text-[11px] text-[var(--yak-components-muted-text)]">{definition.description}</div>
      </div>

      <Modal open={formOpen} centered width={620} bodyClassName="py-3"
        title={editing ? `编辑${definition.title}` : `新建${definition.title}`}
        onClose={closeForm}
        footer={
          <>
            <Button size="sm" variant="secondary" isDisabled={saving} onClick={closeForm}>取消</Button>
            <Button size="sm" variant="primary" isPending={saving} onClick={() => void save()}>保存</Button>
          </>
        }>
        <div className="space-y-3">
          {definition.fields.map((field) => {
            const options = getFieldOptions(field);
            const isSelect = Boolean(field.options || field.optionsSource);
            const sourceIndex = field.optionsSource ? optionSources.indexOf(field.optionsSource) : -1;
            const optionsLoading = sourceIndex >= 0 && optionQueries[sourceIndex]?.isFetching;
            return (
              <Field key={field.key} className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
                <FieldLabel required={field.required} className="pt-1.5 text-xs leading-4">{field.label}</FieldLabel>
                <div className="min-w-0">
                  {isSelect ? (
                    <Select size="small" items={options} value={formValues[field.key] || undefined}
                      onValueChange={(value) => {
                        setFormValues((values) => ({ ...values, [field.key]: String(value || "") }));
                        setFormError("");
                      }}>
                      <SelectTrigger variant="outlined">
                        <SelectValue placeholder={optionsLoading ? "正在加载选项..." : field.placeholder || `请选择${field.label}`} />
                      </SelectTrigger>
                      <SelectContent>
                        {options.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            <SelectItemText>{option.label}</SelectItemText><SelectItemIndicator />
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  ) : field.multiline ? (
                    <Textarea size="small" variant="outlined" rows={3} value={formValues[field.key] || ""}
                      placeholder={field.placeholder || `请输入${field.label}`}
                      onValueChange={(value) => {
                        setFormValues((values) => ({ ...values, [field.key]: String(value || "") }));
                        setFormError("");
                      }} />
                  ) : (
                    <Input size="small" variant="outlined" value={formValues[field.key] || ""}
                      placeholder={field.placeholder || `请输入${field.label}`}
                      onChange={(event) => {
                        setFormValues((values) => ({ ...values, [field.key]: event.target.value }));
                        setFormError("");
                      }} />
                  )}
                </div>
              </Field>
            );
          })}
          {formError ? <p role="alert" className="pl-[116px] text-xs text-[#d92d20]">{formError}</p> : null}
        </div>
      </Modal>

      <Modal open={Boolean(pendingDelete)} centered width={420} title={`删除${definition.title}`}
        onClose={() => { if (!deleting) setPendingDelete(undefined); }}
        footer={
          <>
            <Button size="sm" variant="secondary" isDisabled={deleting} onClick={() => setPendingDelete(undefined)}>取消</Button>
            <Button size="sm" variant="danger" isPending={deleting} onClick={() => void remove()}>删除</Button>
          </>
        }>
        <p className="text-sm leading-6 text-[var(--yak-components-muted-text)]">
          确定删除“{String(pendingDelete?.[definition.columns[0]?.key] || pendingDelete?.id || "-")}”吗？
          如果该记录已被其他建模对象引用，删除可能会失败。
        </p>
      </Modal>
    </>
  );
}

export default ModelingEntityPage;
