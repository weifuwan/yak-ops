import { Button, Input, toast } from "@yak-ops/yak-ui";
import { Upload, X } from "lucide-react";
import { useRef, useState } from "react";

import { uploadDataSourceDriver } from "../../api";
import { useIntl } from "../../i18n";

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
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const inputPlaceholder =
    placeholder ||
    intl.formatMessage({ id: "pages.datasource.driver.placeholder" });

  const handleFile = async (file?: File) => {
    if (!file || disabled || uploading) return;

    if (!file.name.toLowerCase().endsWith(".jar")) {
      toast.error(intl.formatMessage({ id: "pages.datasource.driver.jarOnly" }));
      return;
    }

    if (file.size / 1024 / 1024 > maxSizeMB) {
      toast.error(
        intl.formatMessage(
          { id: "pages.datasource.driver.maxSize" },
          { maxSizeMB },
        ),
      );
      return;
    }

    try {
      setUploading(true);
      const driverLocation = await uploadDataSourceDriver(dbType, file);
      onChange?.(driverLocation);
      toast.success(
        intl.formatMessage({ id: "pages.datasource.driver.uploadSuccess" }),
      );
    } catch (error) {
      toast.error(
        error instanceof Error
          ? error.message
          : intl.formatMessage({ id: "pages.datasource.driver.uploadFailed" }),
      );
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

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
              aria-label={intl.formatMessage({
                id: "pages.datasource.driver.clear",
              })}
              className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2 p-0 text-[#98a2b3]"
              onClick={() => onChange?.("")}
            >
              <X size={13} strokeWidth={1.8} />
            </Button>
          ) : null}
        </div>

        <input
          ref={fileInputRef}
          type="file"
          accept=".jar,application/java-archive"
          className="hidden"
          disabled={disabled || uploading}
          onChange={(event) => void handleFile(event.target.files?.[0])}
        />
        <Button
          className="shrink-0"
          loading={uploading}
          disabled={disabled}
          onClick={() => fileInputRef.current?.click()}
        >
          <Upload size={14} />
          {intl.formatMessage({ id: "pages.datasource.driver.upload" })}
        </Button>
      </div>

      <div className="mt-1.5 text-[11px] leading-4 text-[#98a2b3]">
        {intl.formatMessage(
          { id: "pages.datasource.driver.hint" },
          { maxSizeMB },
        )}
      </div>
    </div>
  );
};

export default DriverManager;
