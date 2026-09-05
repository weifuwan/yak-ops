import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton } from '@/components/ui';
import { useIntl } from '@umijs/max';
import { Plus } from 'lucide-react';

interface DashboardEmptyStateProps {
  filtered: boolean;
  onReset: () => void;
  onCreate: () => void;
}

const DashboardEmptyState = ({
  filtered,
  onReset,
  onCreate,
}: DashboardEmptyStateProps) => {
  const intl = useIntl();

  return (
    <div className="flex min-h-[420px] items-center justify-center pb-10">
      <div className="flex flex-col items-center text-center">
        <YakOpsEmpty
          width={180}
          height={120}
          title={intl.formatMessage({
            id: filtered
              ? 'pages.dashboard.list.empty.filtered.title'
              : 'pages.dashboard.list.empty.title',
          })}
          description={intl.formatMessage({
            id: filtered
              ? 'pages.dashboard.list.empty.filtered.description'
              : 'pages.dashboard.list.empty.description',
          })}
          showCaption
        />

        <div className="mt-3">
          {filtered ? (
            <YakButton size="small" onClick={onReset}>
              {intl.formatMessage({ id: 'pages.dashboard.list.empty.reset' })}
            </YakButton>
          ) : (
            <YakButton
              type="primary"
              size="small"
              icon={<Plus size={14} />}
              onClick={onCreate}
            >
              {intl.formatMessage({ id: 'pages.dashboard.list.create' })}
            </YakButton>
          )}
        </div>
      </div>
    </div>
  );
};

export default DashboardEmptyState;
