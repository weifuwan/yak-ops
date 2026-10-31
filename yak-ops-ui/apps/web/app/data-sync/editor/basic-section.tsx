import { CollapseSection, Field, FieldLabel, Input, Textarea } from "@yak-ops/yak-ui";
import { syncSummary } from "../solution/presentation";
import type { DataSyncDraft } from "@/service/data-sync";
import type { EditorForm } from "./types";

interface Props {
  draft: DataSyncDraft;
  form: EditorForm;
  patch: <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => void;
}
export function BasicSection({ draft, form, patch }: Props) {
  return (
    <CollapseSection id="basic" title="基本信息">
      <div className="space-y-3 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
        <div className="grid grid-cols-[104px_minmax(0,1fr)] items-center gap-3 text-sm">
          <span>同步方案</span>
          <span>{syncSummary(draft.productType, draft.steps)}</span>
        </div>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>任务名称</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            maxLength={128}
            value={form.name}
            placeholder="请输入任务名称"
            onChange={(event) => patch("name", event.target.value)}
          />
        </Field>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel className="pt-1.5">备注</FieldLabel>
          <Textarea
            size="small"
            rows={2}
            maxLength={500}
            value={form.remark}
            className="min-h-[56px] resize-none"
            placeholder="可选"
            onValueChange={(value) => patch("remark", value)}
          />
        </Field>
      </div>
    </CollapseSection>
  );
}
