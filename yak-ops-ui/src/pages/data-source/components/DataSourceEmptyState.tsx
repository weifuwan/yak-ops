import { YakButton } from '@/components/ui';
import { useIntl } from '@umijs/max';

const DataSourceEmptyIllustration = () => (
  <svg
    width="220"
    height="158"
    viewBox="0 0 220 158"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <circle cx="67" cy="27" r="18" fill="#ffe9ee" />
    <path
      d="M67 19V35M59 27H75"
      stroke="#fe2c55"
      strokeWidth="3.5"
      strokeLinecap="round"
    />
    <ellipse cx="115" cy="145" rx="77" ry="5" fill="#161823" opacity="0.045" />
    <rect
      x="94"
      y="40"
      width="82"
      height="66"
      rx="5"
      fill="#fff"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path d="M94 55H176" stroke="#515151" strokeWidth="1.5" />
    <circle cx="103" cy="48" r="2" fill="#c6cacd" />
    <circle cx="110" cy="48" r="2" fill="#c6cacd" />
    <circle cx="117" cy="48" r="2" fill="#c6cacd" />
    <ellipse
      cx="135"
      cy="72"
      rx="17"
      ry="6"
      fill="#f3f4f5"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path
      d="M118 72V91C118 95 125.5 98 135 98C144.5 98 152 95 152 91V72"
      fill="#f8f9fa"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path
      d="M118 81C118 85 125.5 88 135 88C144.5 88 152 85 152 81"
      stroke="#c6cacd"
      strokeWidth="1.5"
    />
    <circle
      cx="62"
      cy="88"
      r="10"
      fill="#fff"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path
      d="M50 121C50 106 54 98 62 98C70 98 75 106 75 120V135H48L50 121Z"
      fill="#515151"
    />
    <path
      d="M70 104C79 103 86 96 96 86"
      stroke="#515151"
      strokeWidth="2"
      strokeLinecap="round"
    />
    <circle
      cx="97"
      cy="85"
      r="3"
      fill="#fff"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path
      d="M164 116C164 108 170 102 178 102C186 102 192 108 192 116V134H164V116Z"
      fill="#e7e9eb"
      stroke="#515151"
      strokeWidth="1.5"
    />
    <path
      d="M170 113H186M170 120H186M170 127H181"
      stroke="#9aa0a6"
      strokeWidth="1.5"
      strokeLinecap="round"
    />
    <path
      d="M153 108C157 112 159 116 160 121"
      stroke="#c6cacd"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeDasharray="3 4"
    />
    <circle cx="184" cy="39" r="2.5" fill="#ffd8e1" />
    <circle cx="191" cy="33" r="1.5" fill="#c6cacd" />
  </svg>
);

interface DataSourceEmptyStateProps {
  filtered: boolean;
  canCreate: boolean;
  onReset: () => void;
  onCreate: () => void;
}

const DataSourceEmptyState = ({
  filtered,
  canCreate,
  onReset,
  onCreate,
}: DataSourceEmptyStateProps) => {
  const intl = useIntl();

  return (
    <div className="mt-1 flex min-h-[360px] items-center justify-center rounded-[10px] bg-[#fafafa]">
      <div className="flex w-[340px] -translate-y-1 flex-col items-center">
        <DataSourceEmptyIllustration />
        <h3 className="mt-0.5 text-[14px] font-semibold leading-[22px] text-[#1c1f23]">
          {intl.formatMessage({
            id: filtered
              ? 'pages.datasource.empty.filtered'
              : 'pages.datasource.empty.default',
          })}
        </h3>
        <div className="mt-3.5">
          {filtered ? (
            <YakButton size="small" onClick={onReset}>
              {intl.formatMessage({ id: 'pages.datasource.empty.reset' })}
            </YakButton>
          ) : canCreate ? (
            <YakButton type="primary" size="small" onClick={onCreate}>
              {intl.formatMessage({ id: 'pages.datasource.empty.create' })}
            </YakButton>
          ) : null}
        </div>
      </div>
    </div>
  );
};

export default DataSourceEmptyState;
