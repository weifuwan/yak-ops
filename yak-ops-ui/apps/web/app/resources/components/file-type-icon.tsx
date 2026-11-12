import { getResourceFileTypeConfig } from "../file-types";

/**
 * Every supported resource type uses the matching SVG from public/icons/resource-file-types.
 * XML keeps the original inline glyph until a dedicated asset is supplied.
 */
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

  return (
    <span
      aria-hidden="true"
      title={config.label}
      className={[
        "inline-flex shrink-0 items-center justify-center",
        className,
      ].filter(Boolean).join(" ")}
      style={{ width: size, height: size }}
    >
      {config.icon === "xml" ? (
        <svg
          width={size}
          height={size}
          viewBox="0 0 24 24"
          fill="none"
          stroke={config.color}
          strokeWidth="1.75"
          strokeLinecap="round"
          strokeLinejoin="round"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path d="m8.5 6-5 6 5 6M15.5 6l5 6-5 6M13.5 4l-3 16" />
        </svg>
      ) : (
        <img
          src={`${import.meta.env.BASE_URL}icons/resource-file-types/${config.icon}.svg`}
          width={size}
          height={size}
          alt=""
          draggable={false}
          className="block size-full object-contain"
        />
      )}
    </span>
  );
}
