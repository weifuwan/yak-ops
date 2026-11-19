import { AlertCircle } from "lucide-react";

type MessageErrorProps = {
  message: string;
};

export function MessageError({ message }: MessageErrorProps) {
  return (
    <div
      role="alert"
      className="mt-3 flex items-start gap-2 rounded-xl border border-[#f0d6d6] bg-[#fff7f7] px-3 py-2.5 text-xs leading-5 text-[#a14d4d]"
    >
      <AlertCircle size={14} className="mt-0.5 shrink-0" />
      <span>{message}</span>
    </div>
  );
}
