import { forwardRef, useCallback, useLayoutEffect, useRef, useState, type ReactNode } from "react";

import { Button } from "../button";
import { cn } from "../cn";
import { Input, type InputProps } from "./Input";

export type PasswordInputProps = Omit<InputProps, "type"> & {
  showPasswordLabel?: string;
  hidePasswordLabel?: string;
  onVisibilityChange?: (visible: boolean) => void;
  /** 只替换图形；显隐按钮的行为与可访问语义仍由控件负责。 */
  renderVisibilityIcon?: (visible: boolean) => ReactNode;
};

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  function PasswordInput(
    {
      className,
      showPasswordLabel = "Show password",
      hidePasswordLabel = "Hide password",
      onVisibilityChange,
      renderVisibilityIcon,
      variant,
      ...props
    },
    ref,
  ) {
    const [visible, setVisible] = useState(false);
    const inputRef = useRef<HTMLInputElement | null>(null);
    const selectionRef = useRef<{
      start: number;
      end: number;
      direction: "forward" | "backward" | "none";
    } | null>(null);
    const setInputRef = useCallback(
      (node: HTMLInputElement | null) => {
        inputRef.current = node;
        if (typeof ref === "function") ref(node);
        else if (ref) ref.current = node;
      },
      [ref],
    );

    useLayoutEffect(() => {
      const input = inputRef.current;
      const selection = selectionRef.current;
      selectionRef.current = null;
      // Restore mouse-editing selection without stealing focus from a keyboard user.
      if (!input || !selection || document.activeElement !== input) return;
      input.setSelectionRange(selection.start, selection.end, selection.direction);
      // Some browsers collapse selection again after the native type/defaultValue update.
      // Finish before the next paint, but never refocus or overwrite a new user selection.
      const frame = window.requestAnimationFrame(() => {
        if (
          input.isConnected &&
          document.activeElement === input &&
          input.selectionStart === 0 &&
          input.selectionEnd === 0
        ) {
          input.setSelectionRange(selection.start, selection.end, selection.direction);
        }
      });
      return () => window.cancelAnimationFrame(frame);
    }, [visible]);

    const toggleVisibility = () => {
      const input = inputRef.current;
      if (input && document.activeElement === input && input.selectionStart !== null) {
        selectionRef.current = {
          start: input.selectionStart,
          end: input.selectionEnd ?? input.selectionStart,
          direction: input.selectionDirection ?? "none",
        };
      }
      const next = !visible;
      setVisible(next);
      // A state updater must stay pure, including in React StrictMode.
      onVisibilityChange?.(next);
    };

    return (
      <div className="relative">
        <Input
          {...props}
          ref={setInputRef}
          variant={variant}
          type={visible ? "text" : "password"}
          className={cn("pr-10", className)}
        />
        <Button
          variant="ghost"
          size="sm"
          type="button"
          aria-label={visible ? hidePasswordLabel : showPasswordLabel}
          aria-pressed={visible}
          isDisabled={props.disabled}
          style={variant === "underlined" ? { padding: 0, width: 32, height: 32 } : undefined}
          className={cn(
            "absolute top-1/2 h-7 w-7 -translate-y-1/2 p-0 text-[var(--yak-components-input-icon)]",
            variant === "underlined" ? "right-0" : "right-1",
          )}
          onMouseDown={(event) => event.preventDefault()}
          onClick={toggleVisibility}
        >
          {renderVisibilityIcon ? (
            renderVisibilityIcon(visible)
          ) : visible ? (
            <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none">
              <path
                d="M3 3l14 14M8.6 8.6a2 2 0 0 0 2.8 2.8M5.1 5.4C3.5 6.5 2.3 8 1.7 10c1.4 4.1 4.2 6.2 8.3 6.2 1.5 0 2.8-.3 3.9-.9M8.2 3.9c.6-.1 1.2-.1 1.8-.1 4.1 0 6.9 2.1 8.3 6.2-.5 1.5-1.2 2.7-2.2 3.7"
                stroke="currentColor"
                strokeWidth="1.4"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          ) : (
            <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4" fill="none">
              <path
                d="M1.7 10C3.1 5.9 5.9 3.8 10 3.8s6.9 2.1 8.3 6.2c-1.4 4.1-4.2 6.2-8.3 6.2S3.1 14.1 1.7 10Z"
                stroke="currentColor"
                strokeWidth="1.4"
              />
              <circle cx="10" cy="10" r="2.4" stroke="currentColor" strokeWidth="1.4" />
            </svg>
          )}
        </Button>
      </div>
    );
  },
);

PasswordInput.displayName = "PasswordInput";
