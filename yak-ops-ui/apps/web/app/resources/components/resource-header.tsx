import { useRef, type ChangeEvent } from "react";
import { FolderPlus, Upload } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

interface ResourceHeaderProps {
  canManage: boolean;
  busy: boolean;
  uploading: boolean;
  onCreateFolder: () => void;
  onUpload: (files: File[]) => Promise<void>;
}

export function ResourceHeader({
  canManage,
  busy,
  uploading,
  onCreateFolder,
  onUpload,
}: ResourceHeaderProps) {
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const handleFileChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.currentTarget.files ?? []);
    try {
      await onUpload(files);
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  return (
    <header className="flex h-[60px] shrink-0 items-center justify-between gap-4 border-b border-[#edf0f3] px-6 max-md:px-4">
      <h1 className="text-[17px] font-semibold tracking-[-0.02em] text-[var(--yak-components-panel-text)]">
        资源管理
      </h1>
      <div className="flex shrink-0 items-center gap-2">
        <Button
          size="sm"
          variant="secondary"
          isDisabled={!canManage || busy || uploading}
          onClick={onCreateFolder}
        >
          <FolderPlus size={14} />
          新建文件夹
        </Button>
        <Button
          size="sm"
          isDisabled={!canManage || busy || uploading}
          isPending={uploading}
          onClick={() => fileInputRef.current?.click()}
        >
          <Upload size={14} />
          上传
        </Button>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          className="hidden"
          onChange={(event) => void handleFileChange(event)}
        />
      </div>
    </header>
  );
}
