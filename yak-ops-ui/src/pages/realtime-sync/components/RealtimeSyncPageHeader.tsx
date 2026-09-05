import { YakButton } from '@/components/ui';
import type { RuntimeCapabilities } from '@/services/realtime-sync';
import { PlusOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Descriptions, Popover } from 'antd';

interface RealtimeSyncPageHeaderProps {
  capabilities: RuntimeCapabilities;
  streamConnected: boolean;
  onCreate: () => void;
}

const RealtimeSyncPageHeader = ({
  capabilities,
  streamConnected,
  onCreate,
}: RealtimeSyncPageHeaderProps) => {
  const intl = useIntl();

  return (
    <header className="flex items-start justify-between gap-6">
      <div className="min-w-0">
        <h1 className="m-0 text-[17px] font-semibold leading-7 text-[#101828]">
          {intl.formatMessage({ id: 'pages.realtimeSync.page.title' })}
        </h1>

        <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-[12px] leading-5 text-[#98a2b3]">
          <span>
            {intl.formatMessage({ id: 'pages.realtimeSync.page.description' })}
          </span>

          <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
            <span
              className={[
                'h-1.5 w-1.5 rounded-full',
                streamConnected ? 'bg-[#12b76a]' : 'bg-[#d0d5dd]',
              ].join(' ')}
            />
            {intl.formatMessage({
              id: streamConnected
                ? 'pages.realtimeSync.page.streamConnected'
                : 'pages.realtimeSync.page.polling',
            })}
          </span>

          <Popover
            placement="bottomLeft"
            content={
              <Descriptions size="small" column={1} className="w-[420px]">
                <Descriptions.Item
                  label={intl.formatMessage({
                    id: 'pages.realtimeSync.capabilities.defaultEnvironment',
                  })}
                >
                  {capabilities.runtimeEnvironmentName || '-'}
                </Descriptions.Item>
                <Descriptions.Item
                  label={intl.formatMessage({
                    id: 'pages.realtimeSync.capabilities.submitEngine',
                  })}
                >
                  {capabilities.runtimeVersion || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="Flink">
                  {capabilities.flinkVersion || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="Flink CDC">
                  {capabilities.flinkCdcVersion || '-'}
                </Descriptions.Item>
                <Descriptions.Item label="REST">
                  {capabilities.restUrl || '-'}
                </Descriptions.Item>
                <Descriptions.Item
                  label={intl.formatMessage({
                    id: 'pages.realtimeSync.capabilities.semantics',
                  })}
                >
                  {capabilities.deliverySemantics || '-'}
                </Descriptions.Item>
                <Descriptions.Item
                  label={intl.formatMessage({
                    id: 'pages.realtimeSync.capabilities.note',
                  })}
                >
                  {intl.formatMessage({
                    id: 'pages.realtimeSync.capabilities.noteContent',
                  })}
                </Descriptions.Item>
              </Descriptions>
            }
          >
            <button
              type="button"
              className="border-0 bg-transparent p-0 text-[12px] font-medium leading-5 text-[#667085] transition-colors hover:text-[#344054]"
            >
              {intl.formatMessage({ id: 'pages.realtimeSync.page.defaultEnvironment' })}{' '}
              ·{' '}
              {capabilities.runtimeEnvironmentName ||
                intl.formatMessage({ id: 'pages.realtimeSync.page.notConfigured' })}
            </button>
          </Popover>
        </div>
      </div>

      <YakButton
        type="primary"
        size="small"
        icon={<PlusOutlined />}
        className="!h-9 !shrink-0 !rounded-[9px] !px-4"
        onClick={onCreate}
      >
        {intl.formatMessage({ id: 'pages.realtimeSync.page.create' })}
      </YakButton>
    </header>
  );
};

export default RealtimeSyncPageHeader;
