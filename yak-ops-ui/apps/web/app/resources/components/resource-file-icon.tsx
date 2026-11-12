import {
  Archive,
  File,
  FileCode2,
  FileImage,
  FileSpreadsheet,
  FileText,
  Folder,
} from "lucide-react";

import type { ResourceItemRecord } from "@/service/resources";

import { getExtension, getName, isDirectoryItem } from "../utils";

const getFileVisual = (name: string) => {
  const extension = getExtension(name);
  if (["jar", "zip", "gz", "tar", "war", "7z"].includes(extension)) {
    return { Icon: Archive, color: "text-[#2563eb]", background: "bg-[#eff6ff]" };
  }
  if (["sql", "py", "java", "js", "ts", "sh", "json", "xml", "yml", "yaml"].includes(extension)) {
    return { Icon: FileCode2, color: "text-[#2563eb]", background: "bg-[#eff6ff]" };
  }
  if (["xlsx", "xls", "csv"].includes(extension)) {
    return { Icon: FileSpreadsheet, color: "text-[#15966b]", background: "bg-[#eaf8f1]" };
  }
  if (["png", "jpg", "jpeg", "gif", "webp", "svg"].includes(extension)) {
    return { Icon: FileImage, color: "text-[#a855f7]", background: "bg-[#f5edff]" };
  }
  if (["pdf"].includes(extension)) {
    return { Icon: FileText, color: "text-[#e05252]", background: "bg-[#fff0f0]" };
  }
  if (["md", "txt", "log", "properties", "conf", "doc", "docx"].includes(extension)) {
    return { Icon: FileText, color: "text-[#15966b]", background: "bg-[#eaf8f1]" };
  }
  return { Icon: File, color: "text-[#64748b]", background: "bg-[#f1f5f9]" };
};

export function ResourceFileIcon({
  item,
  large = false,
}: {
  item: ResourceItemRecord;
  large?: boolean;
}) {
  if (isDirectoryItem(item)) {
    return (
      <span
        className={[
          "flex shrink-0 items-center justify-center rounded-md bg-[#fff7e6] text-[#e5ad35]",
          large ? "size-10" : "size-8",
        ].join(" ")}
      >
        <Folder size={large ? 23 : 19} strokeWidth={1.65} />
      </span>
    );
  }

  const visual = getFileVisual(getName(item));
  const Icon = visual.Icon;
  return (
    <span
      className={[
        "flex shrink-0 items-center justify-center rounded-md",
        visual.background,
        visual.color,
        large ? "size-10" : "size-8",
      ].join(" ")}
    >
      <Icon size={large ? 21 : 18} strokeWidth={1.65} />
    </span>
  );
}
