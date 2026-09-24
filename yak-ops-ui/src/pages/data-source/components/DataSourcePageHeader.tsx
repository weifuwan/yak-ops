import { Button } from '@/shared/ui';
import { useIntl } from '@/pages/data-source/i18n';
import { Plus } from 'lucide-react';

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
        <h1 className="m-0 text-xl font-semibold leading-7 tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.datasource.page.title' })}
        </h1>
      </div>

      {canCreate ? (
        <Button variant="primary" className="shrink-0" onClick={onCreate}>
          <Plus size={16} strokeWidth={2.1} />
          {intl.formatMessage({ id: 'pages.datasource.page.create' })}
        </Button>
      ) : null}
    </header>
  );
};

export default DataSourcePageHeader;
