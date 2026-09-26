import { PageHeader } from "@yak-ops/yak-ui";

type ManagementPageShellProps = {
  title: string;
};

export default function ManagementPageShell({ title }: ManagementPageShellProps) {
  return (
    <div className="flex min-h-full flex-col bg-[#F6F6F6] text-[#242731]">
      <PageHeader title={title} bordered className="bg-white px-6 max-md:px-4" />
      <div className="min-h-0 flex-1" />
    </div>
  );
}
