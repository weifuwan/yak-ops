import { Tabs as BaseTabs } from "@base-ui/react/tabs";

import { cn } from "../cn";

export const Tabs = BaseTabs.Root;
export type TabsProps = BaseTabs.Root.Props;

export type TabsListProps = Omit<BaseTabs.List.Props, "className"> & { className?: string };
export function TabsList({ className, ...props }: TabsListProps) {
  return (
    <BaseTabs.List
      className={cn(
        "relative flex items-end gap-6 border-b border-[var(--yak-components-control-border)]",
        className,
      )}
      {...props}
    />
  );
}

export type TabsTabProps = Omit<BaseTabs.Tab.Props, "className"> & { className?: string };
export function TabsTab({ className, ...props }: TabsTabProps) {
  return (
    <BaseTabs.Tab
      className={cn(
        "relative cursor-pointer px-1 pb-2.5 text-[13px] font-medium text-[var(--yak-components-tab-text)] outline-none",
        "after:pointer-events-none after:absolute after:inset-x-0 after:-bottom-px after:h-0.5 after:origin-center after:scale-x-0 after:rounded-full after:bg-[var(--yak-components-tab-active)] after:content-['']",
        "after:transition-transform after:duration-200 after:ease-out",
        "hover:text-[var(--yak-components-tab-text-hover)] data-active:font-semibold data-active:text-[var(--yak-components-tab-text-active)] data-active:after:scale-x-100",
        "focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-input-focus-ring)] data-disabled:cursor-not-allowed data-disabled:opacity-40",
        className,
      )}
      {...props}
    />
  );
}

export type TabsPanelProps = Omit<BaseTabs.Panel.Props, "className"> & { className?: string };
export function TabsPanel({ className, ...props }: TabsPanelProps) {
  return <BaseTabs.Panel className={cn("outline-none", className)} {...props} />;
}
