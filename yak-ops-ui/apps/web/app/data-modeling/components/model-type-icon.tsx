import type { CSSProperties, SVGProps } from "react";

export const MODEL_TYPE_ICON_COLORS = {
  DIM: "#f97316",
  SOURCE: "#1677ff",
  DIMENSION: "#14a8a0",
  DETAIL: "#4f46e5",
  SUMMARY: "#16a34a",
  APPLICATION: "#9333ea",
} as const;

export type ModelTypeIconType = keyof typeof MODEL_TYPE_ICON_COLORS;

export function isModelTypeIconType(value: unknown): value is ModelTypeIconType {
  return typeof value === "string" && value in MODEL_TYPE_ICON_COLORS;
}

type ModelTypeIconProps = Omit<SVGProps<SVGSVGElement>, "color"> & {
  type: ModelTypeIconType;
  size?: number;
  color?: string;
};

function iconContent(type: ModelTypeIconType) {
  switch (type) {
    case "DIM":
      return (
        <>
          <circle cx="8" cy="3.1" r="1.65" />
          <circle cx="4" cy="11.8" r="1.65" />
          <circle cx="12" cy="11.8" r="1.65" />
          <path d="M8 4.75v2M8 6.75H4v3.4M8 6.75h4v3.4" />
        </>
      );
    case "SOURCE":
      return (
        <>
          <ellipse cx="6.4" cy="3.6" rx="3.7" ry="1.6" />
          <path d="M2.7 3.6v6.1c0 .9 1.65 1.65 3.7 1.65 1.05 0 1.95-.2 2.6-.55" />
          <path d="M2.7 6.55c0 .9 1.65 1.65 3.7 1.65 1.05 0 1.95-.2 2.6-.55" />
          <path d="M10.1 8.15h3.2M12 6.85l1.3 1.3-1.3 1.3" />
        </>
      );
    case "DIMENSION":
      return (
        <>
          <rect x="2.5" y="3.1" width="11" height="10.2" rx="1.25" />
          <path d="M2.5 6.45h11M6 6.45v6.85" />
          <path
            d="m11.75 1.35.5 1.05 1.05.5-1.05.5-.5 1.05-.5-1.05-1.05-.5 1.05-.5.5-1.05Z"
            fill="currentColor"
            stroke="none"
          />
        </>
      );
    case "DETAIL":
      return (
        <>
          <rect x="2.5" y="2.5" width="11" height="11" rx="1.25" />
          <circle cx="4.2" cy="5.15" r=".45" fill="currentColor" stroke="none" />
          <circle cx="4.2" cy="8" r=".45" fill="currentColor" stroke="none" />
          <circle cx="4.2" cy="10.85" r=".45" fill="currentColor" stroke="none" />
          <path d="M5.6 5.15h5.8M5.6 8h5.8M5.6 10.85h5.8" />
        </>
      );
    case "SUMMARY":
      return (
        <>
          <rect x="2.5" y="2.5" width="11" height="11" rx="1.25" />
          <path d="M4.25 11.3V8.8M7.25 11.3V6.6M10.25 11.3V4.75M3.75 11.45h8.5" />
        </>
      );
    case "APPLICATION":
      return (
        <>
          <rect x="2.3" y="2.5" width="11.4" height="11" rx="1.4" />
          <path d="M2.3 5.35h11.4" />
          <circle cx="4" cy="3.95" r=".42" fill="currentColor" stroke="none" />
          <rect x="4" y="7.15" width="3.1" height="2.35" rx=".45" />
          <rect x="8.65" y="7.15" width="3.1" height="2.35" rx=".45" />
          <path d="M4 11.15h7.75" />
        </>
      );
  }
}

export function ModelTypeIcon({
  type,
  size = 16,
  color,
  className,
  style,
  ...props
}: ModelTypeIconProps) {
  const mergedStyle: CSSProperties = {
    color: color || MODEL_TYPE_ICON_COLORS[type],
    ...style,
  };

  return (
    <svg
      {...props}
      aria-hidden={props["aria-label"] ? undefined : true}
      viewBox="0 0 16 16"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.45"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      style={mergedStyle}
    >
      {iconContent(type)}
    </svg>
  );
}

export default ModelTypeIcon;
