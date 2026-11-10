export function WorkflowTreeIcon({
  size = 14,
}: {
  size?: number;
}) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="none"
      aria-hidden="true"
      className="block shrink-0 text-[#2563eb]"
    >
      <rect
        x="1.5"
        y="1.5"
        width="4"
        height="4"
        rx="1"
        fill="#eff6ff"
        stroke="currentColor"
        strokeWidth="1.2"
      />
      <rect
        x="10.5"
        y="1.5"
        width="4"
        height="4"
        rx="1"
        fill="#eff6ff"
        stroke="currentColor"
        strokeWidth="1.2"
      />
      <rect
        x="10.5"
        y="10.5"
        width="4"
        height="4"
        rx="1"
        fill="#eff6ff"
        stroke="currentColor"
        strokeWidth="1.2"
      />
      <path
        d="M5.5 3.5H8.25C9.5 3.5 10.5 4.5 10.5 5.75V6.25"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M8.25 3.5V10.25C8.25 11.5 9.25 12.5 10.5 12.5"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="8.25" cy="3.5" r="1" fill="currentColor" />
    </svg>
  );
}
