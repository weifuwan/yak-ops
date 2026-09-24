import { Switch as BaseSwitch } from "@base-ui/react/switch";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "../cn";

const rootVariants = cva(
  [
    "group relative inline-flex shrink-0 cursor-pointer items-center bg-[var(--yak-components-switch-bg)] outline-none transition-colors duration-150",
    "hover:bg-[var(--yak-components-switch-bg-hover)] data-checked:bg-[var(--yak-components-switch-bg-checked)] data-checked:hover:bg-[var(--yak-components-switch-bg-checked-hover)]",
    "focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-input-focus-ring)]",
    "data-disabled:cursor-not-allowed data-disabled:opacity-45 motion-reduce:transition-none",
  ],
  {
    variants: {
      size: {
        small: "h-4 w-7 rounded-full p-0.5",
        medium: "h-5 w-9 rounded-full p-0.5",
        large: "h-6 w-11 rounded-full p-0.5",
      },
    },
    defaultVariants: { size: "medium" },
  },
);

const thumbVariants = cva(
  "block rounded-full bg-white shadow-sm transition-transform duration-150 motion-reduce:transition-none",
  {
    variants: {
      size: {
        small: "size-3 data-checked:translate-x-3",
        medium: "size-4 data-checked:translate-x-4",
        large: "size-5 data-checked:translate-x-5",
      },
    },
    defaultVariants: { size: "medium" },
  },
);

export type SwitchProps = Omit<BaseSwitch.Root.Props, "children" | "className"> &
  VariantProps<typeof rootVariants> & {
    className?: string;
    loading?: boolean;
  };

export function Switch({ className, disabled, loading = false, size, ...props }: SwitchProps) {
  return (
    <BaseSwitch.Root
      {...props}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(rootVariants({ size }), className)}
    >
      <BaseSwitch.Thumb className={thumbVariants({ size })} />
    </BaseSwitch.Root>
  );
}
