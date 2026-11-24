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
          <path d="M896 128H128a42.688 42.688 0 0 0-42.688 42.688v682.624c0 23.552 19.136 42.688 42.688 42.688h192v-85.312H170.688v-384h170.624V576h85.376V426.688h170.624V576h85.376V426.688h170.624V576h85.376V170.688A42.688 42.688 0 0 0 896 128z m-42.688 213.312H170.688v-128h682.624v128z" />
          <path d="M385.216 648.32h119.488v182.4h-32.256v-143.744h-54.656v143.808h-32.576V648.32z m-3.712 234.368q24.576-4.608 36.288-23.36 11.648-18.752 11.648-46.4v-104.128h31.36v104.128q0 6.144-0.32 9.216 38.4 29.184 53.76 98.048h-34.432q-3.072-19.392-9.216-33.92-6.144-14.656-16.896-25.408-8.576 24.96-26.688 40.576-18.112 15.68-45.44 21.76v-40.512z m136.704-114.304h42.432v-126.208h39.936v39.296h63.552v39.936h-63.552v46.976h50.048v148.736H518.208v-148.736z m93.696 109.056v-69.376h-54.976v69.376h55.04z m355.776-229.44v36.224h-55.04v14.72h49.472v121.088h-48.192v100.16h-36.864v-100.16h-51.328v-121.024h47.616v-14.72h-59.264v105.92q0 36.864-3.072 68.224-3.072 31.36-10.432 61.76h-41.792q9.792-31.36 13.632-61.312 3.84-29.952 3.84-68.672v-142.208h191.36z m-234.432 272.192q21.824-54.4 31.36-130.88h-41.152q-8.32 64.832-35.008 130.88h44.8z m31.36-145.024v-41.152l-73.408-18.112v41.728l73.408 17.536z m-0.64-72.512v-41.792l-66.624-17.152v41.728l66.624 17.28z m54.4 214.72q13.184-43.904 13.184-85.12h32.256q0 44.288-10.752 85.12h-34.688z m108.416-127.168v-16.896H861.44v16.896h65.408z m0-44.16v-16.96H861.44v16.896h65.408z m32.64 86.272q0 41.152 13.184 85.12h-34.752q-10.752-40.896-10.752-85.12h32.256z" />
        </>
      );
    case "DIMENSION":
      return (
        <path d="M192 326.509714v370.907429L512 882.688l320-185.270857V326.509714L512 141.238857 192 326.509714zM512 42.642286l405.357714 234.642285v469.357715L512 981.284571l-405.357714-234.642285V277.284571L512 42.642286zM277.211429 425.398857l192.073142 111.177143v215.405714h85.284572V536.576l192.073143-111.177143-42.788572-73.874286L512 462.701714l-191.926857-111.177143-42.788572 73.874286z" />
      );
    case "DETAIL":
      return (
        <path d="M1028.65361 658.152594v219.55471A146.618049 146.618049 0 0 1 881.663198 1023.999535H146.990412A146.618049 146.618049 0 0 1 0 877.707304V146.292696A146.618049 146.618049 0 0 1 146.990412 0.000465h734.672786A146.618049 146.618049 0 0 1 1028.65361 146.292696V658.152594zM94.952641 929.419257a73.076297 73.076297 0 0 0 52.084316 21.50398h734.858969a73.681388 73.681388 0 0 0 67.816665-45.288686c3.677088-8.843628 5.585449-18.385438 5.538904-28.020338V731.228892H73.402115v146.385321a72.610843 72.610843 0 0 0 21.597071 51.805044zM404.014178 402.152827v255.999767h220.625254v-255.999767H404.014178z m220.625254-73.076297v-255.999767H404.014178v255.999767h220.625254zM330.565518 402.152827H73.402115v255.999767h257.163403v-255.999767zM94.999186 94.487652a72.517752 72.517752 0 0 0-21.597071 51.665408V329.07653h257.163403v-255.999767H147.083503a73.35557 73.35557 0 0 0-52.037771 21.364344z m860.252309 51.665408a72.610843 72.610843 0 0 0-45.288686-67.490848 73.541751 73.541751 0 0 0-28.066883-5.538904h-183.854379v255.999767h257.209948V146.15306z m0 256.046313h-257.163402v255.999767h257.163402v-255.999767z" />
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
      viewBox={
        type === "SOURCE" || type === "DIMENSION"
          ? "0 0 1024 1024"
          : type === "DETAIL"
            ? "0 0 1070 1024"
            : "0 0 16 16"
      }
      width={size}
      height={size}
      fill={
        type === "SOURCE" || type === "DETAIL" || type === "DIMENSION"
          ? "currentColor"
          : "none"
      }
      stroke={
        type === "SOURCE" || type === "DETAIL" || type === "DIMENSION"
          ? "none"
          : "currentColor"
      }
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
