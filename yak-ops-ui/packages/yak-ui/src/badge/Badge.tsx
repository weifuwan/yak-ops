import { cva, type VariantProps } from "class-variance-authority";
import type { HTMLAttributes } from "react";

import { cn } from "../cn";

const badgeVariants = cva("inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium", {
  variants: {
    tone: {
      neutral: "bg-[var(--yak-components-badge-neutral-bg)] text-[var(--yak-components-badge-neutral-text)]",
      success: "bg-[var(--yak-components-badge-success-bg)] text-[var(--yak-components-badge-success-text)]",
      warning: "bg-[var(--yak-components-badge-warning-bg)] text-[var(--yak-components-badge-warning-text)]",
      danger: "bg-[var(--yak-components-badge-danger-bg)] text-[var(--yak-components-badge-danger-text)]",
      info: "bg-[var(--yak-components-badge-info-bg)] text-[var(--yak-components-badge-info-text)]",
    },
  },
  defaultVariants: { tone: "neutral" },
});

export type BadgeProps = HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeVariants>;

export function Badge({ className, tone, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}
