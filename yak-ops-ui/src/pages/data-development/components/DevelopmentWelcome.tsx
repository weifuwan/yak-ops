import { useIntl } from '@umijs/max';
import { Code2 } from 'lucide-react';

import type { DevelopmentNodeType } from '../types';

interface DevelopmentWelcomeProps {
  onCreateNode: (type: DevelopmentNodeType) => void;
}

const DevelopmentIllustration = () => (
  <svg width="200" height="150" viewBox="0 0 220 160" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <circle cx="57" cy="34" r="20" fill="#FFE8EE" />
    <path d="M57 24V44M47 34H67" stroke="#FE2C55" strokeWidth="4" strokeLinecap="round" />
    <rect x="61" y="42" width="108" height="76" rx="4" fill="white" stroke="#515151" strokeWidth="1.5" />
    <path d="M61 57H169" stroke="#515151" strokeWidth="1.5" />
    <circle cx="70" cy="50" r="2" fill="#C6CACD" />
    <circle cx="78" cy="50" r="2" fill="#C6CACD" />
    <circle cx="86" cy="50" r="2" fill="#C6CACD" />
    <rect x="69" y="65" width="18" height="45" rx="2" fill="#F1F2F3" />
    <path d="M74 73H82M74 81H80M74 89H83" stroke="#9CA3AA" strokeWidth="1.5" strokeLinecap="round" />
    <path d="M97 70H139" stroke="#515151" strokeWidth="2" strokeLinecap="round" />
    <path d="M97 79H151M97 88H131M97 97H145M97 106H120" stroke="#C6CACD" strokeWidth="2" strokeLinecap="round" />
    <path d="M50 118H177L166 133H39L50 118Z" fill="#E7E9EB" stroke="#515151" strokeWidth="1.5" strokeLinejoin="round" />
    <ellipse cx="172" cy="101" rx="18" ry="7" fill="white" stroke="#515151" strokeWidth="1.5" />
    <path d="M154 101V122C154 126 162 129 172 129C182 129 190 126 190 122V101" fill="white" stroke="#515151" strokeWidth="1.5" />
    <path d="M154 111C154 115 162 118 172 118C182 118 190 115 190 111" stroke="#C6CACD" strokeWidth="1.5" />
    <circle cx="42" cy="95" r="8" fill="white" stroke="#515151" strokeWidth="1.5" />
    <path d="M33 111C34 103 38 99 44 99C51 99 55 105 55 113V129H30C30 122 31 116 33 111Z" fill="#515151" />
    <path d="M50 106C55 109 59 111 67 111" stroke="#515151" strokeWidth="2" strokeLinecap="round" />
    <path d="M143 68L151 72L147 74L145 79L143 68Z" fill="#515151" />
  </svg>
);

const DevelopmentWelcome = (_: DevelopmentWelcomeProps) => {
  const intl = useIntl();
  const shortcuts = [
    ['pages.dataDevelopment.welcome.save', 'Ctrl + S'],
    ['pages.dataDevelopment.welcome.run', 'Ctrl + Enter'],
    ['pages.dataDevelopment.welcome.quickOpen', 'Ctrl + P'],
    ['pages.dataDevelopment.welcome.closeTab', 'Ctrl + W'],
  ] as const;

  return (
    <main className="flex min-w-0 flex-1 flex-col overflow-hidden bg-white">
      <div className="flex h-9 shrink-0 items-end border-b border-[#e4e7ec] bg-[#f7f8fa]">
        <div className="flex h-9 items-center gap-2 border-r border-[#e4e7ec] bg-white px-3.5 text-[12px] font-medium text-[#344054]">
          <Code2 size={13} strokeWidth={1.7} className="text-[#98a2b3]" />
          {intl.formatMessage({ id: 'pages.dataDevelopment.welcome.tab' })}
        </div>
      </div>
      <div className="relative flex min-h-0 flex-1 items-center justify-center overflow-auto bg-white pb-10">
        <div className="flex w-[360px] -translate-y-6 flex-col items-center">
          <DevelopmentIllustration />
          <div className="mt-1 text-[14px] font-medium text-[#515151]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.welcome.title' })}
          </div>
          <div className="mt-7 w-[280px]">
            {shortcuts.map(([id, keys]) => (
              <div key={keys} className="flex h-9 items-center justify-between px-1 text-[12px]">
                <span className="text-[#667085]">{intl.formatMessage({ id })}</span>
                <kbd className="min-w-[72px] whitespace-nowrap rounded-[4px] border border-[#e4e7ec] bg-[#fafafa] px-2 py-[2px] text-center font-mono text-[10px] font-normal leading-4 text-[#8b93a6] shadow-[0_1px_1px_rgba(16,24,40,0.03)]">
                  {keys}
                </kbd>
              </div>
            ))}
          </div>
        </div>
        <div className="absolute inset-x-0 bottom-0 flex h-6 items-center justify-between border-t border-[#eef0f2] bg-[#fafafa] px-2.5 text-[10px] text-[#98a2b3]">
          <span>Yak Ops · Data Development</span>
          <span>{intl.formatMessage({ id: 'pages.dataDevelopment.welcome.ready' })}</span>
        </div>
      </div>
    </main>
  );
};

export default DevelopmentWelcome;
