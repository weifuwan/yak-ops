import { YakButton } from '@/components/ui';
import { useIntl } from '@umijs/max';
import { Plus } from 'lucide-react';

interface OfflineSyncPageHeaderProps {
  onCreate: () => void;
}

const OfflineSyncPageHeader = ({ onCreate }: OfflineSyncPageHeaderProps) => {
  const intl = useIntl();

  return (
    <div>
      <div className="flex min-h-10 items-start justify-between gap-6">
        <div>
          <h1 className="m-0 text-[17px] font-semibold text-[#161823]">
            {intl.formatMessage({ id: 'pages.batchLinkUp.page.title' })}
          </h1>
        </div>

        <YakButton
          type="primary"
          size="small"
          icon={<Plus size={14} strokeWidth={2} />}
          className="!h-8 !px-3.5"
          onClick={onCreate}
        >
          {intl.formatMessage({ id: 'pages.batchLinkUp.page.create' })}
        </YakButton>
      </div>

      <div className="mt-3 flex min-h-9 items-center rounded-sm bg-[#fff7e6] px-3 text-[12px] text-[#475467]">
        <span className="mr-2 text-[14px] text-[#faad14]">▲</span>
        <span className="font-medium text-[#344054]">
          {intl.formatMessage({ id: 'pages.batchLinkUp.page.tipLabel' })}
        </span>
        <span>
          {intl.formatMessage({ id: 'pages.batchLinkUp.page.tipContent' })}
        </span>
      </div>
    </div>
  );
};

export default OfflineSyncPageHeader;
