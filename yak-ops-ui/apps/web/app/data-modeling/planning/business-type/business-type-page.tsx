import { useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Building2, ChevronDown, ChevronRight, Folder, FolderOpen, Pencil, Plus, PlusSquare,
  RefreshCw, Search, Trash2, X,
} from "lucide-react";
import {
  Button, Checkbox, Input, Modal, Popover, PopoverContent, PopoverTrigger,
  Select, SelectContent, SelectItem, SelectItemIndicator, SelectItemText, SelectTrigger, SelectValue,
  Table, Textarea, toast, type TableColumns,
} from "@yak-ops/yak-ui";

import {
  modelingBusinessTypeApi, modelingEntityApi, type BusinessTypeTreeNode, type ModelingRecord,
} from "../../service";

interface Props { projectCode: string; }
interface BusinessTypeRecord extends ModelingRecord {
  id?: string; businessName?: string; enShortName?: string; reviewer?: string;
  director?: string; parentId?: string; remark?: string; createBy?: string;
  createTime?: string; updateBy?: string; updateTime?: string;
}
interface DomainRecord extends ModelingRecord {
  id?: string; cnName?: string; enName?: string; enShortName?: string; remark?: string;
}
interface DataMartRecord extends ModelingRecord {
  id?: string; martName?: string; enShortName?: string;
  martType?: string | { code?: string; name?: string };
  businessTypeId?: string; director?: string; remark?: string; createTime?: string;
}
interface BusinessTypeFormValues {
  businessName: string; enShortName: string; reviewer: string; director: string; remark: string;
}
const EMPTY_FORM: BusinessTypeFormValues = {
  businessName: "", enShortName: "", reviewer: "", director: "", remark: "",
};
const CARD = "mb-4 rounded-md bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,0.08)]";
const TITLE = "mb-4 flex items-center gap-2 text-sm font-semibold text-[var(--yak-components-panel-text)]";
const display = (value?: string) => value && value.trim() ? value : "-";

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

function TreeNodeRow({
  node, depth, selectedId, expandedKeys, onToggle, onSelect, onContextMenu,
}: {
  node: BusinessTypeTreeNode; depth: number; selectedId: string; expandedKeys: Set<string>;
  onToggle: (key: string) => void; onSelect: (node: BusinessTypeTreeNode) => void;
  onContextMenu: (event: ReactMouseEvent, node: BusinessTypeTreeNode) => void;
}) {
  const key = node.key || node.value;
  const hasChildren = Boolean(node.children?.length);
  const expanded = expandedKeys.has(key);
  const selected = selectedId === key;
  return (
    <div>
      <div
        role="treeitem" aria-selected={selected} aria-expanded={hasChildren ? expanded : undefined}
        onContextMenu={(event) => onContextMenu(event, node)}
        onClick={() => onSelect(node)}
        style={{ paddingLeft: 10 + depth * 18 }}
        className={[
          "flex min-h-8 cursor-pointer items-center gap-1 rounded-md pr-2 text-[13px] transition-colors",
          selected ? "bg-[var(--yak-color-primary-subtle)] font-medium text-[var(--yak-color-primary)]" : "text-[#3a3d51] hover:bg-[var(--yak-color-hover)]",
        ].join(" ")}
      >
        <button
          type="button" className="flex size-4 shrink-0 cursor-pointer items-center justify-center text-[#98a0aa]"
          aria-label={hasChildren ? (expanded ? "收起" : "展开") : "无子级"}
          onClick={(event) => { event.stopPropagation(); if (hasChildren) onToggle(key); }}
        >
          {hasChildren ? (expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />) : <span className="size-1 rounded-full bg-[#c4c9d1]" />}
        </button>
        {hasChildren ? (expanded ? <FolderOpen size={14} className="shrink-0 text-[#8b929e]" /> : <Folder size={14} className="shrink-0 text-[#8b929e]" />) : <Building2 size={14} className="shrink-0 text-[#8b929e]" />}
        <span className="min-w-0 flex-1 truncate">{node.title}</span>
      </div>
      {hasChildren && expanded ? (
        <div role="group">
          {node.children?.map((child) => (
            <TreeNodeRow key={child.key || child.value} node={child} depth={depth + 1}
              selectedId={selectedId} expandedKeys={expandedKeys} onToggle={onToggle}
              onSelect={onSelect} onContextMenu={onContextMenu} />
          ))}
        </div>
      ) : null}
    </div>
  );
}

