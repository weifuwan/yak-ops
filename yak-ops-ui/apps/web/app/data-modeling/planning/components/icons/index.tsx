export type PlanningIconName =
  | "business-type"
  | "dw-layer"
  | "data-domain"
  | "business-process"
  | "data-mart"
  | "subject-domain";

type PlanningIconProps = {
  name: PlanningIconName;
  size?: number;
  className?: string;
};

/**
 * Renders a public SVG as a CSS mask so the icon inherits the active menu color.
 */
export function PlanningIcon({
  name,
  size = 16,
  className = "",
}: PlanningIconProps) {
  const iconUrl = `${import.meta.env.BASE_URL}icons/data-modeling/planning/${name}.svg`;

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
