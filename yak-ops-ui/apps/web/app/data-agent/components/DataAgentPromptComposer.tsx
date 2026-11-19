import { ArrowUp } from "lucide-react";
import {
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";

type DataAgentPromptComposerProps = {
  onSubmit: (value: string) => boolean | void;
  placeholder?: string;
  placeholderPrefix?: string;
  placeholderSuggestions?: readonly string[];
  trailingActions?: ReactNode;
};

const MIN_TEXTAREA_HEIGHT = 40;
const MAX_TEXTAREA_HEIGHT = 160;

export function DataAgentPromptComposer({
  onSubmit,
  placeholder = "向 Data Agent 描述你想完成的任务...",
  placeholderPrefix,
  placeholderSuggestions = [],
  trailingActions,
}: DataAgentPromptComposerProps) {
  const [value, setValue] = useState("");
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
    <div className="data-agent-composer-root">
      <span aria-hidden="true" className="data-agent-composer-halo" />
      <div className="data-agent-composer-surface">
        <span aria-hidden="true" className="data-agent-composer-fx-root">
          <span data-composer-fx="shadow" className="data-agent-composer-fx-layer" />
          <span data-composer-fx="fill" className="data-agent-composer-fx-layer" />
          <span data-composer-fx="highlight" className="data-agent-composer-fx-layer" />
          <span data-composer-fx="rim" className="data-agent-composer-fx-layer" />
          <span data-composer-fx="focus" className="data-agent-composer-fx-layer" />
        </span>

        <div className="relative z-10 p-3">
          <div className="relative">
            {showAnimatedPlaceholder && (
              <span
                aria-hidden="true"
                className="pointer-events-none absolute inset-x-0 top-0 px-2 py-1 text-[15px] leading-6 text-[#999999]"
              >
                <span>{placeholderPrefix}</span>
                {visibleLength > 0 ? <span> {suggestion.slice(0, visibleLength)}</span> : null}
                <span className="data-agent-composer-caret" />
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
              className="relative block min-h-10 w-full resize-none bg-transparent px-2 py-1 text-[15px] leading-6 text-[#20201e] outline-none placeholder:text-[#999999]"
            />
          </div>

          <div className="mt-1 flex min-h-7 items-center justify-between gap-3 px-1">
            <div className="flex min-w-0 items-center gap-1">
              <span className="text-[11px] text-[#aaaaaa]">AI 助手</span>
            </div>
            <div className="flex shrink-0 items-center gap-1.5">
              {trailingActions}
              <button
                type="button"
                aria-label="发送消息"
                title="发送消息"
                disabled={!value.trim()}
                className="flex size-7 cursor-pointer items-center justify-center rounded-full bg-[#20201e] text-white transition-colors hover:bg-[#343431] disabled:cursor-not-allowed disabled:bg-[#dededb] disabled:text-white"
                onClick={submit}
              >
                <ArrowUp size={16} strokeWidth={1.8} />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default DataAgentPromptComposer;