function BusinessTypeFormDialog({
  open, record, parentId, saving, onClose, onSubmit,
}: {
  open: boolean; record?: BusinessTypeRecord; parentId?: string; saving: boolean;
  onClose: () => void; onSubmit: (values: BusinessTypeFormValues) => void;
}) {
  const [values, setValues] = useState<BusinessTypeFormValues>(EMPTY_FORM);
  const [error, setError] = useState("");
  useEffect(() => {
    setValues({
      businessName: record?.businessName || "", enShortName: record?.enShortName || "",
      reviewer: record?.reviewer || "", director: record?.director || "", remark: record?.remark || "",
    });
    setError("");
  }, [record, open]);
  const update = (key: keyof BusinessTypeFormValues, value: string) => {
    setValues((current) => ({ ...current, [key]: value }));
    if (error) setError("");
  };
  const submit = () => {
    if (!values.businessName.trim()) return setError("请输入中文名");
    if (values.businessName.trim().length > 64) return setError("中文名不能超过 64 个字符");
    if (!values.enShortName.trim()) return setError("请输入英文缩写");
    if (values.enShortName.trim().length > 64) return setError("英文缩写不能超过 64 个字符");
    if (!values.director.trim()) return setError("请输入负责人");
    if (values.director.trim().length > 32) return setError("负责人不能超过 32 个字符");
    if (values.reviewer.length > 32) return setError("模型评审人不能超过 32 个字符");
    if (values.remark.length > 2048) return setError("备注不能超过 2048 个字符");
    onSubmit({
      businessName: values.businessName.trim(), enShortName: values.enShortName.trim(),
      reviewer: values.reviewer.trim(), director: values.director.trim(), remark: values.remark.trim(),
    });
  };
  const field = (label: string, key: keyof BusinessTypeFormValues, required = false, multiline = false) => (
    <label className="grid grid-cols-[112px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-2 text-right text-xs text-[var(--yak-components-muted-text)]">
        {required ? <span className="mr-1 text-[#d92d20]">*</span> : null}{label}
      </span>
      {multiline ? (
        <Textarea size="small" variant="outlined" rows={3} value={values[key]}
          onValueChange={(value) => update(key, String(value))} placeholder={label} />
      ) : (
        <Input size="small" variant="outlined" value={values[key]}
          onValueChange={(value) => update(key, String(value))} placeholder={label} />
      )}
    </label>
  );
  return (
    <Modal open={open} centered width={680} title={record?.id ? "编辑业务分类" : "新增业务分类"}
      onClose={onClose}
      footer={<>
        <Button size="sm" variant="secondary" isDisabled={saving} onClick={onClose}>取消</Button>
        <Button size="sm" variant="primary" isPending={saving} onClick={submit}>确定</Button>
      </>}>
      <div className="space-y-4">
        {field("英文缩写", "enShortName", true)}
        {field("中文名", "businessName", true)}
        {field("模型评审人", "reviewer")}
        {field("负责人", "director", true)}
        {field("备注", "remark", false, true)}
        {parentId && !record?.id ? <div className="pl-[124px] text-[11px] text-[var(--yak-components-muted-text)]">将作为所选业务分类的子级创建</div> : null}
        {error ? <div role="alert" className="pl-[124px] text-xs text-[#d92d20]">{error}</div> : null}
      </div>
    </Modal>
  );
}

