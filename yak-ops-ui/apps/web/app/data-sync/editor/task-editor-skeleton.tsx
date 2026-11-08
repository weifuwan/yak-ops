import type { ReactNode } from "react";
import { Skeleton } from "@yak-ops/yak-ui";
import { ChevronRight } from "lucide-react";

function SectionSkeleton({
  children,
  titleWidth,
}: {
  children: ReactNode;
  titleWidth: number;
}) {
  return (
    <section className="space-y-3">
      <div className="flex h-8 items-center gap-2 rounded-sm border border-[var(--yak-components-collapse-section-border)] bg-[var(--yak-components-collapse-section-bg)] px-3">
        <ChevronRight
          aria-hidden="true"
          size={14}
          className="text-[var(--yak-components-collapse-section-icon)]"
        />
        <Skeleton width={titleWidth} height={14} />
      </div>
      <div className="space-y-3 pt-3">{children}</div>
    </section>
  );
}

function DataSourceCardSkeleton() {
  return (
    <div className="rounded-sm border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-3">
      <Skeleton width={44} height={14} />
      <div className="mt-2 space-y-2">
        <div className="grid grid-cols-[90px_minmax(0,1fr)] items-center gap-3">
          <Skeleton width={38} height={13} />
          <Skeleton width={94} height={16} />
        </div>
        <div className="grid grid-cols-[90px_minmax(0,1fr)] items-center gap-3">
          <Skeleton width={48} height={13} />
          <Skeleton height={32} />
        </div>
      </div>
    </div>
  );
}

function FieldSkeleton({ wide = false }: { wide?: boolean }) {
  return (
    <div className="grid grid-cols-[148px_minmax(0,1fr)] items-start gap-x-3 gap-y-1 max-md:grid-cols-1">
      <Skeleton width={72} height={13} className="mt-1" />
      <div className={wide ? "min-w-0" : "min-w-0 max-w-lg"}>
        <Skeleton height={32} />
      </div>
    </div>
  );
}

export function TaskEditorSkeleton() {
  return (
    <div
      role="status"
      aria-label="正在加载同步任务"
      className="flex h-full min-h-0 flex-col overflow-hidden bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-panel-text)]"
    >
      <header className="flex w-full shrink-0 flex-wrap items-start justify-between gap-x-6 gap-y-3 border-b border-[var(--yak-components-page-header-border)] bg-[var(--yak-components-panel-bg)] px-6 py-4 max-md:px-4">
        <div className="flex min-w-0 basis-full items-center gap-2 sm:basis-auto sm:flex-1">
          <Skeleton width={84} height={28} />
          <Skeleton width={8} height={14} />
          <Skeleton width={168} height={16} />
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <Skeleton width={76} height={28} />
          <Skeleton width={56} height={28} />
        </div>
      </header>

      <div className="flex min-h-0 flex-1 gap-4 px-4">
        <main className="min-h-0 min-w-0 flex-1 space-y-3 overflow-y-auto pb-6 pt-4">
          <SectionSkeleton titleWidth={54}>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
              <DataSourceCardSkeleton />
              <DataSourceCardSkeleton />
            </div>
          </SectionSkeleton>
          <SectionSkeleton titleWidth={60}>
            <FieldSkeleton />
            <FieldSkeleton />
          </SectionSkeleton>
          <SectionSkeleton titleWidth={74}>
            <FieldSkeleton />
          </SectionSkeleton>
          <SectionSkeleton titleWidth={98}>
            <Skeleton width={210} height={14} />
            <Skeleton height={90} />
          </SectionSkeleton>
        </main>

        <aside className="hidden w-[176px] shrink-0 pt-4 lg:block" aria-hidden="true">
          <div className="space-y-10">
            <Skeleton width={108} height={20} />
            <Skeleton width={84} height={20} />
            <Skeleton width={100} height={20} />
            <Skeleton width={112} height={20} />
          </div>
        </aside>
      </div>

      <div className="flex shrink-0 items-center justify-between gap-3 border-t border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-6 py-3 max-md:px-4">
        <div className="flex items-center gap-2">
          <Skeleton width={58} height={32} />
          <Skeleton width={58} height={32} />
          <Skeleton width={58} height={32} />
        </div>
        <Skeleton width={42} height={14} />
      </div>
    </div>
  );
}
