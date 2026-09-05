import type { DataSourceSummary } from '@/services/data-source';
import { useIntl } from '@umijs/max';
import { motion } from 'framer-motion';
import { CheckCircle2, Database, Server, XCircle } from 'lucide-react';
import type { ReactNode } from 'react';

import { PAGE_ANIMATION } from '../constants';

interface SummaryItem {
  key: keyof Pick<
    DataSourceSummary,
    'total' | 'connected' | 'disconnected' | 'environmentCount'
  >;
  labelId: string;
  icon: ReactNode;
  iconClassName: string;
  valueClassName: string;
}

const SUMMARY_ITEMS: SummaryItem[] = [
  {
    key: 'total',
    labelId: 'pages.datasource.summary.total',
    icon: <Database size={17} strokeWidth={1.9} />,
    iconClassName: 'bg-[#eef2ff] text-[#5868d8]',
    valueClassName: 'text-[#242731]',
  },
  {
    key: 'connected',
    labelId: 'pages.datasource.summary.connected',
    icon: <CheckCircle2 size={17} strokeWidth={1.9} />,
    iconClassName: 'bg-[#edf8f1] text-[#2ea35d]',
    valueClassName: 'text-[#242731]',
  },
  {
    key: 'disconnected',
    labelId: 'pages.datasource.summary.disconnected',
    icon: <XCircle size={17} strokeWidth={1.9} />,
    iconClassName: 'bg-[#fff1f2] text-[#e35b67]',
    valueClassName: 'text-[#242731]',
  },
  {
    key: 'environmentCount',
    labelId: 'pages.datasource.summary.environments',
    icon: <Server size={17} strokeWidth={1.9} />,
    iconClassName: 'bg-[#f3f5f7] text-[#697587]',
    valueClassName: 'text-[#242731]',
  },
];

interface DataSourceSummaryCardsProps {
  summary: DataSourceSummary;
}

const DataSourceSummaryCards = ({ summary }: DataSourceSummaryCardsProps) => {
  const intl = useIntl();

  return (
    <motion.section
      variants={PAGE_ANIMATION.fadeUp}
      className="flex flex-wrap gap-3"
    >
      {SUMMARY_ITEMS.map((item) => (
        <div
          key={item.key}
          className="flex min-w-[180px] flex-1 items-center gap-3 rounded-[14px] bg-[#fafbfc] px-4 py-3"
        >
          <span
            className={[
              'flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px]',
              item.iconClassName,
            ].join(' ')}
          >
            {item.icon}
          </span>

          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium text-[#606571]">
              {intl.formatMessage({ id: item.labelId })}
            </div>
          </div>

          <strong
            className={[
              'ml-auto shrink-0 text-[28px] font-semibold leading-none tracking-[-0.6px]',
              item.valueClassName,
            ].join(' ')}
          >
            {summary[item.key]}
          </strong>
        </div>
      ))}
    </motion.section>
  );
};

export default DataSourceSummaryCards;
