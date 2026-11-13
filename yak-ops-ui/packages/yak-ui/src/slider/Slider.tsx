import { Slider as BaseSlider } from "@base-ui/react/slider";

import { cn } from "../cn";

type SliderRootValue = NonNullable<BaseSlider.Root.Props["value"]>;

export type SliderProps<Value extends SliderRootValue = SliderRootValue> = Omit<
  BaseSlider.Root.Props<Value>,
  "className"
> & {
  className?: string;
};

export function Slider<Value extends SliderRootValue = SliderRootValue>({
  className,
  ...props
}: SliderProps<Value>) {
  return (
    <BaseSlider.Root
      {...props}
      className={cn(
        "group/slider relative flex w-full min-w-0 data-[orientation=vertical]:w-auto data-[orientation=vertical]:flex-col",
        "data-disabled:opacity-45",
        className,
      )}
    />
  );
}

export type SliderLabelProps = Omit<BaseSlider.Label.Props, "className"> & {
  className?: string;
};

export function SliderLabel({ className, ...props }: SliderLabelProps) {
  return (
    <BaseSlider.Label
      className={cn(
        "text-xs font-medium text-[var(--yak-components-field-label)]",
        className,
      )}
      {...props}
    />
  );
}

export type SliderValueProps = Omit<BaseSlider.Value.Props, "className"> & {
  className?: string;
};

export function SliderValue({ className, ...props }: SliderValueProps) {
  return (
    <BaseSlider.Value
      className={cn(
        "text-xs tabular-nums text-[var(--yak-components-muted-text)]",
        className,
      )}
      {...props}
    />
  );
}

export type SliderControlProps = Omit<BaseSlider.Control.Props, "className"> & {
  className?: string;
};

export function SliderControl({ className, ...props }: SliderControlProps) {
  return (
    <BaseSlider.Control
      className={cn(
        "relative flex h-5 w-full touch-none items-center select-none",
        "data-[orientation=vertical]:h-32 data-[orientation=vertical]:w-5 data-[orientation=vertical]:justify-center",
        "data-disabled:cursor-not-allowed",
        className,
      )}
      {...props}
    />
  );
}

export type SliderTrackProps = Omit<BaseSlider.Track.Props, "className"> & {
  className?: string;
};

export function SliderTrack({ className, ...props }: SliderTrackProps) {
  return (
    <BaseSlider.Track
      className={cn(
        "relative h-1 w-full rounded-full bg-[var(--yak-components-slider-track)]",
        "data-[orientation=vertical]:h-full data-[orientation=vertical]:w-1",
        className,
      )}
      {...props}
    />
  );
}

export type SliderIndicatorProps = Omit<BaseSlider.Indicator.Props, "className"> & {
  className?: string;
};

export function SliderIndicator({ className, ...props }: SliderIndicatorProps) {
  return (
    <BaseSlider.Indicator
      className={cn(
        "h-full rounded-full bg-[var(--yak-components-slider-range)]",
        className,
      )}
      {...props}
    />
  );
}

export type SliderThumbProps = Omit<BaseSlider.Thumb.Props, "className"> & {
  className?: string;
};

export function SliderThumb({ className, ...props }: SliderThumbProps) {
  return (
    <BaseSlider.Thumb
      className={cn(
        "block h-5 w-2 shrink-0 rounded-[3px] border-[0.5px]",
        "border-[var(--yak-components-slider-thumb-border)] bg-[var(--yak-components-slider-thumb-bg)] shadow-sm",
        "transition-[background-color,border-color,box-shadow,opacity] motion-reduce:transition-none",
        "hover:bg-[var(--yak-components-slider-thumb-hover)]",
        "has-focus-visible:ring-2 has-focus-visible:ring-[var(--yak-components-slider-focus-ring)] has-focus-visible:ring-offset-0",
        "active:shadow-md group-data-dragging/slider:has-focus:shadow-md",
        "data-disabled:cursor-not-allowed data-disabled:shadow-none",
        "data-[orientation=vertical]:h-2 data-[orientation=vertical]:w-5",
        className,
      )}
      {...props}
    />
  );
}
