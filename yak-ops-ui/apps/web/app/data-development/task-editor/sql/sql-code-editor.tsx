import { useEffect, useRef } from "react";
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

export function SqlCodeEditor({
  value,
  readOnly,
  onChange,
}: {
  value: string;
  readOnly: boolean;
  onChange: (value: string) => void;
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
      language: "sql",
      theme: "vs",
      readOnly,
      automaticLayout: true,
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

  return <div ref={containerRef} className="h-full min-h-[280px] min-w-0 w-full" />;
}
