import { useEffect, useState } from "react";

import { cn } from "../cn";
import { NumberField, NumberFieldGroup, NumberFieldInput } from "../number-field";
import {
  Slider,
  SliderControl,
  SliderIndicator,
  SliderThumb,
  SliderTrack,
} from "./Slider";

export interface SliderInputProps {
  /** Accessible name shared by the slider and numeric input. */
  label: string;
  /** Current numeric value. */
  value: number;
  /** Called whenever the slider or numeric input changes. */
  onValueChange: (value: number) => void;
  min: number;
  max: number;
  step?: number;
  /** Optional unit displayed inside the value field. */
  unit?: string;
  disabled?: boolean;
  className?: string;
}

/**
 * A compact slider paired with an editable numeric value and optional unit.
 * The value field has a fixed width so it cannot squeeze the slider track.
 */
export function SliderInput({
  label,
  value,
  onValueChange,
  min,
  max,
  step = 1,
  unit,
  disabled = false,
  className,
}: SliderInputProps) {
  const safeValue = Number.isFinite(value)
    ? Math.min(max, Math.max(min, value))
    : min;
  const [inputValue, setInputValue] = useState<number | null>(safeValue);

  useEffect(() => {
    setInputValue(safeValue);
  }, [safeValue]);

  const handleInputValueChange = (nextValue: number | null) => {
    setInputValue(nextValue);
    if (nextValue !== null && Number.isFinite(nextValue)) {
      onValueChange(Math.min(max, Math.max(min, nextValue)));
    }
  };

  return (
    <div
      className={cn(
        "grid w-full min-w-0 grid-cols-[minmax(0,1fr)_80px] items-center gap-2",
        className,
      )}
    >
      <Slider
        value={safeValue}
        min={min}
        max={max}
        step={step}
        disabled={disabled}
        onValueChange={(nextValue) => {
          if (typeof nextValue === "number") onValueChange(nextValue);
        }}
        className="w-full min-w-0"
      >
        <SliderControl className="!h-8 min-w-0">
          <SliderTrack>
            <SliderIndicator />
            <SliderThumb aria-label={label} />
          </SliderTrack>
        </SliderControl>
      </Slider>

      <div className="w-full min-w-0">
        <NumberField
          value={inputValue}
          min={min}
          max={max}
          step={step}
          disabled={disabled}
          onValueChange={handleInputValueChange}
        >
          <NumberFieldGroup
            size="small"
            className="!h-8 !w-full !rounded-lg !bg-[var(--yak-components-slider-value-bg)] hover:!bg-[var(--yak-components-slider-value-bg-hover)]"
          >
            <NumberFieldInput
              aria-label={label + "数值"}
              className="!px-2 text-left tabular-nums"
              onBlur={() => setInputValue(safeValue)}
            />
            {unit ? (
              <span className="flex shrink-0 items-center pr-2 text-xs text-[var(--yak-components-muted-text)]">
                {unit}
              </span>
            ) : null}
          </NumberFieldGroup>
        </NumberField>
      </div>
    </div>
  );
}
