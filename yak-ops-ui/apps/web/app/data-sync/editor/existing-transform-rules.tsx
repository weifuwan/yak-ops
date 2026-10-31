import { CollapseSection } from "@yak-ops/yak-ui";
import type { DataSyncTransform } from "@/service/data-sync";
import { updateTransformRule } from "@/service/data-sync/transforms";
import { TransformExpressionFields } from "./transform-expression-fields";

/** Exposes already-authored native rules without adding field preparation to replication creation. */
export function ExistingTransformRules({
  value,
  disabled,
  onChange,
}: {
  value: DataSyncTransform[];
  disabled: boolean;
  onChange: (rules: DataSyncTransform[]) => void;
}) {
  return (
    <CollapseSection id="advanced-transforms" title="高级转换规则">
      <div className="space-y-4">
        {value.map((rule, index) => (
          <div
            key={index}
            className="rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4"
          >
            <TransformExpressionFields
              rule={rule}
              index={index}
              showSelector
              disabled={disabled}
              onChange={(patch) =>
                onChange(updateTransformRule(value, index, rule["source-table"], patch))
              }
            />
          </div>
        ))}
      </div>
    </CollapseSection>
  );
}
