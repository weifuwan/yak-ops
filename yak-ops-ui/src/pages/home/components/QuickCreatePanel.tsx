import { history, useIntl } from '@umijs/max';
import {
  ArrowRightLeft,
  Braces,
  ChevronRight,
  RadioTower,
  Workflow,
} from 'lucide-react';
import type { ReactNode } from 'react';

type CardTheme = 'offline' | 'realtime' | 'development' | 'workflow';

interface QuickCreateItem {
  key: CardTheme;
  icon: ReactNode;
}

const QUICK_CREATE_ITEMS: QuickCreateItem[] = [
  {
    key: 'offline',
    icon: <ArrowRightLeft size={18} strokeWidth={2.1} />,
  },
  {
    key: 'realtime',
    icon: <RadioTower size={18} strokeWidth={2.1} />,
  },
  {
    key: 'development',
    icon: <Braces size={18} strokeWidth={2.1} />,
  },
  {
    key: 'workflow',
    icon: <Workflow size={18} strokeWidth={2.1} />,
  },
];

const THEME_STYLES: Record<
  CardTheme,
  { panel: string; core: string; glow: string; particles: string }
> = {
  offline: {
    panel: 'bg-[linear-gradient(180deg,#9fc7ff_0%,#d6e7ff_54%,#f1f7ff_100%)]',
    core: 'bg-[linear-gradient(145deg,#758fff_0%,#6267f2_100%)] shadow-[inset_0_-1px_2px_rgba(60,73,214,0.22)]',
    glow: 'bg-[#abc9ff]',
    particles: 'bg-[radial-gradient(circle,rgba(112,142,255,0.42)_0.8px,transparent_0.9px)]',
  },
  realtime: {
    panel: 'bg-[linear-gradient(180deg,#80d6ff_0%,#ccefff_54%,#effaff_100%)]',
    core: 'bg-[linear-gradient(145deg,#35c6ff_0%,#168cf4_100%)] shadow-[inset_0_-1px_2px_rgba(0,93,231,0.22)]',
    glow: 'bg-[#78d9ff]',
    particles: 'bg-[radial-gradient(circle,rgba(44,173,239,0.42)_0.8px,transparent_0.9px)]',
  },
  development: {
    panel: 'bg-[linear-gradient(180deg,#ff8ea5_0%,#ffd4dd_54%,#fff0f3_100%)]',
    core: 'bg-[linear-gradient(145deg,#ff4768_0%,#fe2c55_100%)] shadow-[inset_0_-1px_2px_rgba(188,21,56,0.22)]',
    glow: 'bg-[#ff8ea5]',
    particles: 'bg-[radial-gradient(circle,rgba(254,44,85,0.34)_0.8px,transparent_0.9px)]',
  },
  workflow: {
    panel: 'bg-[linear-gradient(180deg,#ffd95c_0%,#ffedb5_54%,#fff9e6_100%)]',
    core: 'bg-[linear-gradient(145deg,#ffd33d_0%,#ffb900_100%)] shadow-[inset_0_-1px_2px_rgba(225,152,0,0.20)]',
    glow: 'bg-[#ffe27d]',
    particles: 'bg-[radial-gradient(circle,rgba(241,181,0,0.36)_0.8px,transparent_0.9px)]',
  },
};

function LayeredIcon({ theme, icon }: { theme: CardTheme; icon: ReactNode }) {
  const styles = THEME_STYLES[theme];
  return (
    <div className="pointer-events-none absolute -left-px -top-[2px] z-[3] h-[80px] w-[76px] origin-center transition-transform duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:-translate-y-[1px] group-hover:scale-[1.045]">
      <div className="absolute left-[2px] top-[8px] h-[72px] w-[59px] origin-bottom-right rotate-[20deg] rounded-[12px] border border-white/80 bg-[#d7d9df]/40 shadow-[0_4px_12px_rgba(31,35,41,0.035)] backdrop-blur-[4px] transition-[transform,border-width,border-color] duration-[360ms] ease-[cubic-bezier(0.34,1.56,0.64,1)] group-hover:rotate-[26deg] group-hover:border-2 group-hover:border-white" />
      <div className={`absolute left-0 top-[4px] flex h-[72px] w-[61px] items-center justify-center overflow-hidden rounded-[12px] border border-white/90 shadow-[0_7px_16px_rgba(31,35,41,0.09)] ${styles.panel}`}>
        <span className="absolute inset-x-[5px] top-[2px] h-[20px] rounded-full bg-white/25 blur-[8px]" />
        <div className={`relative flex h-[34px] w-[34px] shrink-0 items-center justify-center overflow-hidden rounded-[9px] text-white transition-transform duration-[320ms] ease-[cubic-bezier(0.22,1,0.36,1)] group-hover:scale-[1.035] ${styles.core}`}>
          <span className={`absolute -left-[7px] -top-[7px] h-[19px] w-[19px] rounded-full opacity-60 blur-[6px] ${styles.glow}`} />
          <span className="absolute -bottom-[7px] -right-[6px] h-[18px] w-[18px] rounded-full bg-white/40 blur-[6px]" />
          <span className="relative z-[2] flex h-5 w-5 items-center justify-center drop-shadow-[0_1px_1px_rgba(0,0,0,0.06)]">
            {icon}
          </span>
        </div>
      </div>
    </div>
  );
}

