import type { IndicatorKind } from "../workbench/types";

export type IndicatorIconName =
  | "composite-indicator"
  | "derived-indicator"
  | "atomic-indicator"
  | "indicator-modifier"
  | "time-period";

const INDICATOR_ICON_BY_KIND: Record<IndicatorKind, IndicatorIconName> = {
  composeIndicator: "composite-indicator",
  derivativeIndicator: "derived-indicator",
  atomIndicator: "atomic-indicator",
  modifier: "indicator-modifier",
  timePeriod: "time-period",
};

export function getIndicatorIconName(kind: IndicatorKind): IndicatorIconName {
  return INDICATOR_ICON_BY_KIND[kind];
}

type IndicatorIconProps = {
  name: IndicatorIconName;
  size?: number;
  className?: string;
};

/**
 * Renders the indicator SVG from public/icons without masking its semantic colors.
 */
export function IndicatorIcon({
  name,
  size = 16,
  className = "",
}: IndicatorIconProps) {
  const iconUrl = `${import.meta.env.BASE_URL}icons/${name}.svg`;

  return (
    <img
      src={iconUrl}
      alt=""
      aria-hidden="true"
      draggable={false}
      className={["inline-block shrink-0 object-contain", className]
        .filter(Boolean)
        .join(" ")}
      style={{ width: size, height: size }}
    />
  );
}
