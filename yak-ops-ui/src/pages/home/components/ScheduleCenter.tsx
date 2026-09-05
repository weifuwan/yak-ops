import {
  homeScheduleCenterApi,
  type HomeScheduleCalendar,
  type HomeScheduleDay,
} from '@/services/home';
import { history, useIntl } from '@umijs/max';
import { ChevronLeft, ChevronRight, Clock3 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { HomeEmptyState } from './HomeEmptyState';

const pad2 = (value: number) => String(value).padStart(2, '0');

interface CalendarCell {
  day: number | null;
  date: Date | null;
  isToday: boolean;
}

function buildCalendarWeeks(cursor: Date): CalendarCell[][] {
  const year = cursor.getFullYear();
  const month = cursor.getMonth();
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const today = new Date();

  const cells: CalendarCell[] = Array.from({ length: 42 }, (_, index) => {
    const day = index - firstDay + 1;
    if (day < 1 || day > daysInMonth) {
      return { day: null, date: null, isToday: false };
    }
    const date = new Date(year, month, day);
    const isToday =
      today.getFullYear() === year &&
      today.getMonth() === month &&
      today.getDate() === day;
    return { day, date, isToday };
  });

  return Array.from({ length: 6 }, (_, weekIndex) =>
    cells.slice(weekIndex * 7, weekIndex * 7 + 7),
  );
}

const formatDateKey = (date: Date) =>
  `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;

const taskTypeMessageId = (taskType: string) => {
  if (taskType === 'OFFLINE_SYNC') return 'pages.home.scheduleCenter.taskType.sync';
  if (taskType === 'WORKFLOW') return 'pages.home.scheduleCenter.taskType.workflow';
  if (taskType === 'DATA_QUALITY') return 'pages.home.scheduleCenter.taskType.quality';
  return 'pages.home.scheduleCenter.taskType.task';
};

const tooltipPosition = (index: number) => {
  if (index <= 1) return 'left-0';
  if (index >= 5) return 'right-0';
  return 'left-1/2 -translate-x-1/2';
};

function CalendarWeek({
  cells,
  scheduleMap,
}: {
  cells: CalendarCell[];
  scheduleMap: Map<string, HomeScheduleDay>;
}) {
  const intl = useIntl();
  return (
    <div className="relative grid h-[39px] grid-cols-7 items-center">
      {cells.map((cell, index) => {
        const schedule = cell.date
          ? scheduleMap.get(formatDateKey(cell.date))
          : undefined;
        return (
          <div
            key={`${cell.day ?? 'empty'}-${index}`}
            className="group relative flex h-full items-center justify-center text-[12px] text-[#353943]"
          >
            {cell.day && (
              <>
                <span
                  className={`flex h-7 min-w-7 items-center justify-center rounded-full px-1 ${
                    cell.isToday
                      ? 'bg-[#eef4ff] font-semibold text-[#356fe8]'
                      : ''
                  }`}
                >
                  {pad2(cell.day)}
                </span>

                {schedule && schedule.count > 0 && (
                  <>
                    <span className="pointer-events-none absolute bottom-0 h-[3px] w-4 rounded-full bg-[#bfd3ff]" />
                    <div
                      className={`absolute top-[36px] z-40 hidden w-[230px] rounded-[9px] border border-[#eceef2] bg-white px-3 py-2.5 text-left shadow-[0_10px_28px_rgba(31,35,41,0.12)] group-hover:block ${tooltipPosition(index)}`}
                    >
                      <div className="flex items-center justify-between gap-3">
                        <strong className="text-[12px] font-semibold text-[#343842]">
                          {cell.date
                            ? new Intl.DateTimeFormat(intl.locale, {
                                month: 'long',
                                day: 'numeric',
                              }).format(cell.date)
                            : ''}
                        </strong>
                        <span className="text-[11px] text-[#8f949d]">
                          {intl.formatMessage(
                            { id: 'pages.home.scheduleCenter.taskCount' },
                            { count: schedule.count },
                          )}
                        </span>
                      </div>

                      <div className="mt-2 space-y-1.5">
                        {schedule.items.map((item) => (
                          <div
                            key={`${item.taskType}-${item.taskId}`}
                            className="flex min-w-0 items-center gap-2"
                          >
                            <span className="w-[36px] shrink-0 text-[10px] font-medium text-[#6e7480]">
                              {item.time}
                            </span>
                            <span className="min-w-0 flex-1 truncate text-[11px] text-[#454a54]">
                              {item.taskName}
                            </span>
                            <span className="shrink-0 text-[10px] text-[#969ba4]">
                              {intl.formatMessage({
                                id: taskTypeMessageId(item.taskType),
                              })}
                            </span>
                          </div>
                        ))}
                      </div>

                      {schedule.count > schedule.items.length && (
                        <div className="mt-2 border-t border-[#f0f1f3] pt-2 text-[10px] text-[#9ca1a9]">
                          {intl.formatMessage(
                            { id: 'pages.home.scheduleCenter.moreTasks' },
                            { count: schedule.count - schedule.items.length },
                          )}
                        </div>
                      )}
                    </div>
                  </>
                )}
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}

