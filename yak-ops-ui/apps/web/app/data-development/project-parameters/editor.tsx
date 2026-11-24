import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  Button,
  Field,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { Save, Trash2 } from "lucide-react";

import { requireActiveProjectCode } from "@/service/workspace/project-scope";
import {
  createProjectParameter,
  deleteProjectParameter,
  updateProjectParameter,
  type ProjectParameterRecord,
} from "@/service/project/parameter";
import { useDataDevelopmentWorkbench } from "../workbench-context";

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

export function ProjectParameterEditor({
  record,
}: {
  record: ProjectParameterRecord | null;
}) {
  const { updateTabTitle, activeTabId, closeTab } =
    useDataDevelopmentWorkbench();
  const projectCode = requireActiveProjectCode();
  const queryClient = useQueryClient();
  const [savedRecord, setSavedRecord] =
    useState<ProjectParameterRecord | null>(record);
  const [paramName, setParamName] = useState(record?.paramName ?? "");
  const [paramValue, setParamValue] = useState(record?.paramValue ?? "");
  const [paramDataType, setParamDataType] = useState(
    record?.paramDataType || "VARCHAR",
  );
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const save = async () => {
    const name = paramName.trim();
    if (!name) {
      toast.error("请输入参数名称");
      return;
    }

    setSaving(true);
    try {
      const payload = {
        projectParameterName: name,
        projectParameterValue: paramValue,
        projectParameterDataType: paramDataType,
      };
      const next = savedRecord
        ? await updateProjectParameter(projectCode, savedRecord.code, payload)
        : await createProjectParameter(projectCode, payload);
      setSavedRecord(next);
      if (activeTabId) updateTabTitle(activeTabId, next.paramName);
      await queryClient.invalidateQueries({
        queryKey: ["data-development", "project-parameter-panel"],
      });
      toast.success(savedRecord ? "项目参数已更新" : "项目参数已创建");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "保存项目参数失败");
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!savedRecord) return;
    setDeleting(true);
    try {
      await deleteProjectParameter(projectCode, savedRecord.code);
      await queryClient.invalidateQueries({
        queryKey: ["data-development", "project-parameter-panel"],
      });
      toast.success("项目参数已删除");
      if (activeTabId) closeTab(activeTabId);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "删除项目参数失败");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="h-full min-h-0 w-full min-w-0 overflow-y-auto bg-[#f7f8fa] p-5">
      <div className="mx-auto w-full max-w-[980px]">
        <section className="rounded-lg border border-[#e6e9ee] bg-white p-5">
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <div className="text-sm font-semibold text-[#30343a]">
                {savedRecord ? "编辑项目参数" : "新建项目参数"}
              </div>
              <div className="mt-1 text-xs text-[#8a9099]">
                当前项目公共参数，可被工作流和节点配置复用
              </div>
            </div>
            <div className="flex gap-2">
              {savedRecord ? (
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() => void remove()}
                  isPending={deleting}
                  isDisabled={saving || deleting}
                >
                  <Trash2 size={13} />
                  删除
                </Button>
              ) : null}
              <Button
                size="sm"
                variant="primary"
                onClick={() => void save()}
                isPending={saving}
                isDisabled={saving || deleting}
              >
                <Save size={13} />
                {saving ? "保存中..." : "保存"}
              </Button>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 max-md:grid-cols-1">
            <Field>
              <FieldLabel>参数名称</FieldLabel>
              <Input
                size="small"
                variant="outlined"
                value={paramName}
                onChange={(event) => setParamName(event.target.value)}
                placeholder="例如 biz_date"
              />
            </Field>
            <Field>
              <FieldLabel>数据类型</FieldLabel>
              <Select
                size="small"
                items={DATA_TYPES}
                value={paramDataType}
                onValueChange={(value) =>
                  setParamDataType(String(value ?? "VARCHAR"))
                }
              >
                <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(DATA_TYPES).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{label}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
          </div>

          <div className="mt-4">
            <Field>
              <FieldLabel>参数值</FieldLabel>
              <Textarea
                size="small"
                variant="outlined"
                rows={8}
                value={paramValue}
                onValueChange={setParamValue}
                placeholder="请输入参数值"
              />
            </Field>
          </div>
        </section>
      </div>
    </div>
  );
}
