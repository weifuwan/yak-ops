type Props = {
  title: string;
};

export function IndicatorSectionHeader({ title }: Props) {
  return (
    <div className="mb-3 flex min-w-0 items-center gap-2">
      <span aria-hidden="true" className="h-4 w-1 shrink-0 bg-[#5664ff]" />
      <h3 className="m-0 shrink-0 text-sm font-semibold leading-5 text-[var(--yak-components-panel-text)]">
        {title}
      </h3>
      <span
        aria-hidden="true"
        className="h-px min-w-0 flex-1 bg-[var(--yak-components-panel-border)]"
      />
    </div>
  );
}