export default function ScheduleCenter() {
  const intl = useIntl();
  const now = useMemo(() => new Date(), []);
  const [cursor, setCursor] = useState(
    () => new Date(now.getFullYear(), now.getMonth(), 1),
  );
  const [calendar, setCalendar] = useState<HomeScheduleCalendar>();

  const weeks = useMemo(() => buildCalendarWeeks(cursor), [cursor]);
  const monthKey = `${cursor.getFullYear()}-${pad2(cursor.getMonth() + 1)}`;
  const monthLabel = new Intl.DateTimeFormat(intl.locale, {
    year: 'numeric',
    month: 'long',
  }).format(cursor);
  const monthOnlyLabel = new Intl.DateTimeFormat(intl.locale, {
    month: 'long',
  }).format(cursor);
  const scheduleMap = useMemo(
    () => new Map((calendar?.days || []).map((item) => [item.date, item])),
    [calendar?.days],
  );
  const weekdays = [
    'sun',
    'mon',
    'tue',
    'wed',
    'thu',
    'fri',
    'sat',
  ].map((day) =>
    intl.formatMessage({ id: `pages.home.scheduleCenter.weekday.${day}` }),
  );

  useEffect(() => {
    let active = true;
    homeScheduleCenterApi
      .calendar(monthKey)
      .then((response) => {
        if (active) setCalendar(response.data);
      })
      .catch(() => {
        if (active) setCalendar(undefined);
      });
    return () => {
      active = false;
    };
  }, [monthKey]);

  const moveMonth = (offset: number) => {
    setCursor(
      (current) =>
        new Date(current.getFullYear(), current.getMonth() + offset, 1),
    );
  };

  return (
    <section className="rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header className="flex items-start justify-between gap-4">
        <h2 className="text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.home.scheduleCenter.title' })}
        </h2>

        <div className="flex flex-col items-end gap-2">
          <button
            type="button"
            className="flex items-center gap-0.5 text-[12px] text-[#666b75] transition-colors hover:text-[#252832]"
          >
            {intl.formatMessage({ id: 'pages.home.common.viewMore' })}
            <ChevronRight size={14} strokeWidth={1.8} />
          </button>
          <span className="flex items-center gap-1.5 text-[11px] text-[#8b9099]">
            <span className="h-2 w-2 rounded-full bg-[#5b8cff]" />
            {intl.formatMessage({ id: 'pages.home.scheduleCenter.configured' })}
          </span>
        </div>
      </header>

      <div className="mt-4">
        <div className="flex items-center justify-center gap-2">
          <button
            type="button"
            onClick={() => moveMonth(-1)}
            className="flex h-7 w-7 items-center justify-center rounded-full text-[#91959d] transition-colors hover:bg-[#f5f6f8] hover:text-[#4a4f58]"
            aria-label={intl.formatMessage({
              id: 'pages.home.scheduleCenter.previousMonth',
            })}
          >
            <ChevronLeft size={16} strokeWidth={1.8} />
          </button>

          <div className="min-w-[128px] text-center text-[14px] font-semibold text-[#333741]">
            {monthLabel}
          </div>

          <button
            type="button"
            onClick={() => moveMonth(1)}
            className="flex h-7 w-7 items-center justify-center rounded-full text-[#91959d] transition-colors hover:bg-[#f5f6f8] hover:text-[#4a4f58]"
            aria-label={intl.formatMessage({
              id: 'pages.home.scheduleCenter.nextMonth',
            })}
          >
            <ChevronRight size={16} strokeWidth={1.8} />
          </button>
        </div>

        <div className="mt-2 grid grid-cols-7 text-center text-[11px] text-[#6d727c]">
          {weekdays.map((day) => (
            <span key={day}>{day}</span>
          ))}
        </div>

        <div className="mt-1">
          {weeks.map((week, index) => (
            <CalendarWeek key={index} cells={week} scheduleMap={scheduleMap} />
          ))}
        </div>
      </div>

      <div className="mt-4 border-t border-[#eef0f3] pt-4">
        <div className="flex items-center justify-between">
          <strong className="text-[13px] font-semibold text-[#3c4049]">
            {intl.formatMessage(
              { id: 'pages.home.scheduleCenter.monthOverview' },
              { month: monthOnlyLabel },
            )}
          </strong>
          <span className="text-[11px] text-[#999da5]">
            {intl.formatMessage(
              { id: 'pages.home.scheduleCenter.totalConfigs' },
              { count: calendar?.totalSchedules ?? 0 },
            )}
          </span>
        </div>

        <div className="mt-2.5 space-y-2">
          {(calendar?.overview || []).map((item) => (
            <button
              key={`${item.taskType}-${item.taskId}`}
              type="button"
              onClick={() => item.detailPath && history.push(item.detailPath)}
              className="group flex w-full items-center gap-2 text-left"
            >
              <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-[#5b8cff]" />
              <span className="min-w-0 flex-1 truncate text-[12px] text-[#464a53] transition-colors group-hover:text-[#252832]">
                {item.taskName}
              </span>
              <span className="shrink-0 rounded border border-[#eceef2] px-1.5 py-0.5 text-[10px] leading-4 text-[#8d929a]">
                {intl.formatMessage({ id: taskTypeMessageId(item.taskType) })}
              </span>
              <span className="w-[74px] shrink-0 text-right text-[10px] text-[#9da1a8]">
                {item.nextRunDate.slice(5)} {item.nextRunTime}
              </span>
            </button>
          ))}

          {calendar && calendar.overview.length === 0 && (
            <HomeEmptyState
              icon={Clock3}
              title={intl.formatMessage({ id: 'pages.home.scheduleCenter.empty' })}
              size="medium"
              className="min-h-[126px]"
            />
          )}
        </div>
      </div>
    </section>
  );
}
