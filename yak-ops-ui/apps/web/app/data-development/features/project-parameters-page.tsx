import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  Textarea,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";

import { requireActiveProjectCode } from "@/service/workspace/project-scope";
import {
  createProjectParameter,
  deleteProjectParameter,
  queryProjectParameters,
  updateProjectParameter,
  type ProjectParameterRecord,
} from "@/service/project/parameter";
import { OperationsPageShell } from "@/app/operations/components/operations-page-shell";

const PAGE_SIZE = 20;
const DATA_TYPES = {
  VARCHAR: "VARCHAR",
  INTEGER: "INTEGER",
  LONG: "LONG",
  FLOAT: "FLOAT",
  DOUBLE: "DOUBLE",
  BOOLEAN: "BOOLEAN",
  DATE: "DATE",
  DATETIME: "DATETIME",
};

type EditorValue = {
  paramName: string;
  paramValue: string;
  paramDataType: string;
};

const EMPTY_EDITOR: EditorValue = {
  paramName: "",
  paramValue: "",
  paramDataType: "VARCHAR",
};

export function ProjectParametersPage() {
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<ProjectParameterRecord | null>(null);
  const [form, setForm] = useState<EditorValue>(EMPTY_EDITOR);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState<ProjectParameterRecord | null>(null);
  const [deletingPending, setDeletingPending] = useState(false);

  const projectCode = requireActiveProjectCode();
  const query = useQuery({
    queryKey: ["data-development", "project-parameters", projectCode, pageNo, keyword],
    queryFn: ({ signal }) =>
      queryProjectParameters(
        projectCode,
        { pageNo, pageSize: PAGE_SIZE, searchVal: keyword || undefined },
        signal,
      ),
  });

  const openCreate = () => {
    setEditing(null);
    setForm(EMPTY_EDITOR);
    setEditorOpen(true);
  };

  const openEdit = (record: ProjectParameterRecord) => {
    setEditing(record);
    setForm({
      paramName: record.paramName,
      paramValue: record.paramValue ?? "",
      paramDataType: record.paramDataType || "VARCHAR",
    });
    setEditorOpen(true);
  };

  const save = async () => {
    if (!form.paramName.trim()) {
      toast.error("请输入参数名称");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        projectParameterName: form.paramName.trim(),
        projectParameterValue: form.paramValue,
        projectParameterDataType: form.paramDataType || "VARCHAR",
      };
      if (editing) {
        await updateProjectParameter(projectCode, editing.code, payload);
        toast.success("项目参数已更新");
      } else {
        await createProjectParameter(projectCode, payload);
        toast.success("项目参数已创建");
      }
      setEditorOpen(false);
      await query.refetch();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "保存项目参数失败");
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    setDeletingPending(true);
    try {
      await deleteProjectParameter(projectCode, deleting.code);
      toast.success("项目参数已删除");
      setDeleting(null);
      if ((query.data?.totalList?.length ?? 0) <= 1 && pageNo > 1) {
        setPageNo((value) => value - 1);
      } else {
        await query.refetch();
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "删除项目参数失败");
    } finally {
      setDeletingPending(false);
    }
  };

  const columns: TableColumns<ProjectParameterRecord> = [
    {
      key: "paramName",
      title: "参数名称",
      minWidth: 200,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-xs font-medium text-[#30343a]">{record.paramName}</div>
          <div className="mt-0.5 text-[10px] text-[#8a9099]">参数编码 #{record.code}</div>
        </div>
      ),
    },
    {
      key: "paramValue",
      title: "参数值",
      minWidth: 260,
      render: (_value, record) => (
        <span className="block max-w-[460px] truncate text-xs text-[#5b6470]" title={record.paramValue ?? ""}>
          {record.paramValue || <span className="text-[#a0a6af]">空值</span>}
        </span>
      ),
    },
    {
      key: "paramDataType",
      title: "数据类型",
      width: 130,
      render: (_value, record) => (
        <span className="rounded bg-[#f3f5f8] px-2 py-1 text-[10px] text-[#687282]">
          {record.paramDataType || "VARCHAR"}
        </span>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 130,
      fixed: "right",
      align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1">
          <Button size="sm" variant="ghost" onClick={() => openEdit(record)}>
            <Pencil size={13} />编辑
          </Button>
          <Button size="sm" variant="ghost" className="text-[var(--yak-color-danger)]" onClick={() => setDeleting(record)}>
            <Trash2 size={13} />
          </Button>
        </div>
      ),
    },
  ];

  return (
    <OperationsPageShell
      title="项目参数"
      extra={
        <div className="flex items-center gap-2">
          <Button size="sm" variant="secondary" onClick={() => void query.refetch()} isDisabled={query.isFetching}>
            <RefreshCw size={13} />刷新
          </Button>
          <Button size="sm" variant="primary" onClick={openCreate}>
            <Plus size={13} />新建参数
          </Button>
        </div>
      }
    >
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Input
          size="small"
          variant="outlined"
          className="w-[320px] max-w-full"
          placeholder="搜索参数名称或参数值"
          value={keywordInput}
          onChange={(event) => setKeywordInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              setKeyword(keywordInput.trim());
              setPageNo(1);
            }
          }}
        />
        <Button size="sm" variant="secondary" onClick={() => { setKeyword(keywordInput.trim()); setPageNo(1); }}>
          搜索
        </Button>
      </div>

      <div className="min-h-0 flex-1">
        <Table<ProjectParameterRecord>
          className="min-h-full [&_td]:!text-xs"
          columns={columns}
          dataSource={query.data?.totalList ?? []}
          rowKey="code"
          bordered
          size="medium"
          loading={query.isPending || query.isFetching}
          scroll={{ x: 860 }}
          emptyText={query.isError ? "项目参数加载失败" : "暂无项目参数"}
          pagination={
            (query.data?.total ?? 0) > 0
              ? { current: pageNo, pageSize: PAGE_SIZE, total: query.data?.total ?? 0, onChange: setPageNo }
              : false
          }
        />
      </div>

      <Modal
        open={editorOpen}
        centered
        width={520}
        title={editing ? "编辑项目参数" : "新建项目参数"}
        onClose={() => setEditorOpen(false)}
        footer={
          <>
            <Button size="sm" variant="secondary" onClick={() => setEditorOpen(false)} isDisabled={saving}>取消</Button>
            <Button size="sm" variant="primary" onClick={() => void save()} isPending={saving} isDisabled={saving}>
              {saving ? "保存中..." : "保存"}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="mb-1.5 block text-xs font-medium text-[#505866]">参数名称</label>
            <Input size="small" variant="outlined" value={form.paramName} onChange={(event) => setForm((value) => ({ ...value, paramName: event.target.value }))} placeholder="请输入参数名称" />
          </div>
          <div>
            <label className="mb-1.5 block text-xs font-medium text-[#505866]">数据类型</label>
            <Select
              size="small"
              items={DATA_TYPES}
              value={form.paramDataType}
              onValueChange={(value) => setForm((current) => ({ ...current, paramDataType: String(value ?? "VARCHAR") }))}
            >
              <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
              <SelectContent>
                {Object.entries(DATA_TYPES).map(([value, label]) => (
                  <SelectItem key={value} value={value}><SelectItemText>{label}</SelectItemText><SelectItemIndicator /></SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div>
            <label className="mb-1.5 block text-xs font-medium text-[#505866]">参数值</label>
            <Textarea
              size="small"
              variant="outlined"
              rows={4}
              value={form.paramValue}
              onValueChange={(value) => setForm((current) => ({ ...current, paramValue: value }))}
              placeholder="请输入参数值"
            />
          </div>
        </div>
      </Modal>

      <Modal
        open={Boolean(deleting)}
        centered
        width={420}
        title="删除项目参数"
        onClose={() => { if (!deletingPending) setDeleting(null); }}
        footer={
          <>
            <Button size="sm" variant="secondary" onClick={() => setDeleting(null)} isDisabled={deletingPending}>取消</Button>
            <Button size="sm" variant="danger" onClick={() => void confirmDelete()} isPending={deletingPending} isDisabled={deletingPending}>确认删除</Button>
          </>
        }
      >
        <p className="m-0 text-sm leading-6 text-[#626b78]">
          确定删除参数 <strong>{deleting?.paramName}</strong> 吗？删除后无法恢复。
        </p>
      </Modal>
    </OperationsPageShell>
  );
}
