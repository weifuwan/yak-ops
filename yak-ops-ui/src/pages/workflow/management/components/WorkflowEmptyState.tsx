import { YakButton } from '@/components/ui';
import { useIntl } from '@umijs/max';

const WorkflowEmptyIllustration = () => (
  <svg
    width="220"
    height="158"
    viewBox="0 0 220 158"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <circle cx="67" cy="27" r="18" fill="#ffe9ee" />
    <path d="M67 19V35M59 27H75" stroke="#fe2c55" strokeWidth="3.5" strokeLinecap="round" />
    <ellipse cx="112" cy="145" rx="78" ry="5" fill="#161823" opacity="0.045" />
    <rect x="91" y="35" width="96" height="79" rx="7" fill="#fff" stroke="#515151" strokeWidth="1.5" />
    <path d="M91 51H187" stroke="#515151" strokeWidth="1.5" />
    <circle cx="101" cy="43" r="2" fill="#c6cacd" />
    <circle cx="108" cy="43" r="2" fill="#c6cacd" />
    <circle cx="115" cy="43" r="2" fill="#c6cacd" />
    <rect x="105" y="66" width="24" height="14" rx="5" fill="#f6f7f9" stroke="#515151" strokeWidth="1.5" />
    <rect x="151" y="60" width="24" height="14" rx="5" fill="#f6f7f9" stroke="#515151" strokeWidth="1.5" />
    <rect x="151" y="91" width="24" height="14" rx="5" fill="#f6f7f9" stroke="#515151" strokeWidth="1.5" />
    <path d="M129 73H140C146 73 147 67 151 67" stroke="#8f959d" strokeWidth="1.7" strokeLinecap="round" />
    <path d="M129 73H140C146 73 147 98 151 98" stroke="#8f959d" strokeWidth="1.7" strokeLinecap="round" />
    <circle cx="117" cy="73" r="3" fill="#fe2c55" />
    <circle cx="163" cy="67" r="3" fill="#5868d8" />
    <circle cx="163" cy="98" r="3" fill="#2ea35d" />
    <circle cx="57" cy="90" r="10" fill="#fff" stroke="#515151" strokeWidth="1.5" />
    <path d="M45 121C45 106 49 100 57 100C65 100 70 107 70 121V135H43L45 121Z" fill="#515151" />
    <path d="M65 106C76 104 82 98 94 89" stroke="#515151" strokeWidth="2" strokeLinecap="round" />
    <circle cx="95" cy="88" r="3" fill="#fff" stroke="#515151" strokeWidth="1.5" />
    <path d="M179 121C183 116 190 113 197 114" stroke="#c6cacd" strokeWidth="1.5" strokeLinecap="round" strokeDasharray="3 4" />
    <circle cx="189" cy="40" r="2.5" fill="#ffd8e1" />
    <circle cx="196" cy="34" r="1.5" fill="#c6cacd" />
  </svg>
);

interface WorkflowEmptyStateProps {
  filtered: boolean;
  onReset: () => void;
  onCreate: () => void;
}

const WorkflowEmptyState = ({ filtered, onReset, onCreate }: WorkflowEmptyStateProps) => {
  const intl = useIntl();

  return (
    <div className="mt-1 flex min-h-[360px] items-center justify-center rounded-[10px] bg-[#fafafa]">
      <div className="flex w-[360px] -translate-y-1 flex-col items-center">
        <WorkflowEmptyIllustration />
        <h3 className="mt-0.5 text-[14px] font-semibold leading-[22px] text-[#1c1f23]">
          {intl.formatMessage({
            id: filtered
              ? 'pages.workflow.definition.emptyFiltered'
              : 'pages.workflow.definition.empty',
          })}
        </h3>
        <p className="mb-0 mt-1 text-center text-[11px] leading-5 text-[#969ba5]">
          {intl.formatMessage({
            id: filtered
              ? 'pages.workflow.definition.emptyFilteredHint'
              : 'pages.workflow.definition.emptyHint',
          })}
        </p>
        <div className="mt-3.5">
          {filtered ? (
            <YakButton size="small" onClick={onReset}>
              {intl.formatMessage({ id: 'pages.workflow.definition.resetFilter' })}
            </YakButton>
          ) : (
            <YakButton type="primary" size="small" onClick={onCreate}>
              {intl.formatMessage({ id: 'pages.workflow.definition.create' })}
            </YakButton>
          )}
        </div>
      </div>
    </div>
  );
};

export default WorkflowEmptyState;
