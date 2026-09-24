import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "../cn";

const spinnerVariants = cva("inline-block animate-spin rounded-full border-2 border-current border-r-transparent motion-reduce:animate-none", {
  variants: {
    size: {
      small: "size-3",
      medium: "size-4",
      large: "size-5",
    },
  },
  defaultVariants: { size: "medium" },
});

export type SpinnerProps = VariantProps<typeof spinnerVariants> & {
  className?: string;
  label?: string;
};

export function Spinner({ className, label = "Loading", size }: SpinnerProps) {
  return (
    <span role="status" aria-label={label} className={cn("text-[var(--yak-components-spinner)]", spinnerVariants({ size }), className)}>
      <span className="sr-only">{label}</span>
    </span>
  );
}
