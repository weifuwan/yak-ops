import { ScriptCodeEditor } from "./script-code-editor";
import type { ScriptLanguage } from "./types";

export function ScriptEditorPanel({
  rawScript,
  language,
  scriptLabel,
}: {
  rawScript: string;
  language: ScriptLanguage;
  scriptLabel: string;
}) {
  const scriptLineCount = Math.max(1, rawScript.split("\n").length);
  const scriptCharacterCount = rawScript.length;

  return (
    <div className="flex min-h-[250px] min-w-0 flex-1 flex-col overflow-hidden">
      <div className="min-h-0 min-w-0 flex-1">
        <ScriptCodeEditor
          value={rawScript}
          readOnly={false}
          onChange={() => undefined}
          language={language}
          scriptLabel={scriptLabel}
        />
      </div>
      <div className="flex h-6 shrink-0 items-center justify-between border-t border-[#f0f1f3] bg-white px-3 text-[10px] text-[#98a1ad]">
        <span>行数：{scriptLineCount}　字符：{scriptCharacterCount}</span>
        <span>{language === "python" ? "Python" : "Shell"} · UTF-8 · LF</span>
      </div>
    </div>
  );
}
