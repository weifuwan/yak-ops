import type { ReactNode } from "react";
import { PageHeader } from "@yak-ops/yak-ui";

export function OperationsPageShell({
  title,
  extra,
  children,
}: {
  title: string;
  extra?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader title={title} bordered className="bg-white px-6 max-md:px-4" extra={extra} />
      <div className="flex min-h-0 min-w-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
        <section className="flex min-h-0 min-w-0 flex-1 flex-col bg-white p-4">
          {children}
        </section>
      </div>
    </div>
  );
}