function BusinessTypeInfo({ record, onEdit }: { record: BusinessTypeRecord; onEdit: () => void }) {
  const items = [
    ["英文缩写", record.enShortName], ["业务名称", record.businessName],
    ["创建人", record.createBy], ["创建时间", record.createTime],
    ["修改人", record.updateBy], ["修改时间", record.updateTime],
    ["模型评审人", record.reviewer], ["负责人", record.director],
  ];
  return (
    <section className={CARD}>
      <div className="mb-4 flex items-center justify-between">
        <h3 className={TITLE.replace("mb-4 ", "")}><span className="h-4 w-1 rounded-full bg-[var(--yak-color-primary)]" />基本信息</h3>
        <Button size="sm" variant="ghost" className="text-xs" onClick={onEdit}><Pencil size={13} />编辑</Button>
      </div>
      <div className="grid grid-cols-1 gap-x-8 gap-y-3 sm:grid-cols-2">
        {items.map(([label, value]) => (
          <div key={label} className="grid grid-cols-[84px_minmax(0,1fr)] gap-2 text-xs">
            <span className="text-[var(--yak-components-muted-text)]">{label}</span>
            <span className="break-words text-[var(--yak-components-panel-text)]">{display(value)}</span>
          </div>
        ))}
        <div className="grid grid-cols-[84px_minmax(0,1fr)] gap-2 text-xs sm:col-span-2">
          <span className="text-[var(--yak-components-muted-text)]">备注</span>
          <span className="whitespace-pre-wrap break-words text-[var(--yak-components-panel-text)]">{display(record.remark)}</span>
        </div>
      </div>
    </section>
  );
}

function BusinessTypeChildren({
  projectCode, parent, onAdd, onEdit, onDelete,
}: {
  projectCode: string; parent: BusinessTypeRecord; onAdd: () => void;
  onEdit: (record: BusinessTypeRecord) => void; onDelete: (record: BusinessTypeRecord) => void;
}) {
  const [pageNo, setPageNo] = useState(1);
  const pageSize = 10;
  const query = useQuery({
    queryKey: ["modeling", "business-type", "children", projectCode, parent.id, pageNo],
    queryFn: () => modelingEntityApi.page<BusinessTypeRecord>(projectCode, "businessType", { id: parent.id, pageNo, pageSize }),
    enabled: Boolean(projectCode && parent.id),
  });
  useEffect(() => setPageNo(1), [parent.id]);
  const columns: TableColumns<BusinessTypeRecord> = [
    { key: "businessName", title: "业务名称", dataIndex: "businessName", width: 220 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 180 },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 190 },
    { key: "remark", title: "备注" },
    { key: "actions", title: "操作", width: 116, align: "center", render: (_v, record) => (
      <div className="flex items-center justify-center gap-1">
        <Button size="sm" variant="ghost" className="px-1 text-xs" onClick={() => onEdit(record)}>编辑</Button>
        <span className="h-3 w-px bg-[#e4e7ec]" />
        <Button size="sm" variant="ghost" className="px-1 text-xs text-[#d92d20]" onClick={() => onDelete(record)}>删除</Button>
      </div>
    )},
  ];
  return (
    <section className={CARD}>
      <div className="mb-3 flex items-center justify-between">
        <h3 className={TITLE.replace("mb-4 ", "")}><span className="h-4 w-1 rounded-full bg-[var(--yak-color-primary)]" />子业务分类</h3>
        <Button size="sm" variant="primary" onClick={onAdd}><Plus size={13} />新建子业务分类</Button>
      </div>
      <Table<BusinessTypeRecord> columns={columns} dataSource={query.data?.totalList || []}
        rowKey={(record) => record.id || JSON.stringify(record)} loading={query.isPending || query.isFetching}
        bordered size="small" emptyText="暂无子业务分类"
        pagination={(query.data?.total || 0) > 0 ? {
          current: pageNo, pageSize, total: query.data?.total || 0,
          disabled: query.isFetching, onChange: (page) => setPageNo(page),
        } : false} />
    </section>
  );
}

