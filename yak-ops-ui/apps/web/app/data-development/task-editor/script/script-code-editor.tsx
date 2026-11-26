import { useEffect, useRef } from "react";
import * as monaco from "monaco-editor/editor";
import "monaco-editor/features/register.all";
import "monaco-editor/languages/definitions/python/register";
import "monaco-editor/languages/definitions/shell/register";
import EditorWorker from "monaco-editor/editor/editor.worker?worker";

import type { ScriptLanguage } from "./types";

const workerGlobal = self as typeof self & {
  MonacoEnvironment?: {
    getWorker: (workerId: string, label: string) => Worker;
  };
};

workerGlobal.MonacoEnvironment = {
  getWorker: () => new EditorWorker(),
};

export function ScriptCodeEditor({
  value,
  readOnly,
  onChange,
  language,
  scriptLabel,
}: {
  value: string;
  readOnly: boolean;
  onChange: (value: string) => void;
  language: ScriptLanguage;
  scriptLabel: string;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const initialValueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  useEffect(() => {
    if (!containerRef.current) return;

    const editor = monaco.editor.create(containerRef.current, {
      value: initialValueRef.current,
      language,
      theme: "vs",
      readOnly,
      automaticLayout: true,
      minimap: { enabled: false },
      fontSize: 13,
      lineHeight: 22,
      fontFamily: "Consolas, 'Cascadia Code', 'Courier New', monospace",
      tabSize: language === "python" ? 4 : 2,
      insertSpaces: true,
      wordWrap: "on",
      scrollBeyondLastLine: false,
      renderLineHighlight: "line",
      cursorBlinking: "smooth",
      smoothScrolling: true,
      contextmenu: true,
      padding: { top: 12, bottom: 12 },
      ariaLabel: scriptLabel + "编辑器",
      overviewRulerBorder: false,
      scrollbar: {
        verticalScrollbarSize: 9,
        horizontalScrollbarSize: 9,
        useShadows: false,
      },
    });

    editorRef.current = editor;
    const subscription = editor.onDidChangeModelContent(() => {
      onChangeRef.current(editor.getValue());
    });

    return () => {
      subscription.dispose();
      editor.dispose();
      editorRef.current = null;
    };
  }, [language, scriptLabel]);

  useEffect(() => {
    const editor = editorRef.current;
    if (editor && editor.getValue() !== value) {
      editor.setValue(value);
    }
  }, [value]);

  useEffect(() => {
    editorRef.current?.updateOptions({ readOnly });
  }, [readOnly]);

  return <div ref={containerRef} className="nokey h-full min-h-[240px] min-w-0 w-full" />;
}
