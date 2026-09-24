import { Collapsible as BaseCollapsible } from "@base-ui/react/collapsible";

import { cn } from "../cn";

export const Collapsible = BaseCollapsible.Root;
export const CollapsibleTrigger = BaseCollapsible.Trigger;

export type CollapsiblePanelProps = Omit<BaseCollapsible.Panel.Props, "className"> & { className?: string };
export function CollapsiblePanel({ className, ...props }: CollapsiblePanelProps) {
  return (
    <BaseCollapsible.Panel
      className={cn(
        "h-[var(--collapsible-panel-height)] overflow-hidden transition-[height] duration-150 data-starting-style:h-0 data-ending-style:h-0 motion-reduce:transition-none",
        className,
      )}
      {...props}
    />
  );
}
