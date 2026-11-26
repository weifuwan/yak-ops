import { useEffect, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, toast } from "@yak-ops/yak-ui";
import { Save, Send, X } from "lucide-react";

import { getIndicatorApi } from "@/service/data-modeling/indicators";
import type { ModelingRecord, ModelingVersionRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorEditorFieldsProps, IndicatorOptionResource, IndicatorValidation } from "../../types";
import type { IndicatorDefinition, IndicatorKind, IndicatorTab } from "../../types";
import { initialFormValues, readFormValue } from "../../utils/indicator-form";
import { IndicatorPanelRail } from "../panels/indicator-panel-rail";
import { IndicatorVersionPanel } from "../panels/indicator-version-panel";
import { IndicatorFormRenderer } from "./indicator-form-renderer";

type IndicatorEditorProps = {
  projectCode: string;
  definition: IndicatorDefinition;
  tab: IndicatorTab;
  optionsByResource: Partial<Record<IndicatorOptionResource, ModelingRecord[]>>;
  optionsFailedResources?: IndicatorOptionResource[];
  optionsLoading: boolean;
  renderFields?: (props: IndicatorEditorFieldsProps) => ReactNode;
  validateBeforeSave?: IndicatorValidation;
  validateBeforeSubmit?: IndicatorValidation;
  onSaved: (kind: IndicatorKind, tabKey: string, record: ModelingRecord) => void;
  onDraftChange: (kind: IndicatorKind, tabKey: string, values: Record<string, string>) => void;
};

export function IndicatorEditor({
  projectCode,
  definition,
  tab,
  optionsByResource,
  optionsFailedResources = [],
  optionsLoading,
  renderFields,
  validateBeforeSave,
  validateBeforeSubmit,
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
    queryFn: () => recordId ? getIndicatorApi(definition.key).versions(projectCode, recordId) : Promise.resolve([]),
    enabled: Boolean(projectCode && recordId && versionOpen),
  });

  const changeValues = (updates: Record<string, string>) => {
    const nextValues = { ...values, ...updates };
    setValues(nextValues);
    setDirty(true);
    setNoticeDismissed(false);
    onDraftChange(tab.kind, tab.key, nextValues);
    setError("");
  };

  const changeValue = (key: string, value: string) => changeValues({ [key]: value });

  const save = async () => {
    if (saving) return;
    const missing = definition.fields.find((field) => field.required && !String(values[field.key] || "").trim());
    if (missing) {
      setError("请填写" + missing.label);
      return;
    }
    const validationMessage = validateBeforeSave?.(values, optionsByResource);
    if (validationMessage) {
      setError(validationMessage);
      return;
    }
    const payload: Record<string, unknown> = {};
    definition.fields.forEach((field) => {
      const value = String(values[field.key] ?? "").trim();
      if (field.key === "decimalNumber" && value !== "") payload[field.key] = Number(value);
      else if (field.key === "removeDuplicates" && value !== "") payload[field.key] = value === "true";
      else if (field.key === "dwType" && value !== "PUBLIC_LAYER" && value !== "APPLICATION_LAYER") payload[field.key] = null;
      else payload[field.key] = value;
    });
    // Atomic indicators are always stored in the public layer; the editor intentionally
    // does not expose this as a user-editable form field.
    if (definition.key === "atomIndicator") payload.dwType = "PUBLIC_LAYER";
    if (recordId) payload.id = recordId;

    setSaving(true);
    try {
      let saved: ModelingRecord;
      if (recordId) {
        saved = await getIndicatorApi(definition.key).update(projectCode, payload);
      } else {
        const createdId = await getIndicatorApi(definition.key).create(projectCode, payload);
        saved = await getIndicatorApi(definition.key).getById<ModelingRecord>(projectCode, String(createdId));
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
    const validationMessage = validateBeforeSubmit?.(values, optionsByResource);
    if (validationMessage) {
      setError(validationMessage);
      return;
    }
    setSubmitting(true);
    try {
      const submitted = await getIndicatorApi(definition.key).submit(projectCode, recordId);
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
      const restored = await getIndicatorApi(definition.key).rollback(projectCode, recordId, version.versionNumber);
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
            <IndicatorFormRenderer
              projectCode={projectCode}
              definition={definition}
              values={values}
              recordId={recordId}
              optionsByResource={optionsByResource}
              optionsFailedResources={optionsFailedResources}
              optionsLoading={optionsLoading}
              renderFields={renderFields}
              onChange={changeValue}
              onChangeMany={changeValues}
            />
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
