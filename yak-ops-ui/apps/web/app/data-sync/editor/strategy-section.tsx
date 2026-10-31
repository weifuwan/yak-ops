import { Alert, CollapseSection, Field, FieldLabel } from "@yak-ops/yak-ui";
import type { DataSyncDraft, DataSyncSolution, DataSyncStep } from "@/service/data-sync";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { strategyLabel } from "../solution/presentation";

export function StrategySection({
  draft,
  solution,
  disabled,
  startupMode,
  onChange,
}: {
  draft: DataSyncDraft;
  solution?: DataSyncSolution;
  disabled: boolean;
  startupMode?: string;
  onChange: (steps: DataSyncStep[]) => void;
}) {
  const choices =
    solution?.allowedCombinations.filter((combination) => !combination.steps.includes("SCHEMA")) ??
    [];
  const replication = draft.productType === "DATABASE_REPLICATION";
  const incremental = draft.steps.includes("INCREMENTAL");
  const initial = draft.steps.includes("FULL");
  return (
    <CollapseSection id="strategy" title="同步策略">
      <div className="space-y-3 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel required>读取策略</FieldLabel>
          <DataSyncSearchableSelect
            ariaLabel="读取策略"
            placeholder="请选择读取策略"
            value={draft.steps.filter((step) => step !== "SCHEMA").join("+")}
            options={choices.map(({ steps }) => ({
              value: steps.join("+"),
              label: strategyLabel(draft.productType, steps),
            }))}
            disabled={disabled || !solution}
            onValueChange={(value) => {
              const selected = choices.find(({ steps }) => steps.join("+") === value);
              if (selected) onChange(selected.steps);
            }}
          />
        </Field>
        {disabled ? (
          <Alert>请先加载调度状态，或将逐行更新改为按主键更新，再修改同步策略。</Alert>
        ) : null}
        {replication && !incremental ? (
          <Alert>读取所选表的全量数据，完成后结束。可手动运行或配置调度。</Alert>
        ) : null}
        {replication && incremental && !initial ? (
          <Alert>
            {startupMode && startupMode !== "LATEST_OFFSET"
              ? `当前使用 ${startupMode} 启动位置；修改其他配置会保留完整位点参数。`
              : "从任务启动时的最新位点读取变更，不复制已有数据。请提前准备目标表和所需历史数据。"}
          </Alert>
        ) : null}
        {replication && incremental ? (
          <Alert>持续同步需要满足来源 CDC 日志和权限要求；连接测试通过不代表已通过同步校验。</Alert>
        ) : null}
      </div>
    </CollapseSection>
  );
}
