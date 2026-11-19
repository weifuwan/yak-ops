type UserMessageProps = {
  content: string;
};

export function UserMessage({ content }: UserMessageProps) {
  return (
    <div className="rounded-[22px] rounded-br-md border border-[#e5e5e2] bg-[#f5f5f3] px-4 py-3 text-[15px] leading-7 text-[#20201e]">
      <p className="m-0 whitespace-pre-wrap break-words">{content}</p>
    </div>
  );
}
