import { useEffect, useRef, useState } from "react";
import type { KeyboardEvent, PointerEvent } from "react";
import * as monaco from "monaco-editor/editor";
import "monaco-editor/features/register.all";
import EditorWorker from "monaco-editor/editor/editor.worker?worker";

const workerGlobal = self as typeof self & {
  MonacoEnvironment?: {
    getWorker: (workerId: string, label: string) => Worker;
  };
};

workerGlobal.MonacoEnvironment = {
  getWorker: () => new EditorWorker(),
};

const SQL_KEYWORDS = [
  "ADD", "ALL", "ALTER", "AND", "AS", "ASC", "BEGIN", "BETWEEN", "BY",
  "CASE", "CAST", "CHECK", "COLUMN", "COMMIT", "CREATE", "CROSS", "DATABASE",
  "DEFAULT", "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "END", "EXISTS",
  "FALSE", "FETCH", "FROM", "FULL", "GRANT", "GROUP", "HAVING", "IN",
  "INDEX", "INNER", "INSERT", "INTERSECT", "INTO", "IS", "JOIN", "LEFT",
  "LIKE", "LIMIT", "NOT", "NULL", "OFFSET", "ON", "OR", "ORDER", "OUTER",
  "OVER", "PARTITION", "PRIMARY", "REFERENCES", "RIGHT", "ROLLBACK", "ROW",
  "SELECT", "SET", "TABLE", "THEN", "TRUE", "TRUNCATE", "UNION", "UNIQUE",
  "UPDATE", "USE", "VALUES", "VIEW", "WHEN", "WHERE", "WITH",
];

const SQL_TYPES = [
  "BIGINT", "BINARY", "BOOLEAN", "CHAR", "DATE", "DATETIME", "DECIMAL",
  "DOUBLE", "FLOAT", "INT", "INTEGER", "JSON", "NUMBER", "NUMERIC",
  "REAL", "SMALLINT", "STRING", "TEXT", "TIME", "TIMESTAMP", "VARCHAR",
];

if (!monaco.languages.getLanguages().some((language) => language.id === "sql")) {
  monaco.languages.register({ id: "sql" });
  monaco.languages.setMonarchTokensProvider("sql", {
    ignoreCase: true,
    keywords: SQL_KEYWORDS,
    typeKeywords: SQL_TYPES,
    tokenizer: {
      root: [
        [/--.*$/, "comment"],
        [/\/\*/, "comment", "@comment"],
        [/'([^']|'')*'/, "string"],
        [/"([^"]|"")*"/, "string"],
        [/[a-zA-Z_][\w$]*/, {
          cases: {
            "@keywords": "keyword",
            "@typeKeywords": "type",
            "@default": "identifier",
          },
        }],
        [/\d+(\.\d+)?/, "number"],
        [/[;,.]/, "delimiter"],
        [/[()]/, "@brackets"],
        [/[<>!=]=?|[+\-*\/%]/, "operator"],
      ],
      comment: [
        [/[^*/]+/, "comment"],
        [/\*\//, "comment", "@pop"],
        [/[*/]/, "comment"],
      ],
    },
  });
}

type SqlCodeEditorResizeOptions = {
  initialHeight: number;
  minHeight: number;
  maxHeight: number;
};

type ResizeStart = {
  pointerId: number;
  startY: number;
  startHeight: number;
};

export function SqlCodeEditor({
  value,
  readOnly,
  onChange,
  resizable,
}: {
  value: string;
  readOnly: boolean;
  onChange: (value: string) => void;
  resizable?: SqlCodeEditorResizeOptions;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const initialValueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  const resizeStartRef = useRef<ResizeStart | null>(null);
  const [editorHeight, setEditorHeight] = useState(resizable?.initialHeight ?? 420);
  onChangeRef.current = onChange;

  useEffect(() => {
    if (resizable) setEditorHeight(resizable.initialHeight);
  }, [resizable?.initialHeight]);

  useEffect(() => {
    if (!containerRef.current) return;

    const editor = monaco.editor.create(containerRef.current, {
      value: initialValueRef.current,
      language: "sql",
      theme: "vs",
      readOnly,
      automaticLayout: true,
      fixedOverflowWidgets: true,
      minimap: { enabled: false },
      fontSize: 13,
      lineHeight: 22,
      fontFamily: "Consolas, 'Cascadia Code', 'Courier New', monospace",
      tabSize: 2,
      insertSpaces: true,
      wordWrap: "on",
      scrollBeyondLastLine: false,
      renderLineHighlight: "line",
      cursorBlinking: "smooth",
      smoothScrolling: true,
      contextmenu: true,
      padding: { top: 12, bottom: 12 },
      ariaLabel: "SQL 编辑器",
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
  }, []);

  useEffect(() => {
    const editor = editorRef.current;
    if (editor && editor.getValue() !== value) editor.setValue(value);
  }, [value]);

  useEffect(() => {
    editorRef.current?.updateOptions({ readOnly });
  }, [readOnly]);

  if (!resizable) {
    return <div ref={containerRef} className="nokey h-full min-h-[280px] min-w-0 w-full" />;
  }

  const clampHeight = (height: number) =>
    Math.min(resizable.maxHeight, Math.max(resizable.minHeight, height));

  const handleResizeStart = (event: PointerEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    resizeStartRef.current = {
      pointerId: event.pointerId,
      startY: event.clientY,
      startHeight: editorHeight,
    };
  };

  const handleResizeMove = (event: PointerEvent<HTMLDivElement>) => {
    const start = resizeStartRef.current;
    if (!start || start.pointerId !== event.pointerId) return;
    setEditorHeight(clampHeight(start.startHeight + event.clientY - start.startY));
  };

  const handleResizeEnd = () => {
    resizeStartRef.current = null;
  };

  const handleResizeKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
    event.preventDefault();
    setEditorHeight((height) => clampHeight(height + (event.key === "ArrowDown" ? 16 : -16)));
  };

  return (
    <div
      className="nokey flex w-full min-w-0 flex-col"
      style={{
        height: editorHeight,
        minHeight: resizable.minHeight,
        maxHeight: resizable.maxHeight,
      }}
    >
      <div ref={containerRef} className="min-h-0 min-w-0 w-full flex-1" />
      <div
        role="separator"
        aria-label="调整 SQL 编辑器高度"
        aria-orientation="horizontal"
        aria-valuemin={resizable.minHeight}
        aria-valuemax={resizable.maxHeight}
        aria-valuenow={editorHeight}
        aria-valuetext={`${editorHeight}px`}
        tabIndex={0}
        title="上下拖动调整编辑器高度"
        className="group flex h-2 shrink-0 cursor-ns-resize touch-none items-center justify-center bg-white transition-colors hover:bg-[#f7f8fa] focus-visible:outline focus-visible:outline-1 focus-visible:outline-[#0033ff]"
        onPointerDown={handleResizeStart}
        onPointerMove={handleResizeMove}
        onPointerUp={handleResizeEnd}
        onPointerCancel={handleResizeEnd}
        onLostPointerCapture={handleResizeEnd}
        onKeyDown={handleResizeKeyDown}
      >
        <span className="h-1 w-10 rounded-full bg-[#d1d5db] transition-colors group-hover:bg-[#64748b]" />
      </div>
    </div>
  );
}
