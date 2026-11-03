import { CollapseSection, Field, FieldLabel } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

export function ContentSection({
  id,
  title,
  children,
  extra,
}: {
  id: string;
  title: string;
  children: ReactNode;
  extra?: ReactNode;
}) {
  return (
    <CollapseSection
      id={id}
      title={title}
      extra={extra}
      triggerClassName="!h-8 rounded-sm px-3"
      contentClassName="space-y-3 pt-3"
    >
      {children}
    </CollapseSection>
  );
}

export function ContentField({
  label,
  children,
  description,
  required = false,
  wide = false,
}: {
  label: string;
  children: ReactNode;
  description?: ReactNode;
  required?: boolean;
  wide?: boolean;
}) {
  return (
    <Field className="grid grid-cols-[148px_minmax(0,1fr)] items-start gap-x-3 gap-y-1 max-md:grid-cols-1">
      <FieldLabel required={required} className="pt-1 text-xs font-normal">
        {label}
      </FieldLabel>
      <div className={wide ? "min-w-0" : "min-w-0 max-w-lg"}>
        {children}
        {description ? (
          <p className="mt-1 text-xs text-[var(--yak-components-muted-text)]">{description}</p>
        ) : null}
      </div>
    </Field>
  );
}
