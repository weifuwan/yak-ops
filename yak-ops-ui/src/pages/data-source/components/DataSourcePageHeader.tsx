import { YakButton } from '@/components/ui';
import { useIntl } from '@umijs/max';
import { Database, Plus } from 'lucide-react';

interface DataSourcePageHeaderProps {
  canCreate: boolean;
  onCreate: () => void;
}

const DataSourcePageHeader = ({
  canCreate,
  onCreate,
}: DataSourcePageHeaderProps) => {
  const intl = useIntl();

  return (
    <header className="flex items-center justify-between gap-6">
      <div className="flex min-w-0 items-center gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-[11px] border border-[rgba(31,35,41,0.07)] bg-[#f7f8fa] text-[#4f5968]">
          <Database size={19} strokeWidth={1.85} />
        </span>
        <h1 className="m-0 text-xl font-semibold leading-7 tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.datasource.page.title' })}
        </h1>
      </div>

      {canCreate ? (
        <YakButton
          type="primary"
          icon={<Plus size={16} strokeWidth={2.1} />}
          className="!h-9 !shrink-0 !rounded-[10px] !px-4 !text-[13px]"
          onClick={onCreate}
        >
          {intl.formatMessage({ id: 'pages.datasource.page.create' })}
        </YakButton>
      ) : null}
    </header>
  );
};

export default DataSourcePageHeader;