function QuickCreateCard({ item }: { item: QuickCreateItem }) {
  const intl = useIntl();
  const styles = THEME_STYLES[item.key];
  const title = intl.formatMessage({
    id: `pages.home.quickCreate.${item.key}.title`,
  });
  const description = intl.formatMessage({
    id: `pages.home.quickCreate.${item.key}.description`,
  });

  return (
    <button
      type="button"
      onClick={() => history.push(`/create?type=${item.key}`)}
      className="group relative flex h-[76px] min-w-0 items-center overflow-visible rounded-[16px] border border-[rgba(31,35,41,0.075)] bg-white/[0.96] pr-4 text-left shadow-[0_3px_10px_rgba(31,35,41,0.045),0_1px_2px_rgba(31,35,41,0.025)] transition-[border-color,box-shadow,transform] duration-[260ms] ease-[cubic-bezier(0.22,1,0.36,1)] hover:-translate-y-px hover:border-[rgba(31,35,41,0.09)] hover:shadow-[0_8px_20px_rgba(31,35,41,0.07),0_1px_2px_rgba(31,35,41,0.025)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-sky-200/70"
    >
      <span
        aria-hidden="true"
        className={`pointer-events-none absolute inset-0 z-0 overflow-hidden rounded-[16px] opacity-0 transition-opacity duration-300 group-hover:opacity-100 [background-size:8px_8px] [mask-image:linear-gradient(90deg,#000_0%,rgba(0,0,0,0.84)_24%,rgba(0,0,0,0.16)_72%,transparent_100%)] ${styles.particles}`}
      />
      <span aria-hidden="true" className="pointer-events-none absolute inset-0 z-[1] rounded-[16px] bg-[linear-gradient(90deg,rgba(255,255,255,0.03)_0%,rgba(255,255,255,0.12)_38%,rgba(255,255,255,0.91)_100%)]" />
      <div className="relative z-[2] h-[76px] w-[77px] shrink-0">
        <LayeredIcon theme={item.key} icon={item.icon} />
      </div>
      <div className="relative z-[2] ml-2 min-w-0 flex-1">
        <div className="truncate text-[15px] font-semibold leading-[22px] text-[#292c35]">
          {title}
        </div>
        <div className="mt-0.5 flex min-w-0 items-center text-[13px] font-normal leading-5 text-[#9498a1]">
          <span className="min-w-0 truncate">{description}</span>
          <ChevronRight
            size={13}
            strokeWidth={1.8}
            className="ml-[2px] shrink-0 -translate-x-[3px] text-[#92969f] opacity-0 transition-[opacity,transform] duration-[220ms] ease-out group-hover:translate-x-0 group-hover:opacity-100"
          />
        </div>
      </div>
    </button>
  );
}

export function QuickCreatePanel() {
  const intl = useIntl();

  return (
    <section className="rounded-[22px] border border-[#f1f1f1] bg-white/[0.74] px-[22px] pb-6 pt-6 backdrop-blur-[8px]">
      <h2 className="mb-5 text-xl font-semibold leading-7 tracking-[-0.35px] text-[#252832]">
        {intl.formatMessage({ id: 'pages.home.quickCreate.title' })}
      </h2>
      <div className="grid grid-cols-1 gap-[14px] sm:grid-cols-2 min-[1280px]:grid-cols-4">
        {QUICK_CREATE_ITEMS.map((item) => (
          <QuickCreateCard key={item.key} item={item} />
        ))}
      </div>
    </section>
  );
}