function BusinessTypeDomainRelation({ projectCode, businessTypeId }: { projectCode: string; businessTypeId: string }) {
  const queryClient = useQueryClient();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const domainsQuery = useQuery({
    queryKey: ["modeling", "business-type", "all-domains", projectCode],
    queryFn: () => modelingEntityApi.all<DomainRecord>(projectCode, "dataDomain"),
    enabled: Boolean(projectCode),
  });
  const relationKey = ["modeling", "business-type", "related-domains", projectCode, businessTypeId];
  const relatedQuery = useQuery({
    queryKey: relationKey,
    queryFn: () => modelingBusinessTypeApi.selectRelatedDomains<DomainRecord>(projectCode, businessTypeId),
    enabled: Boolean(projectCode && businessTypeId),
  });
  const related = relatedQuery.data || [];
  const relatedIds = useMemo(() => new Set(related.map((item) => item.id).filter(Boolean) as string[]), [related]);
  useEffect(() => setSelectedIds(Array.from(relatedIds)), [relatedIds]);

  const addSelected = async () => {
    const newIds = selectedIds;
    if (!newIds.length) { setPickerOpen(false); return; }
    setSaving(true);
    try {
      await modelingBusinessTypeApi.updateRelatedDomains(projectCode, businessTypeId, newIds);
      toast.success("数据域关联已更新");
      await queryClient.invalidateQueries({ queryKey: relationKey });
      setPickerOpen(false);
    } catch {
      toast.warning("关联数据域失败，请刷新后重试");
    } finally { setSaving(false); }
  };
  const removeDomain = async (id: string) => {
    try {
      await modelingBusinessTypeApi.deleteRelatedDomain(projectCode, businessTypeId, id);
      toast.success("数据域已解除关联");
      await queryClient.invalidateQueries({ queryKey: relationKey });
    } catch { toast.warning("解除关联失败，请刷新后重试"); }
  };
  const loadAll = async () => {
    setSaving(true);
    try {
      await modelingBusinessTypeApi.loadAllDomains(projectCode, businessTypeId);
      toast.success("已加载全部数据域");
      await queryClient.invalidateQueries({ queryKey: relationKey });
    } catch { toast.warning("加载全部数据域失败，请重试"); }
    finally { setSaving(false); }
  };
  const columns: TableColumns<DomainRecord> = [
    { key: "cnName", title: "中文名", dataIndex: "cnName", width: 180 },
    { key: "enName", title: "英文名", dataIndex: "enName", width: 180 },
    { key: "enShortName", title: "英文缩写", dataIndex: "enShortName", width: 140 },
    { key: "remark", title: "备注" },
    { key: "actions", title: "操作", width: 88, align: "center", render: (_v, record) => (
      <Button size="sm" variant="ghost" className="text-xs text-[var(--yak-components-muted-text)] hover:text-[#d92d20]"
        onClick={() => record.id && void removeDomain(record.id)}>删除</Button>
    )},
  ];
  return (
    <section className={CARD}>
      <h3 className={TITLE}><span className="h-4 w-1 rounded-full bg-[var(--yak-color-primary)]" />关联数据域</h3>
      <div className="mb-2 text-xs text-[var(--yak-components-muted-text)]">可选数据域：</div>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Popover open={pickerOpen} onOpenChange={(open) => {
          setPickerOpen(open);
          if (open) setSelectedIds([]);
        }}>
          <PopoverTrigger className="inline-flex h-7 min-w-[260px] cursor-pointer items-center justify-between rounded-md border border-[var(--yak-components-input-border)] bg-white px-2.5 text-xs text-[var(--yak-components-muted-text)] hover:border-[var(--yak-color-primary)]">
            请选择数据域<ChevronDown size={13} />
          </PopoverTrigger>
          <PopoverContent align="start" className="w-[300px] p-0">
            <div className="max-h-64 overflow-y-auto p-3">
              {domainsQuery.isPending ? (
                <div className="py-4 text-center text-xs text-[var(--yak-components-muted-text)]">正在加载数据域...</div>
              ) : (domainsQuery.data || []).filter((domain) => domain.id && !relatedIds.has(domain.id)).length === 0 ? (
                <div className="py-4 text-center text-xs text-[var(--yak-components-muted-text)]">暂无可关联的数据域</div>
              ) : (
                <div className="space-y-2">
                  {(domainsQuery.data || []).filter((domain) => domain.id && !relatedIds.has(domain.id)).map((domain) => {
                    const id = domain.id || "";
                    return (
                      <label key={id} className="flex cursor-pointer items-center gap-2 text-xs">
                        <Checkbox checked={selectedIds.includes(id)} onCheckedChange={(checked) => setSelectedIds((current) =>
                          checked ? Array.from(new Set([...current, id])) : current.filter((item) => item !== id),
                        )} />
                        <span>{domain.cnName} ({domain.enShortName || domain.enName || "-"})</span>
                      </label>
                    );
                  })}
                </div>
              )}
            </div>
            <div className="flex items-center justify-between border-t border-[var(--yak-components-panel-border)] px-3 py-2">
              <span className="text-[11px] text-[var(--yak-components-muted-text)]">已选 {selectedIds.length} 项</span>
              <Button size="sm" variant="primary" isPending={saving} onClick={() => void addSelected()}>确定</Button>
            </div>
          </PopoverContent>
        </Popover>
        <Button size="sm" variant="ghost" className="text-xs text-[var(--yak-color-primary)]"
          isDisabled={saving} onClick={() => void loadAll()}>一键加载全部数据域</Button>
      </div>
      <div className="mb-2 text-xs text-[var(--yak-components-muted-text)]">已选数据域：</div>
      <Table<DomainRecord> columns={columns} dataSource={related} rowKey={(record) => record.id || JSON.stringify(record)}
        loading={relatedQuery.isPending || relatedQuery.isFetching} bordered size="small" emptyText="暂无关联数据域" />
    </section>
  );
}

