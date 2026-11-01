import { Radio as BaseRadio } from "@base-ui/react/radio";
import { RadioGroup as BaseRadioGroup } from "@base-ui/react/radio-group";
import { cn } from "../cn";

export const RadioGroup = BaseRadioGroup;

export function Radio({
  className,
  ...props
}: Omit<BaseRadio.Root.Props<string>, "className"> & { className?: string }) {
  return (
    <BaseRadio.Root
      {...props}
      className={cn(
        "inline-flex size-4 shrink-0 cursor-pointer items-center justify-center rounded-full border border-[var(--yak-components-checkbox-border)] bg-[var(--yak-components-checkbox-bg)] outline-none data-checked:border-[var(--yak-color-primary)] focus-visible:ring-[3px] focus-visible:ring-[var(--yak-components-checkbox-focus-ring)] data-disabled:cursor-not-allowed data-disabled:opacity-45",
        className,
      )}
    >
      <BaseRadio.Indicator className="size-2 rounded-full bg-[var(--yak-color-primary)]" />
    </BaseRadio.Root>
  );
}
