import type { Field as BaseFieldNS } from "@base-ui/react/field";
import { Field as BaseField } from "@base-ui/react/field";

import { cn } from "../cn";

export type FieldProps = Omit<BaseFieldNS.Root.Props, "className"> & { className?: string };
export function Field({ className, ...props }: FieldProps) {
  return <BaseField.Root className={cn("grid min-w-0 gap-1.5", className)} {...props} />;
}

export type FieldLabelProps = Omit<BaseFieldNS.Label.Props, "className"> & { className?: string };
export function FieldLabel({ className, ...props }: FieldLabelProps) {
  return (
    <BaseField.Label
      className={cn(
        "w-fit text-[13px] font-medium leading-5 text-[var(--yak-components-field-label)] data-disabled:cursor-not-allowed",
        className,
      )}
      {...props}
    />
  );
}

export type FieldDescriptionProps = Omit<BaseFieldNS.Description.Props, "className"> & {
  className?: string;
};
export function FieldDescription({ className, ...props }: FieldDescriptionProps) {
  return (
    <BaseField.Description
      className={cn(
        "text-[11px] leading-4 text-[var(--yak-components-field-description)]",
        className,
      )}
      {...props}
    />
  );
}

export type FieldErrorProps = Omit<BaseFieldNS.Error.Props, "className"> & { className?: string };
export function FieldError({ className, ...props }: FieldErrorProps) {
  return (
    <BaseField.Error
      className={cn("text-[11px] leading-4 text-[var(--yak-components-field-error)]", className)}
      {...props}
    />
  );
}

export const FieldValidity = BaseField.Validity;
export type FieldValidityProps = BaseFieldNS.Validity.Props;
