import type { CSSProperties } from "react";

import {
  getResourceFileTypeConfig,
  type ResourceFileIconName,
} from "../file-types";

function FileTypeGlyph({ type }: { type: ResourceFileIconName }) {
  switch (type) {
    case "sql":
      return (
        <>
          <ellipse cx="12" cy="5.4" rx="7.2" ry="3.1" fill="currentColor" opacity="0.16" />
          <path d="M4.8 5.4v13.1c0 1.7 3.2 3.1 7.2 3.1s7.2-1.4 7.2-3.1V5.4" />
          <path d="M4.8 5.4c0 1.7 3.2 3.1 7.2 3.1s7.2-1.4 7.2-3.1M4.8 11.8c0 1.7 3.2 3.1 7.2 3.1s7.2-1.4 7.2-3.1" />
        </>
      );
    case "python":
      return (
        <>
          <path
            d="M12.1 2.5c-4.6 0-4.4 2-4.4 2v3h4.6v.9H5.7C3.4 8.4 2.5 10 2.5 12s.9 3.7 3.2 3.7h1.9v-2.2c0-2.1 1.4-3.5 3.5-3.5h4.7c1.8 0 2.8-1.2 2.8-2.8V5c0-1.7-1.5-2.5-6.5-2.5Z"
            fill="#3776AB"
            stroke="none"
          />
          <circle cx="9.4" cy="5.1" r="0.7" fill="#FFFFFF" stroke="none" />
          <path
            d="M11.9 21.5c4.6 0 4.4-2 4.4-2v-3h-4.6v-.9h6.6c2.3 0 3.2-1.6 3.2-3.6s-.9-3.7-3.2-3.7h-1.9v2.2c0 2.1-1.4 3.5-3.5 3.5H8.2c-1.8 0-2.8 1.2-2.8 2.8V19c0 1.7 1.5 2.5 6.5 2.5Z"
            fill="#FFD43B"
            stroke="none"
          />
          <circle cx="14.6" cy="18.9" r="0.7" fill="#8A6D00" stroke="none" />
        </>
      );
    case "shell":
      return (
        <>
          <rect x="3" y="4.2" width="18" height="15.6" rx="2.5" fill="currentColor" opacity="0.12" />
          <rect x="3" y="4.2" width="18" height="15.6" rx="2.5" />
          <path d="m7 9 3 2.7L7 14.4M12.5 14.4h4.2" />
        </>
      );
    case "batch":
      return (
        <>
          <path d="M6 2.8h8l4.2 4.1v13.3a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3.8a1 1 0 0 1 1-1Z" fill="currentColor" opacity="0.1" />
          <path d="M14 2.8v4.4h4.2M8 10h8M8 14h3" />
          <path d="m14.5 12.5-1 3h2l-1 3 3-4h-2l1-2Z" fill="currentColor" stroke="none" />
        </>
      );
    case "config":
      return (
        <>
          <path d="M4 7h16M4 17h16" />
          <path d="M9 4v6M15 14v6" />
          <circle cx="9" cy="7" r="2.2" fill="#FFFFFF" />
          <circle cx="15" cy="17" r="2.2" fill="#FFFFFF" />
        </>
      );
    case "hql":
      return (
        <>
          <path d="m12 2.8 7.2 4.1v8.2L12 19.2l-7.2-4.1V6.9L12 2.8Z" fill="currentColor" opacity="0.12" />
          <path d="m12 2.8 7.2 4.1v8.2L12 19.2l-7.2-4.1V6.9L12 2.8ZM12 2.8V11m7.2-4.1L12 11 4.8 6.9M19.2 15.1 12 11l-7.2 4.1" />
        </>
      );
    case "javascript":
      return (
        <>
          <rect x="2.8" y="2.8" width="18.4" height="18.4" rx="2.4" fill="currentColor" stroke="none" />
          <path d="M11.1 8.3v7.1c0 1.8-.9 2.7-2.5 2.7-1.2 0-2-.5-2.5-1.4M13.4 16.9c.6.8 1.4 1.2 2.5 1.2 1.4 0 2.3-.7 2.3-1.8 0-1.1-.7-1.5-2.1-2l-.5-.2c-1.3-.5-2.1-1.1-2.1-2.5 0-1.3 1-2.3 2.5-2.3 1.1 0 1.9.4 2.5 1.3" stroke="#FFFFFF" strokeWidth="1.7" />
        </>
      );
    case "java":
      return (
        <>
          <path d="M8 13.2h8l-.7 5.1a2 2 0 0 1-2 1.7h-2.6a2 2 0 0 1-2-1.7L8 13.2Z" fill="currentColor" opacity="0.13" />
          <path d="M8 13.2h8l-.7 5.1a2 2 0 0 1-2 1.7h-2.6a2 2 0 0 1-2-1.7L8 13.2ZM16 14h1.1a1.8 1.8 0 0 1 0 3.6h-.8M6.5 21h11" />
          <path d="M10 10.5c-1.1-1.1 1.4-1.6.2-3M13 10.5c-1.1-1.1 1.4-1.6.2-3M16 10.5c-1.1-1.1 1.4-1.6.2-3" />
        </>
      );
    case "json":
      return (
        <>
          <path d="M9 4.5H7.8A2.8 2.8 0 0 0 5 7.3v1.1c0 1.2-.5 2.1-1.8 2.6 1.3.5 1.8 1.4 1.8 2.6v1.1a2.8 2.8 0 0 0 2.8 2.8H9M15 4.5h1.2A2.8 2.8 0 0 1 19 7.3v1.1c0 1.2.5 2.1 1.8 2.6-1.3.5-1.8 1.4-1.8 2.6v1.1a2.8 2.8 0 0 1-2.8 2.8H15" />
          <circle cx="12" cy="8" r="1" fill="currentColor" stroke="none" />
          <circle cx="12" cy="12" r="1" fill="currentColor" stroke="none" />
          <circle cx="12" cy="16" r="1" fill="currentColor" stroke="none" />
        </>
      );
    case "yaml":
      return (
        <>
          <path d="M6 5.5h5.5M6 18.5h5.5M11.5 5.5v13M11.5 12h6.5" />
          <circle cx="5" cy="5.5" r="2" fill="#FFFFFF" />
          <circle cx="5" cy="18.5" r="2" fill="#FFFFFF" />
          <circle cx="19" cy="12" r="2" fill="#FFFFFF" />
          <circle cx="11.5" cy="12" r="1.5" fill="currentColor" />
        </>
      );
    case "xml":
      return (
        <>
          <path d="m8.5 6-5 6 5 6M15.5 6l5 6-5 6M13.5 4l-3 16" />
        </>
      );
    case "log":
      return (
        <>
          <path d="M6 2.8h8l4.2 4.1v13.3a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3.8a1 1 0 0 1 1-1Z" fill="currentColor" opacity="0.1" />
          <path d="M14 2.8v4.4h4.2M8 12.5h2l1.2-2.2 2.1 4.2 1.1-2h1.6" />
        </>
      );
    case "text":
      return (
        <>
          <path d="M6 2.8h8l4.2 4.1v13.3a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3.8a1 1 0 0 1 1-1Z" fill="currentColor" opacity="0.1" />
          <path d="M14 2.8v4.4h4.2M8 11h8M8 14.5h8M8 18h5" />
        </>
      );
    default:
      return null;
  }
}

export function FileTypeIcon({
  extension,
  size = 20,
  className = "",
}: {
  extension: string;
  size?: number;
  className?: string;
}) {
  const config = getResourceFileTypeConfig(extension);
  if (!config) return null;

  const style: CSSProperties = {
    width: size,
    height: size,
    color: config.color,
    backgroundColor: config.background,
  };

  return (
    <span
      aria-hidden="true"
      title={config.label}
      className={[
        "inline-flex shrink-0 items-center justify-center rounded-md",
        className,
      ].filter(Boolean).join(" ")}
      style={style}
    >
      <svg
        width={Math.max(12, Math.round(size * 0.7))}
        height={Math.max(12, Math.round(size * 0.7))}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.75"
        strokeLinecap="round"
        strokeLinejoin="round"
        xmlns="http://www.w3.org/2000/svg"
      >
        <FileTypeGlyph type={config.icon} />
      </svg>
    </span>
  );
}
