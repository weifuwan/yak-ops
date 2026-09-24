import { Button, Input } from '@yak-ops/yak-ui';
import { uploadDataSourceDriver } from '../../api';
import { UploadOutlined } from '@ant-design/icons';
import { useIntl } from '../../i18n';
import { message, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { X } from 'lucide-react';
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
        <div className="relative min-w-0 flex-1">
          <Input
            value={value}
            disabled={disabled}
            placeholder={inputPlaceholder}
            className={value && !disabled ? "pr-9" : undefined}
            onChange={(event) => onChange?.(event.target.value)}
          />
          {value && !disabled ? (
            <Button
              variant="ghost"
              size="small"
              type="button"
              aria-label={intl.formatMessage({ id: 'pages.datasource.driver.clear' })}
              className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2 p-0 text-[#98a2b3]"
              onClick={() => onChange?.('')}
            >
              <X size={13} strokeWidth={1.8} />
            </Button>
          ) : null}
        </div>

        <Upload {...uploadProps}>
          <Button
            className="shrink-0"
            loading={uploading}
            disabled={disabled}
          >
            <UploadOutlined />
            {intl.formatMessage({ id: 'pages.datasource.driver.upload' })}
          </Button>
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
