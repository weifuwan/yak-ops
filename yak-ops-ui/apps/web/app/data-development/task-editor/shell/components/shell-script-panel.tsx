import { Button } from "@yak-ops/yak-ui";
import { FileCode2, Search } from "lucide-react";

import { ShellScriptEditor } from "./shell-script-editor";

export function ShellScriptPanel({
  rawScript,
  readOnly,
  onChange,
}: {
  rawScript: string;
  readOnly: boolean;
  onChange: (value: string) => void;
}) {
  const scriptLineCount = Math.max(1, rawScript.split("\n").length);
  const scriptCharacterCount = rawScript.length;

  return (
    <>
      <div className="flex h-9 shrink-0 items-center justify-between border-b border-[#e8ebef] bg-[#fbfcfe] px-3">
        <div className="flex min-w-0 items-center gap-2">
          <FileCode2 size={14} className="shrink-0 text-[#5b6b82]" />
          <span className="text-xs font-semibold text-[#303846]">Shell 脚本</span>
          <span className="rounded bg-[#eef2f7] px-1.5 py-0.5 text-[10px] text-[#687385]">Shell</span>
        </div>
        <div className="flex items-center gap-2 text-[11px] text-[#8b93a1]">
          <span className="hidden sm:inline-flex items-center gap-1">
            <Search size={12} />
            Ctrl + F 查找
          </span>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            isDisabled={readOnly || !rawScript}
            onClick={() => onChange("")}
            className="!h-6 !px-2 text-[11px]"
          >
            清空脚本
          </Button>
        </div>
      </div>
      
      <div className="flex min-h-[250px] min-w-0 flex-1 flex-col overflow-hidden">
        <div className="min-h-0 min-w-0 flex-1">
          <ShellScriptEditor
            value={rawScript}
            readOnly={readOnly}
            onChange={onChange}
          />
        </div>
        <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#f0f1f3] bg-white px-3 text-[10px] text-[#98a1ad]">
          <span>行数：{scriptLineCount}　字符：{scriptCharacterCount}</span>
          <span>Shell · UTF-8 · LF</span>
        </div>
      </div>
    </>
  );
}
