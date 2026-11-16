export type StandardIconName =
  | "field-standard"
  | "standard-code"
  | "unit-standard"
  | "naming-dictionary";

type StandardIconProps = {
  name: StandardIconName;
  size?: number;
  className?: string;
};

/**
 * Renders a data-standard SVG as a CSS mask so it inherits the menu color.
 */
export function StandardIcon({
  name,
  size = 16,
  className = "",
}: StandardIconProps) {
  const iconUrl = `${import.meta.env.BASE_URL}icons/data-modeling/standards/${name}.svg`;

  return (
    <span
      aria-hidden="true"
      className={["inline-block shrink-0 bg-current", className]
        .filter(Boolean)
        .join(" ")}
      style={{
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
      }}
    />
  );
}
