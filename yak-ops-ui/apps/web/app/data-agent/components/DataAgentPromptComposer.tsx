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
  initialValue?: string;
  placeholder?: string;
  placeholderPrefix?: string;
  placeholderSuggestions?: readonly string[];
  trailingActions?: ReactNode;
};

const MIN_TEXTAREA_HEIGHT = 56;
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
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-6 bottom-0.5 z-0 h-2 rounded-full bg-[linear-gradient(90deg,#5e89f2_0%,#ff66f4_50%,#fe7b02_100%)] opacity-0 blur-[7px] transition-opacity duration-200 ease-out group-focus-within/composer:opacity-20 motion-reduce:transition-none"
      />
      <div className="relative z-[1] isolate overflow-hidden rounded-3xl bg-white">
        <span aria-hidden="true" className="pointer-events-none absolute inset-0 z-0 rounded-[inherit]">
          <span className="pointer-events-none absolute inset-0 z-0 rounded-[inherit] shadow-[0_2px_3px_-2px_rgb(0_0_0_/_12%),0_10px_28px_-16px_rgb(0_0_0_/_22%)]" />
          <span className="pointer-events-none absolute inset-0 z-[1] rounded-[inherit] bg-white" />
          <span className="pointer-events-none absolute inset-0 z-[2] rounded-[inherit] shadow-[inset_0_1px_0_0_rgb(255_255_255_/_92%),inset_0_-1px_0_0_rgb(0_0_0_/_2.5%)]" />
          <span className="pointer-events-none absolute inset-0 z-[3] rounded-[inherit] shadow-[inset_0_0_0_1px_#e6e6e3]" />
          <span className="pointer-events-none absolute inset-0 z-[4] rounded-[inherit] opacity-0 shadow-[inset_0_0_0_1px_#c7c7c2] transition-opacity duration-150 group-focus-within/composer:opacity-100 motion-reduce:transition-none" />
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
                <span className="ml-0.5 inline-block h-[1em] w-px translate-y-[-0.12em] animate-data-agent-caret bg-current align-[-0.12em] opacity-[0.65] motion-reduce:animate-none" />
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
              className="relative block min-h-14 w-full resize-none bg-transparent px-2 py-1 text-[15px] leading-6 text-[#20201e] outline-none placeholder:text-[#999999]"
            />
          </div>

          <div className="mt-1 flex min-h-8 items-center justify-between gap-3 px-1">
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
                className="flex size-8 cursor-pointer items-center justify-center rounded-full bg-[#20201e] text-white transition-colors hover:bg-[#343431] disabled:cursor-not-allowed disabled:bg-[#dededb] disabled:text-white"
                onClick={submit}
              >
                <ArrowUp size={18} strokeWidth={1.8} />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default DataAgentPromptComposer;
