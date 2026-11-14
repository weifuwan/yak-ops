import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert, Button, Field, FieldLabel, Input, Select, SelectContent, SelectItem, SelectItemIndicator,
  SelectItemText, SelectTrigger, SelectValue, Textarea, toast,
} from "@yak-ops/yak-ui";
import { Save, Send, X } from "lucide-react";

import { modelingEntityApi, type ModelingRecord, type ModelingResourceKey, type ModelingVersionRecord } from "../../../../service";
import type { IndicatorDefinition, IndicatorKind, IndicatorTab } from "../../types";
import { fieldOptions, initialFormValues, readFormValue } from "../../utils/indicator-form";
import { IndicatorPanelRail } from "../panels/indicator-panel-rail";
import { IndicatorVersionPanel } from "../panels/indicator-version-panel";

type IndicatorEditorProps = {
  projectCode: string;
  definition: IndicatorDefinition;
  tab: IndicatorTab;
  optionsByResource: Partial<Record<ModelingResourceKey, ModelingRecord[]>>;
  optionsLoading: boolean;
  onSaved: (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => void;
  onDraftChange: (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => void;
};

export function IndicatorEditor({
  projectCode,
  definition,
  tab,
  optionsByResource,
  optionsLoading,
  onSaved,
  onDraftChange,
}: IndicatorEditorProps) {
  const queryClient = useQueryClient();
  const [values, setValues] = useState<Record<string, string>>(() =>
    tab.draftValues || initialFormValues(definition.key, tab.record, definition.fields),
  );
  const [dirty, setDirty] = useState(Boolean(tab.dirty));
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [noticeDismissed, setNoticeDismissed] = useState(false);
  const [versionOpen, setVersionOpen] = useState(false);
  const [versionPanelWidth, setVersionPanelWidth] = useState(340);
  const recordId = tab.recordId || (tab.record?.id ? String(tab.record.id) : "");

  useEffect(() => {
    setValues(tab.draftValues || initialFormValues(definition.key, tab.record, definition.fields));
    setDirty(Boolean(tab.dirty));
    setError("");
    setNoticeDismissed(false);
  }, [tab.key, tab.record, definition]);

  const versionQuery = useQuery<ModelingVersionRecord[]>({
    queryKey: ["modeling", "indicator-versions", projectCode, definition.key, recordId],
    queryFn: () => recordId ? modelingEntityApi.versions(projectCode, definition.key, recordId) : Promise.resolve([]),
    enabled: Boolean(projectCode && recordId && versionOpen),
  });

  const changeValue = (key: string, value: string) => {
    const nextValues = { ...values, [key]: value };
    setValues(nextValues);
    setDirty(true);
    setNoticeDismissed(false);
    onDraftChange(tab.kind, tab.key, nextValues);
    setError("");
  };

  const save = async () => {
    if (saving) return;
    const missing = definition.fields.find((field) => field.required && !String(values[field.key] || "").trim());
    if (missing) {
      setError("请填写" + missing.label);
      return;
    }
    const payload: Record<string, unknown> = {};
    definition.fields.forEach((field) => {
      const value = String(values[field.key] ?? "").trim();
      if (field.key === "decimalNumber" && value !== "") payload[field.key] = Number(value);
      else if (field.key === "removeDuplicates" && value !== "") payload[field.key] = value === "true";
      else payload[field.key] = value;
    });
    if (recordId) payload.id = recordId;

    setSaving(true);
    try {
      let saved: ModelingRecord;
      if (recordId) {
        saved = await modelingEntityApi.update(projectCode, definition.key, payload);
      } else {
        const createdId = await modelingEntityApi.create(projectCode, definition.key, payload);
        saved = await modelingEntityApi.getById<ModelingRecord>(projectCode, definition.key, String(createdId));
      }
      onSaved(tab.kind, tab.key, saved);
      setDirty(false);
      setError("");
      setNoticeDismissed(false);
      toast.success(definition.title + "已保存");
      await queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-versions", projectCode, definition.key] });
    } catch {
      toast.warning("保存失败，请检查填写内容后重试");
    } finally {
      setSaving(false);
    }
  };

  const submit = async () => {
    if (!recordId || dirty || submitting) return;
    setSubmitting(true);
    try {
      const submitted = await modelingEntityApi.submit(projectCode, definition.key, recordId);
      onSaved(tab.kind, tab.key, submitted);
      setNoticeDismissed(false);
      toast.success("已提交，版本快照已保存");
      await queryClient.invalidateQueries({ queryKey: ["modeling", "indicator-versions", projectCode, definition.key, recordId] });
    } catch {
      toast.warning("提交失败，请检查指标信息是否完整");
    } finally {
      setSubmitting(false);
    }
  };

  const rollback = async (version: ModelingVersionRecord) => {
    if (!recordId) return;
    try {
      const restored = await modelingEntityApi.rollback(projectCode, definition.key, recordId, version.versionNumber);
      onSaved(tab.kind, tab.key, restored);
      setValues(Object.fromEntries(definition.fields.map((field) => [field.key, readFormValue(restored, field.key)])));
      setDirty(false);
      setNoticeDismissed(false);
      setVersionOpen(false);
      toast.success("已回滚到 V" + version.versionNumber);
    } catch {
      toast.warning("版本回滚失败，请刷新后重试");
    }
  };

  const sectionNames = Array.from(new Set(definition.fields.map((field) => field.section || "基本信息")));
  const currentVersion = tab.record?.currentVersion ? "V" + String(tab.record.currentVersion) : "暂无";
  const isSubmitted = tab.record?.submit === true || tab.record?.submit === "true";
  const showNotice = dirty || Boolean(recordId && !isSubmitted);
  const noticeMessage = dirty
    ? "当前存在未保存的变更，请及时保存。"
    : "当前存在未提交的内容，保存后可点击上方“提交”生成最新版本。";

  return (
    <div className="flex h-full min-h-0 w-full min-w-0 flex-1 bg-white">
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex shrink-0 items-center justify-between gap-3 border-b border-[var(--yak-components-panel-border)] px-4 py-2.5">
          <div className="flex items-center gap-2">
            <Button size="sm" variant="primary" isDisabled={!dirty || saving} isPending={saving} onClick={() => void save()}>
              <Save size={13} />保存
            </Button>
            <Button size="sm" variant="secondary" isDisabled={!recordId || dirty || isSubmitted || submitting} isPending={submitting} onClick={() => void submit()}>
              <Send size={13} />提交
            </Button>
          </div>
          <div className="flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
            <span className={isSubmitted ? "size-1.5 rounded-full bg-[var(--yak-color-success)]" : "size-1.5 rounded-full bg-[var(--yak-components-muted-text)]"} />
            <span>当前版本：{currentVersion}</span>
            <span className="mx-1 h-3 w-px bg-[var(--yak-components-control-border)]" />
            <span>{isSubmitted ? "已提交" : recordId ? "草稿 / 待提交" : "新建草稿"}</span>
          </div>
        </div>

        {showNotice && !noticeDismissed ? (
          <Alert className="shrink-0 rounded-none px-4 py-1.5">
            <span>{noticeMessage}</span>
            <button
              type="button"
              aria-label="关闭提示"
              className="ml-auto inline-flex size-5 shrink-0 items-center justify-center rounded-sm text-[var(--yak-components-muted-text)] transition-colors hover:bg-black/5 hover:text-[var(--yak-components-panel-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--yak-color-primary)]"
              onClick={() => setNoticeDismissed(true)}
            >
              <X size={12} />
            </button>
          </Alert>
        ) : null}

        <div className="min-h-0 flex-1 overflow-y-auto bg-[var(--yak-color-hover)] p-4">
          <div className="w-full space-y-4">
            {sectionNames.map((section) => (
              <section key={section} className="bg-white p-4">
                <div className="mb-3 flex items-center gap-2 border-b border-[var(--yak-components-panel-border)] pb-2">
                  <span aria-hidden="true" className="h-4 w-1 shrink-0 bg-[#5664ff]" />
                  <h3 className="m-0 text-sm font-semibold leading-7 text-[var(--yak-components-panel-text)]">{section}</h3>
                </div>
                <div className="grid grid-cols-1 gap-y-3">
                  {definition.fields.filter((field) => (field.section || "基本信息") === section).map((field) => {
                    const options = fieldOptions(field, optionsByResource);
                    const isSelect = Boolean(field.options || field.optionsSource);
                    const disabled = Boolean(field.readOnly && recordId);
                    return (
                      <Field key={field.key} className="grid grid-cols-1 items-start gap-2 sm:grid-cols-[150px_minmax(0,1fr)] sm:gap-3">
                        <FieldLabel required={field.required} className="pt-1.5 text-xs leading-5 text-[var(--yak-components-muted-text)]">{field.label}</FieldLabel>
                        <div className="min-w-0">
                          {isSelect ? (
                            <Select
                              size="small"
                              value={values[field.key] || undefined}
                              disabled={disabled}
                              onValueChange={(value) => changeValue(field.key, String(value || ""))}
                            >
                              <SelectTrigger variant="outlined" className="w-full">
                                <SelectValue placeholder={optionsLoading ? "正在加载选项..." : field.placeholder || ("请选择" + field.label)} />
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
                            <Textarea
                              size="small"
                              variant="outlined"
                              rows={3}
                              value={values[field.key] || ""}
                              disabled={disabled}
                              placeholder={field.placeholder || ("请输入" + field.label)}
                              onValueChange={(value) => changeValue(field.key, String(value || ""))}
                            />
                          ) : (
                            <Input
                              size="small"
                              className="w-full"
                              variant="outlined"
                              type={field.key === "decimalNumber" ? "number" : "text"}
                              min={field.key === "decimalNumber" ? 0 : undefined}
                              value={values[field.key] || ""}
                              disabled={disabled}
                              placeholder={field.placeholder || ("请输入" + field.label)}
                              onChange={(event) => changeValue(field.key, event.target.value)}
                            />
                          )}
                        </div>
                      </Field>
                    );
                  })}
                </div>
              </section>
            ))}
            {error ? <p role="alert" className="text-xs text-[var(--yak-components-danger-text)]">{error}</p> : null}
          </div>
        </div>
      </div>

      <aside className="flex h-full min-h-0 shrink-0 bg-white">
        {versionOpen ? (
          <IndicatorVersionPanel
            width={versionPanelWidth}
            versions={versionQuery.data || []}
            loading={versionQuery.isFetching}
            onResize={setVersionPanelWidth}
            onClose={() => setVersionOpen(false)}
            onRollback={(version) => void rollback(version)}
          />
        ) : null}
        <IndicatorPanelRail
          open={versionOpen}
          disabled={!recordId}
          onToggle={() => setVersionOpen((open) => !open)}
        />
      </aside>
    </div>
  );
}
