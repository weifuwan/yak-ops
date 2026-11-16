import { ATOM_INDICATOR_DEFINITION } from "../../atom-indicator/config";
import { DERIVATIVE_INDICATOR_DEFINITION } from "../../derivative-indicator/config";
import { COMPOSITE_INDICATOR_DEFINITION } from "../../composite-indicator/config";
import { MODIFIER_DEFINITION } from "../../modifier/config";
import { TIME_PERIOD_DEFINITION } from "../../time-period/config";
import { DW_TYPE_OPTIONS, PAGE_SIZE_OPTIONS, RELATION_FIELDS } from "./indicator-options";
import type { IndicatorDefinition, IndicatorKind } from "../types";

export { DW_TYPE_OPTIONS, PAGE_SIZE_OPTIONS, RELATION_FIELDS };

const INDICATOR_DEFINITIONS: IndicatorDefinition[] = [
  ATOM_INDICATOR_DEFINITION,
  DERIVATIVE_INDICATOR_DEFINITION,
  COMPOSITE_INDICATOR_DEFINITION,
  MODIFIER_DEFINITION,
  TIME_PERIOD_DEFINITION,
];

export function getDefinition(kind: IndicatorKind): IndicatorDefinition {
  return INDICATOR_DEFINITIONS.find((item) => item.key === kind) || ATOM_INDICATOR_DEFINITION;
}
