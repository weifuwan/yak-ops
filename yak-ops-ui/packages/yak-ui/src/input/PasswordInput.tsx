import { forwardRef, useState } from "react";

import { Button } from "../button";
import { cn } from "../cn";
import { Input, type InputProps } from "./Input";

export type PasswordInputProps = Omit<InputProps, "type"> & {
  showPasswordLabel?: string;
  hidePasswordLabel?: string;
};

export const PasswordInput = forwardRef<HTMLInputElement, PasswordInputProps>(
  function PasswordInput(
    {
      className,
      showPasswordLabel = "Show password",
      hidePasswordLabel = "Hide password",
      ...props
    },
    ref,
  ) {
    const [visible, setVisible] = useState(false);

    return (
      <div className="relative">
        <Input
          {...props}
          ref={ref}
          type={visible ? "text" : "password"}
          className={cn("pr-10", className)}
        />
        <Button
          variant="ghost"
          size="small"
          type="button"
          aria-label={visible ? hidePasswordLabel : showPasswordLabel}
          aria-pressed={visible}
          className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2 p-0 text-[var(--yak-components-input-icon)]"
          onClick={() => setVisible((current) => !current)}
        >
          {visible ? (
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