function DataMartManagement({ projectCode, businessTypeId }: { projectCode: string; businessTypeId: string }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<DataMartRecord>();
  const [values, setValues] = useState({ martName: "", enShortName: "", martType: "", director: "", remark: "" });
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<DataMartRecord>();
  const queryKey = ["modeling", "business-type", "data-marts", projectCode, businessTypeId];
  const martsQuery = useQuery({
    queryKey,
    queryFn: async () => (await modelingEntityApi.all<DataMartRecord>(projectCode, "dataMart"))
      .filter((item) => item.businessTypeId === businessTypeId),
    enabled: Boolean(projectCode && businessTypeId),
  });
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
      await modelingEntityApi.update(projectCode, "dataMart", {
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
      await modelingEntityApi.remove(projectCode, "dataMart", pendingDelete.id);
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
                <Select size="small" items={[{ value: "PUBLIC", label: "公共集市" }, { value: "BUSINESS", label: "业务集市" }, { value: "APPLICATION", label: "数据应用集市" }]} value={values.martType || undefined}
                  onValueChange={(value) => setValues((current) => ({ ...current, martType: String(value || "") }))}>
                  <SelectTrigger variant="outlined"><SelectValue placeholder="请选择集市类型" /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="PUBLIC"><SelectItemText>公共集市</SelectItemText><SelectItemIndicator /></SelectItem>
                    <SelectItem value="BUSINESS"><SelectItemText>业务集市</SelectItemText><SelectItemIndicator /></SelectItem>
                    <SelectItem value="APPLICATION"><SelectItemText>数据应用集市</SelectItemText><SelectItemIndicator /></SelectItem>
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

export function DataModelingBusinessTypePage({ projectCode }: Props) {
  const queryClient = useQueryClient();
  const splitRef = useRef<HTMLDivElement | null>(null);
  const previousKeywordRef = useRef("");
  const [leftWidth, setLeftWidth] = useState(280);
  const [resizing, setResizing] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [selectedId, setSelectedId] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; node: BusinessTypeTreeNode }>();
  const [formOpen, setFormOpen] = useState(false);
  const [formRecord, setFormRecord] = useState<BusinessTypeRecord>();
  const [formParentId, setFormParentId] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<BusinessTypeRecord>();
  const treeQuery = useQuery({
    queryKey: ["modeling", "business-type", "tree", projectCode, keyword],
    queryFn: () => modelingBusinessTypeApi.tree(projectCode, keyword),
    enabled: Boolean(projectCode),
  });
  const detailQuery = useQuery({
    queryKey: ["modeling", "business-type", "detail", projectCode, selectedId],
    queryFn: () => modelingEntityApi.getById<BusinessTypeRecord>(projectCode, "businessType", selectedId),
    enabled: Boolean(projectCode && selectedId),
  });
  const treeData = treeQuery.data || [];

  useEffect(() => {
    if (treeQuery.isPending || treeQuery.isFetching) return;

    const previousKeyword = previousKeywordRef.current.trim();
    const currentKeyword = keyword.trim();
    if (currentKeyword || previousKeyword) {
      setExpandedKeys(collectExpandedBusinessTypeKeys(treeData));
    } else {
      setExpandedKeys((current) => current.size ? current : collectExpandedBusinessTypeKeys(treeData));
    }
    previousKeywordRef.current = keyword;
  }, [treeData, keyword, treeQuery.isFetching, treeQuery.isPending]);

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
    const queryKeys = ["tree", "detail", "children", "all-domains", "related-domains", "data-marts"] as const;
    await Promise.all(queryKeys.map((key) => queryClient.invalidateQueries({
      queryKey: ["modeling", "business-type", key, projectCode],
    })));
  };
  const toggleExpanded = (key: string) => setExpandedKeys((current) => {
    const next = new Set(current);
    if (next.has(key)) next.delete(key); else next.add(key);
    return next;
  });
  const openCreate = (parentId?: string) => {
    setFormRecord(undefined);
    setFormParentId(parentId);
    if (parentId) setExpandedKeys((current) => new Set([...current, parentId]));
    setFormOpen(true);
    setContextMenu(undefined);
  };
  const openEdit = async (id: string) => {
    setContextMenu(undefined);
    try {
      const record = await modelingEntityApi.getById<BusinessTypeRecord>(projectCode, "businessType", id);
      setFormRecord(record);
      setFormParentId(record.parentId);
      setFormOpen(true);
    } catch { toast.warning("获取业务分类详情失败，请刷新后重试"); }
  };
  const saveBusinessType = async (values: BusinessTypeFormValues) => {
    if (saving) return;
    setSaving(true);
    const payload: Record<string, unknown> = { ...values, ...(formParentId ? { parentId: formParentId } : {}) };
    try {
      if (formRecord?.id) {
        await modelingEntityApi.update(projectCode, "businessType", { ...payload, id: formRecord.id });
        toast.success("业务分类已更新");
      } else {
        await modelingEntityApi.create(projectCode, "businessType", payload);
        toast.success("业务分类已创建");
      }
      setFormOpen(false);
      setFormRecord(undefined);
      await queryClient.invalidateQueries({ queryKey: ["modeling", "business-type", "tree", projectCode] });
      if (selectedId) {
        await queryClient.invalidateQueries({ queryKey: ["modeling", "business-type", "detail", projectCode, selectedId] });
        await queryClient.invalidateQueries({ queryKey: ["modeling", "business-type", "children", projectCode, selectedId] });
      }
    } catch { toast.warning("保存业务分类失败，请检查填写内容后重试"); }
    finally { setSaving(false); }
  };
  const removeBusinessType = async () => {
    if (!pendingDelete?.id) return;
    try {
      await modelingEntityApi.remove(projectCode, "businessType", pendingDelete.id);
      toast.success("业务分类已删除");
      if (selectedId === pendingDelete.id) setSelectedId("");
      setPendingDelete(undefined);
      await queryClient.invalidateQueries({ queryKey: ["modeling", "business-type", "tree", projectCode] });
      await queryClient.invalidateQueries({ queryKey: ["modeling", "business-type", "children", projectCode] });
    } catch { toast.warning("删除业务分类失败，请先检查子级分类、关联数据域和数据集市"); }
  };
  const contextAction = (action: "add" | "edit" | "delete") => {
    const node = contextMenu?.node;
    if (!node) return;
    const id = node.key || node.value;
    if (action === "add") openCreate(id);
    if (action === "edit") void openEdit(id);
    if (action === "delete") {
      setPendingDelete({ id, businessName: node.title });
      setContextMenu(undefined);
    }
  };

  const selectedRecord = detailQuery.data;
  return (
    <div ref={splitRef} className="relative flex h-full min-h-[620px] min-w-0 flex-1 overflow-hidden border-t border-[var(--yak-components-panel-border)] bg-white"
      onClick={() => contextMenu && setContextMenu(undefined)}>
      <aside className="flex min-h-0 shrink-0 flex-col overflow-hidden bg-white" style={{ width: leftWidth }}>
        <div className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3">
          <span className="truncate text-sm font-semibold text-[var(--yak-components-panel-text)]">业务分类</span>
          <div className="flex items-center gap-1">
            <button type="button" title="新建一级业务分类" className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]" onClick={() => openCreate()}>
              <PlusSquare size={16} />
            </button>
            <button type="button" title="刷新" className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)]" onClick={() => void refresh()}>
              <RefreshCw size={15} className={treeQuery.isFetching ? "animate-spin" : ""} />
            </button>
          </div>
        </div>
        <div className="shrink-0 px-3 py-2">
          <div className="relative">
            <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]" />
            <Input size="small" variant="outlined" value={keyword} className="pl-8 pr-8" placeholder="搜索"
              onValueChange={(value) => setKeyword(String(value))} />
            {keyword ? <button type="button" aria-label="清空搜索" className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer text-[var(--yak-components-muted-text)]" onClick={() => setKeyword("")}><X size={13} /></button> : null}
          </div>
        </div>
        <div role="tree" className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
          {treeQuery.isPending ? (
            <div className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">正在加载业务分类...</div>
          ) : treeQuery.isError ? (
            <div className="px-2 py-6 text-center text-xs text-[var(--yak-components-muted-text)]">
              <p>业务分类加载失败</p>
              <Button size="sm" variant="secondary" className="mt-2" onClick={() => void treeQuery.refetch()}>重试</Button>
            </div>
          ) : treeData.length === 0 ? (
            <div className="px-2 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">{keyword ? "没有找到匹配的业务分类" : "暂无业务分类，点击右上角新建"}</div>
          ) : treeData.map((node) => (
            <TreeNodeRow key={node.key || node.value} node={node} depth={0} selectedId={selectedId}
              expandedKeys={expandedKeys} onToggle={toggleExpanded} onSelect={(item) => setSelectedId(item.key || item.value)}
              onContextMenu={(event, item) => {
                event.preventDefault(); event.stopPropagation();
                setContextMenu({ x: event.clientX, y: event.clientY, node: item });
              }} />
          ))}
        </div>
      </aside>

      <div role="separator" aria-orientation="vertical" aria-label="调整业务分类树宽度"
        className={["relative z-10 w-1 shrink-0 cursor-col-resize touch-none bg-[var(--yak-components-panel-border)] transition-colors hover:bg-[var(--yak-color-primary)]", resizing ? "bg-[var(--yak-color-primary)]" : ""].join(" ")}
        onPointerDown={(event) => { event.preventDefault(); setResizing(true); }}>
        <span className="absolute inset-y-0 -left-1 -right-1" />
      </div>

      <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#f6f6f6]">
        {selectedId ? (
          detailQuery.isPending ? (
            <div className="flex min-h-0 flex-1 items-center justify-center bg-white text-xs text-[var(--yak-components-muted-text)]">正在加载业务分类详情...</div>
          ) : detailQuery.isError ? (
            <div className="flex min-h-0 flex-1 flex-col items-center justify-center gap-2 bg-white text-xs text-[var(--yak-components-muted-text)]">
              <span>业务分类详情加载失败</span>
              <Button size="sm" variant="secondary" onClick={() => void detailQuery.refetch()}>重试</Button>
            </div>
          ) : selectedRecord ? (
            <>
              <header className="flex h-[72px] shrink-0 items-center border-b border-[var(--yak-components-panel-border)] bg-white px-5">
                <Building2 size={20} className="mr-2 text-[var(--yak-color-primary)]" />
                <h1 className="truncate text-lg font-semibold text-[var(--yak-components-panel-text)]">{selectedRecord.businessName}</h1>
              </header>
              <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
                <BusinessTypeInfo record={selectedRecord} onEdit={() => void openEdit(selectedRecord.id || "")} />
                <BusinessTypeChildren projectCode={projectCode} parent={selectedRecord}
                  onAdd={() => openCreate(selectedRecord.id)}
                  onEdit={(record) => void openEdit(record.id || "")} onDelete={setPendingDelete} />
                <BusinessTypeDomainRelation projectCode={projectCode} businessTypeId={selectedRecord.id || ""} />
                <DataMartManagement projectCode={projectCode} businessTypeId={selectedRecord.id || ""} />
              </div>
            </>
          ) : null
        ) : (
          <div className="flex min-h-0 flex-1 items-center justify-center bg-white">
            <div className="flex flex-col items-center gap-2 text-center">
              <Building2 size={32} strokeWidth={1.3} className="text-[#c5cbd3]" />
              <span className="text-sm text-[var(--yak-components-muted-text)]">请选择左侧业务分类查看详情</span>
            </div>
          </div>
        )}
      </main>

      {contextMenu ? (
        <div className="fixed z-[70] min-w-40 rounded-lg border border-[var(--yak-components-panel-border)] bg-white p-1 shadow-[var(--yak-components-panel-shadow)]"
          style={{ left: contextMenu.x, top: contextMenu.y }} onClick={(event) => event.stopPropagation()}>
          <button type="button" className="flex h-8 w-full cursor-pointer items-center gap-2 rounded px-2.5 text-left text-xs hover:bg-[var(--yak-color-hover)]" onClick={() => contextAction("add")}><Plus size={13} />新增子业务分类</button>
          <button type="button" className="flex h-8 w-full cursor-pointer items-center gap-2 rounded px-2.5 text-left text-xs hover:bg-[var(--yak-color-hover)]" onClick={() => contextAction("edit")}><Pencil size={13} />编辑</button>
          <button type="button" className="flex h-8 w-full cursor-pointer items-center gap-2 rounded px-2.5 text-left text-xs text-[#d92d20] hover:bg-[var(--yak-color-hover)]" onClick={() => contextAction("delete")}><Trash2 size={13} />删除</button>
        </div>
      ) : null}

      <BusinessTypeFormDialog open={formOpen} record={formRecord} parentId={formParentId} saving={saving}
        onClose={() => { if (!saving) { setFormOpen(false); setFormRecord(undefined); } }}
        onSubmit={(values) => void saveBusinessType(values)} />
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
