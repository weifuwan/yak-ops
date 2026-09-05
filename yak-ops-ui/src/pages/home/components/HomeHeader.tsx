import type { HomeCockpitHeaderStats } from '@/services/home';
import { history, useIntl } from '@umijs/max';
import { ChevronRight, Database } from 'lucide-react';

interface ProfileStatProps {
  label: string;
  value: number | string;
  arrow?: boolean;
  onClick?: () => void;
}

interface HomeHeaderProps {
  stats?: HomeCockpitHeaderStats;
}

function ProfileStat({ label, value, arrow = false, onClick }: ProfileStatProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center border-0 bg-transparent p-0 text-sm leading-[22px] text-[#747983] transition-colors duration-200 hover:text-[#292c35]"
    >
      <span>{label}</span>
      <strong className="ml-[5px] font-semibold text-[#282b34]">{value}</strong>
      {arrow ? (
        <ChevronRight
          size={14}
          strokeWidth={1.8}
          className="ml-[3px] text-[#9599a2]"
        />
      ) : null}
    </button>
  );
}

export function HomeHeader({ stats }: HomeHeaderProps) {
  const intl = useIntl();
  const dataSourceCount = stats?.dataSourceCount ?? '--';
  const runningCount = stats?.runningCount ?? '--';

  return (
    <header className="flex h-[116px] items-center px-4">
      <div className="flex items-center">
        <div className="flex h-[66px] w-[66px] shrink-0 items-center justify-center overflow-hidden rounded-full border border-white/90 bg-gradient-to-br from-[#dde6ef] via-[#a6c8e2] to-[#5e93d4] text-white/95 shadow-[0_2px_4px_rgba(31,35,41,0.04)]">
          <Database size={30} strokeWidth={1.5} />
        </div>

        <div className="ml-4 min-w-0">
          <div className="flex min-h-[22px] items-center">
            <span className="whitespace-nowrap text-sm font-medium leading-[22px] text-[#252830]">
              Yak Ops
            </span>
            <span className="mx-3 h-[14px] w-px shrink-0 bg-black/[0.14]" />
            <span className="whitespace-nowrap text-sm font-normal leading-[22px] text-[#777b84]">
              {intl.formatMessage({ id: 'pages.home.header.tagline' })}
            </span>
            <span className="mx-3 h-[14px] w-px shrink-0 bg-black/[0.14]" />
            <span className="whitespace-nowrap text-sm font-normal leading-[22px] text-[#777b84]">
              {intl.formatMessage({ id: 'pages.home.header.description' })}
            </span>
          </div>

          <div className="mt-2.5 flex items-center gap-[27px]">
            <ProfileStat
              label={intl.formatMessage({ id: 'pages.home.header.dataSources' })}
              value={dataSourceCount}
              arrow
              onClick={() => history.push('/data-source')}
            />
            <ProfileStat
              label={intl.formatMessage({ id: 'pages.home.header.running' })}
              value={runningCount}
              arrow
              onClick={() => history.push('/data-development/executions')}
            />
          </div>
        </div>
      </div>
    </header>
  );
}
