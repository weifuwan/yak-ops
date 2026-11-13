import { ArrowRight, CheckCircle2, Layers3 } from "lucide-react";
import { Card, CardContent, PageHeader } from "@yak-ops/yak-ui";

type DataModelingSectionPageProps = {
  title: string;
  description: string;
  capabilities: string[];
};

export function DataModelingSectionPage({
  title,
  description,
  capabilities,
}: DataModelingSectionPageProps) {
  return (
    <div className="min-h-full bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader
        title={title}
        bordered
        className="bg-[var(--yak-components-panel-bg)] px-6 max-md:px-4"
      />
      <div className="px-6 pb-8 pt-5 max-md:px-4">
        <Card>
          <CardContent className="p-6">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-hover)] text-[var(--yak-color-primary)]">
                <Layers3 size={19} />
              </div>
              <div className="min-w-0">
                <div className="text-sm font-semibold">{title}模块已纳入数据建模工作台</div>
                <p className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
                  {description}
                </p>
              </div>
            </div>

            <div className="mt-6 border-t border-[var(--yak-components-panel-border)] pt-5">
              <div className="mb-3 text-xs font-medium">计划迁移的业务能力</div>
              <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-3">
                {capabilities.map((capability) => (
                  <div
                    key={capability}
                    className="flex items-center gap-2 rounded-md bg-[var(--yak-color-hover)] px-3 py-2.5 text-xs"
                  >
                    <CheckCircle2
                      size={14}
                      className="shrink-0 text-[var(--yak-components-muted-text)]"
                    />
                    <span>{capability}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="mt-5 flex items-center gap-2 rounded-md border border-dashed border-[var(--yak-components-panel-border)] px-3 py-3 text-xs text-[var(--yak-components-muted-text)]">
              <ArrowRight size={14} />
              业务功能将在后续迁移阶段接入，当前页面先保留统一的产品导航和布局。
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

export default DataModelingSectionPage;
