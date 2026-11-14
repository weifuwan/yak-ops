import type { CSSProperties } from "react";
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
  monochrome?: boolean;
};

/**
 * Renders indicator SVGs with their original colors by default.
 * Menu icons can opt into a monochrome CSS mask to follow the product palette.
 */
export function IndicatorIcon({
  name,
  size = 16,
  className = "",
  monochrome = false,
}: IndicatorIconProps) {
  const iconUrl = `${import.meta.env.BASE_URL}icons/${name}.svg`;

  if (monochrome) {
    const maskStyle: CSSProperties = {
      width: size,
      height: size,
      maskImage: `url("${iconUrl}")`,
      WebkitMaskImage: `url("${iconUrl}")`,
      maskRepeat: "no-repeat",
      WebkitMaskRepeat: "no-repeat",
      maskPosition: "center",
      WebkitMaskPosition: "center",
      maskSize: "contain",
      WebkitMaskSize: "contain",
    };

    return (
      <span
        aria-hidden="true"
        className={["inline-block shrink-0 bg-[#3a3d51]", className]
          .filter(Boolean)
          .join(" ")}
        style={maskStyle}
      />
    );
  }

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
