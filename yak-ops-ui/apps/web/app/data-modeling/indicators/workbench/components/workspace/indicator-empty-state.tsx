import { Button } from "@yak-ops/yak-ui";
import { CalendarDays, ChartNoAxesCombined, Plus } from "lucide-react";
import type { IndicatorDefinition, IndicatorKind } from "../../types";

type Props = { activeKind: IndicatorKind; definition: IndicatorDefinition; onCreate: () => void };

export function IndicatorEmptyState({ activeKind, definition, onCreate }: Props) {
  return <div className="absolute inset-0 flex min-h-0 flex-col items-center justify-center bg-white text-center">
    <div className="mb-3 flex size-12 items-center justify-center rounded-xl bg-[var(--yak-color-hover)] text-[var(--yak-color-primary)]">{activeKind === "timePeriod" ? <CalendarDays size={22} /> : <ChartNoAxesCombined size={22} />}</div>
    <div className="text-sm font-medium text-[var(--yak-components-panel-text)]">请选择或创建{definition.title}</div>
    <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">从左侧目录打开指标，或新建一条记录开始维护</div>
    <Button size="sm" variant="primary" className="mt-4" onClick={onCreate}><Plus size={14} />新建{definition.title}</Button>
  </div>;
}
