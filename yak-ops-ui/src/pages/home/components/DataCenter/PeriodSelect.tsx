import { useIntl } from '@umijs/max';
import { Check, ChevronDown } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

import type { HomeDataCenterPeriodKey } from '../../types';
import { PERIOD_OPTIONS } from './constants';

interface PeriodSelectProps {
  value: HomeDataCenterPeriodKey;
  onChange: (value: HomeDataCenterPeriodKey) => void;
}

export function PeriodSelect({ value, onChange }: PeriodSelectProps) {
  const intl = useIntl();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const current = PERIOD_OPTIONS.find((option) => option.key === value)!;

  useEffect(() => {
    const handlePointerDown = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };

    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, []);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((currentOpen) => !currentOpen)}
        className={`
          flex h-[27px] items-center rounded-[7px] border px-2.5 text-[12px]
          transition-colors
          ${
            open
              ? 'border-[#9db7ef] bg-[#f7f9fd] text-[#454a54]'
              : 'border-transparent bg-[#f4f5f7] text-[#5f646e] hover:bg-[#eceef2]'
          }
        `}
      >
        <span className="pr-2 text-[#727781]">
          {intl.formatMessage({ id: 'pages.home.dataCenter.period.time' })}
        </span>
        <span className="mr-1.5 h-[12px] w-px bg-[#dcdfe4]" />
        <span className="min-w-[34px] text-left font-medium text-[#4d525c]">
          {intl.formatMessage({ id: current.messageId })}
        </span>
        <ChevronDown
          size={13}
          strokeWidth={1.8}
          className={`ml-1 transition-transform ${open ? 'rotate-180' : ''}`}
        />
      </button>

      {open && (
        <div className="absolute right-0 top-[31px] z-30 w-[140px] overflow-hidden rounded-[8px] border border-[#eceef2] bg-white py-1 shadow-[0_8px_22px_rgba(31,35,41,0.10)]">
          {PERIOD_OPTIONS.map((option) => {
            const selected = option.key === value;
            return (
              <button
                key={option.key}
                type="button"
                onClick={() => {
                  onChange(option.key);
                  setOpen(false);
                }}
                className="flex h-[34px] w-full items-center px-3 text-left text-[12px] text-[#444952] transition-colors hover:bg-[#f6f7f9]"
              >
                <span className="flex w-5 items-center">
                  {selected && <Check size={14} strokeWidth={2} />}
                </span>
                {intl.formatMessage({ id: option.messageId })}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
