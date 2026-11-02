import { useRef, useState } from "react";
import { Button, Input } from "@yak-ops/yak-ui";

interface Props {
  label: string;
  value: string;
  placeholder: string;
  disabled?: boolean;
  maxLength?: number;
  onCommit: (value: string) => string | undefined;
}

/** Single-cell draft: Enter/blur commits; Escape abandons the local edit. */
export function ReplicationEditableCell({
  label,
  value,
  placeholder,
  disabled,
  maxLength,
  onCommit,
}: Props) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  const [error, setError] = useState("");
  const cancelBlur = useRef(false);

  const commit = () => {
    if (cancelBlur.current) {
      cancelBlur.current = false;
      return;
    }
    const message = onCommit(draft);
    if (message) {
      setError(message);
    } else {
      setError("");
      setEditing(false);
    }
  };

  if (editing && !disabled) {
    return (
      <Input
        autoFocus
        size="small"
        variant="outlined"
        aria-label={label}
        aria-invalid={Boolean(error)}
        title={error || undefined}
        maxLength={maxLength}
        value={draft}
        onChange={(event) => {
          setDraft(event.target.value);
          setError("");
        }}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === "Enter") {
            event.preventDefault();
            commit();
          } else if (event.key === "Escape") {
            event.preventDefault();
            cancelBlur.current = true;
            setError("");
            setEditing(false);
          }
        }}
      />
    );
  }

  return (
    <Button
      size="small"
      variant="ghost"
      className="max-w-full truncate px-1 font-normal text-[var(--yak-color-primary)]"
      aria-label={"编辑" + label}
      title={value || placeholder}
      disabled={disabled}
      onClick={() => {
        cancelBlur.current = false;
        setDraft(value);
        setError("");
        setEditing(true);
      }}
    >
      {value || placeholder}
    </Button>
  );
}
