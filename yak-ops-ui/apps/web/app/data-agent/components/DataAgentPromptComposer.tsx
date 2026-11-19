import { ArrowUp, Code2, Database, Plus, Workflow } from "lucide-react";
import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";

type DataAgentPromptComposerProps = {
  onSubmit: (value: string) => boolean | void;
  initialValue?: string;
  placeholder?: string;
  placeholderPrefix?: string;
  placeholderSuggestions?: readonly string[];
  trailingActions?: ReactNode;
};

const MIN_TEXTAREA_HEIGHT = 40;
const MAX_TEXTAREA_HEIGHT = 160;

export function DataAgentPromptComposer({
  onSubmit,
  initialValue = "",
  placeholder = "向 Data Agent 描述你想完成的任务...",
  placeholderPrefix,
  placeholderSuggestions = [],
  trailingActions,
}: DataAgentPromptComposerProps) {
  const [value, setValue] = useState(initialValue);
  const [suggestionIndex, setSuggestionIndex] = useState(0);
  const [visibleLength, setVisibleLength] = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const isComposingRef = useRef(false);

  const suggestion =
    placeholderSuggestions[suggestionIndex % Math.max(placeholderSuggestions.length, 1)] ?? "";
  const showAnimatedPlaceholder =
    Boolean(placeholderPrefix) && placeholderSuggestions.length > 0 && value.length === 0;

  useLayoutEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    textarea.style.height = "auto";
    const nextHeight = Math.min(
      Math.max(textarea.scrollHeight, MIN_TEXTAREA_HEIGHT),
      MAX_TEXTAREA_HEIGHT,
    );
    textarea.style.height = nextHeight + "px";
    textarea.style.overflowY = textarea.scrollHeight > MAX_TEXTAREA_HEIGHT ? "auto" : "hidden";
  }, [value]);

  useEffect(() => {
    if (!showAnimatedPlaceholder) return;

    let timer: number;
    if (visibleLength < suggestion.length) {
      timer = window.setTimeout(() => {
        setVisibleLength((current) => current + 1);
      }, 42);
    } else {
      timer = window.setTimeout(() => {
        setVisibleLength(0);
        setSuggestionIndex((current) => (current + 1) % placeholderSuggestions.length);
      }, 1500);
    }

    return () => window.clearTimeout(timer);
  }, [placeholderSuggestions.length, showAnimatedPlaceholder, suggestion.length, visibleLength]);

  function submit() {
    const normalized = value.trim();
    if (!normalized) return;

    const accepted = onSubmit(normalized);
    if (accepted !== false) {
      setValue("");
      setVisibleLength(0);
    }
  }

  return (
    <div className="group/composer relative isolate w-full">
      <div className="relative z-[1] overflow-hidden rounded-[14px] border border-[#e0e4e3] bg-white shadow-[0_1px_2px_rgba(0,0,0,0.035)] transition-[border-color,box-shadow] duration-150 focus-within:border-[#cbd3d1] focus-within:shadow-[0_2px_8px_rgba(0,0,0,0.055)]">
        <div className="relative p-2.5">
          <div className="relative">
            {showAnimatedPlaceholder && (
              <span
                aria-hidden="true"
                className="pointer-events-none absolute inset-x-0 top-0 px-1 py-1 text-[14px] leading-6 text-[#a3adb8]"
              >
                <span>{placeholderPrefix}</span>
                {visibleLength > 0 ? <span> {suggestion.slice(0, visibleLength)}</span> : null}
                <span className="ml-0.5 inline-block h-[1em] w-px animate-data-agent-caret bg-current align-[-0.12em] opacity-[0.65] motion-reduce:animate-none" />
              </span>
            )}
            <textarea
              ref={textareaRef}
              aria-label="向 Data Agent 发送消息"
              autoComplete="off"
              rows={1}
              value={value}
              placeholder={showAnimatedPlaceholder ? "" : placeholder}
              onChange={(event) => setValue(event.target.value)}
              onKeyDown={(event) => {
                if (
                  event.key !== "Enter" ||
                  event.shiftKey ||
                  event.nativeEvent.isComposing ||
                  isComposingRef.current
                ) {
                  return;
                }
                event.preventDefault();
                submit();
              }}
              onCompositionStart={() => {
                isComposingRef.current = true;
              }}
              onCompositionEnd={() => {
                isComposingRef.current = false;
              }}
              className="relative block min-h-10 w-full resize-none bg-transparent px-1 py-1 text-[14px] leading-6 text-[#20201e] outline-none placeholder:text-[#aab4bf]"
            />
          </div>

          <div className="mt-0.5 flex min-h-7 items-center justify-between gap-3 px-0.5">
            <div className="flex min-w-0 items-center gap-2">
              <button
                type="button"
                aria-label="附件上传暂未接入"
                title="附件上传暂未接入"
                disabled
                className="flex size-7 shrink-0 cursor-not-allowed items-center justify-center rounded-md text-[#9aa4a3] opacity-75"
              >
                <Plus size={17} strokeWidth={1.7} />
              </button>
              
            </div>

            <div className="flex shrink-0 items-center gap-1.5">
              {trailingActions}
              <button
                type="button"
                aria-label="发送消息"
                title="发送消息"
                disabled={!value.trim()}
                className="flex size-8 cursor-pointer items-center justify-center rounded-full bg-[#000000] text-white transition-colors hover:bg-[#727d7a] disabled:cursor-not-allowed disabled:bg-[#e0e3e2] disabled:text-white"
                onClick={submit}
              >
                <ArrowUp size={17} strokeWidth={1.8} />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default DataAgentPromptComposer;
