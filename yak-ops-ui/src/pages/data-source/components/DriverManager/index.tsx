import { YakButton } from '@/components/ui';
import { uploadDataSourceDriver } from '@/services/data-source';
import { UploadOutlined } from '@ant-design/icons';
import { useIntl } from '@umijs/max';
import { Input, message, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { useMemo, useState } from 'react';

const DEFAULT_MAX_SIZE_MB = 200;

export interface DriverManagerProps {
  dbType: string;
  value?: string;
  onChange?: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  maxSizeMB?: number;
}

const DriverManager = ({
  dbType,
  value,
  onChange,
  placeholder,
  disabled = false,
  maxSizeMB = DEFAULT_MAX_SIZE_MB,
}: DriverManagerProps) => {
  const intl = useIntl();
  const [uploading, setUploading] = useState(false);
  const inputPlaceholder =
    placeholder ||
    intl.formatMessage({ id: 'pages.datasource.driver.placeholder' });

  const uploadProps = useMemo<UploadProps>(
    () => ({
      accept: '.jar,application/java-archive',
      multiple: false,
      showUploadList: false,
      disabled: disabled || uploading,
      beforeUpload: (file) => {
        if (!file.name.toLowerCase().endsWith('.jar')) {
          message.error(
            intl.formatMessage({ id: 'pages.datasource.driver.jarOnly' }),
          );
          return Upload.LIST_IGNORE;
        }

        if (file.size / 1024 / 1024 > maxSizeMB) {
          message.error(
            intl.formatMessage(
              { id: 'pages.datasource.driver.maxSize' },
              { maxSizeMB },
            ),
          );
          return Upload.LIST_IGNORE;
        }

        return true;
      },
      customRequest: async ({ file, onSuccess, onError }) => {
        try {
          setUploading(true);
          const driverLocation = await uploadDataSourceDriver(
            dbType,
            file as File,
          );
          onChange?.(driverLocation);
          message.success(
            intl.formatMessage({ id: 'pages.datasource.driver.uploadSuccess' }),
          );
          onSuccess?.({ driverLocation });
        } catch (error) {
          const uploadError =
            error instanceof Error
              ? error
              : new Error(
                  intl.formatMessage({
                    id: 'pages.datasource.driver.uploadFailed',
                  }),
                );
          message.error(uploadError.message);
          onError?.(uploadError);
        } finally {
          setUploading(false);
        }
      },
    }),
    [dbType, disabled, intl, maxSizeMB, onChange, uploading],
  );

  return (
    <div className="w-full">
      <div className="flex w-full items-center gap-2">
        <Input
          variant="filled"
          value={value}
          disabled={disabled}
          allowClear
          placeholder={inputPlaceholder}
          onChange={(event) => onChange?.(event.target.value)}
        />

        <Upload {...uploadProps}>
          <YakButton
            className="shrink-0"
            icon={<UploadOutlined />}
            loading={uploading}
            disabled={disabled}
          >
            {intl.formatMessage({ id: 'pages.datasource.driver.upload' })}
          </YakButton>
        </Upload>
      </div>

      <div className="mt-1.5 text-[11px] leading-4 text-[#98a2b3]">
        {intl.formatMessage(
          { id: 'pages.datasource.driver.hint' },
          { maxSizeMB },
        )}
      </div>
    </div>
  );
};

export default DriverManager;
