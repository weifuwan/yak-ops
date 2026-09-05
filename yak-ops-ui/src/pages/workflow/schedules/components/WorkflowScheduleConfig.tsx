import CronSchedulerInput from '@/components/CronSchedulerEditor/CronSchedulerInput';
import YakButton from '@/components/YakButton';
import { getWorkflowDefinition, type WorkflowDefinition } from '@/services/workflow/definitions';
import {
  createWorkflowSchedule,
  deleteWorkflowSchedule,
  listWorkflowSchedules,
  updateWorkflowSchedule,
  type WorkflowBackfill,
  type WorkflowSchedule,
  type WorkflowScheduleExecutionStrategy,
  type WorkflowScheduleMisfireStrategy,
} from '@/services/workflow/schedules';
import { BRAND_THEME } from '@/styles/brand';
import { history, useIntl, useLocation, useParams } from '@umijs/max';
import {
  ConfigProvider,
  DatePicker,
  Empty,
  Form,
  Modal,
  Select,
  Spin,
  Tooltip,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import {
  ArrowLeft,
  DatabaseBackup,
  History,
  ListTree,
  Save,
  Trash2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import BackfillDrawer from './BackfillDrawer';
import BackfillHistoryDrawer from './BackfillHistoryDrawer';
import TriggerLedgerDrawer from './TriggerLedgerDrawer';

interface FormValues {
  cronExpression: string;
  timezone: string;
  effectiveRange?: [Dayjs, Dayjs];
  executionStrategy: WorkflowScheduleExecutionStrategy;
  misfireStrategy: WorkflowScheduleMisfireStrategy;
}

const SECTION_ITEMS = [
  { key: 'schedule-basic', messageId: 'pages.workflow.schedule.section.basic' },
  { key: 'schedule-strategy', messageId: 'pages.workflow.schedule.section.strategy' },
] as const;
type SectionKey = (typeof SECTION_ITEMS)[number]['key'];
const LAST_SECTION_KEY = SECTION_ITEMS[SECTION_ITEMS.length - 1].key;
const SCROLL_BOTTOM_THRESHOLD = 12;
const SECTION_TOP_OFFSET = 24;
const LOCATE_LOCK_DURATION = 650;

const CRON_PRESETS = [
  { messageId: 'pages.workflow.schedule.preset.daily2', value: '0 0 2 * * ?' },
  { messageId: 'pages.workflow.schedule.preset.hourly', value: '0 0 * * * ?' },
  { messageId: 'pages.workflow.schedule.preset.10m', value: '0 0/10 * * * ?' },
] as const;

const WORKFLOW_STATUS_MESSAGE_IDS: Record<string, string> = {
  DRAFT: 'pages.workflow.status.definition.draft',
  ONLINE: 'pages.workflow.status.definition.online',
  OFFLINE: 'pages.workflow.status.definition.offline',
};

interface SectionNavigatorProps {
  activeKey: SectionKey;
  onSelect: (key: SectionKey) => void;
}

function SectionNavigator({ activeKey, onSelect }: SectionNavigatorProps) {
  const intl = useIntl();
  return (
    <nav
      aria-label={intl.formatMessage({ id: 'pages.workflow.schedule.navigatorAria' })}
      className="rounded-xl bg-white px-3 py-4"
    >
      <div className="mb-3 px-2 text-[12px] font-semibold text-[#344054]">
        {intl.formatMessage({ id: 'pages.workflow.schedule.navigator' })}
      </div>
      <div className="relative">
        <span aria-hidden className="absolute bottom-4 left-[13px] top-4 w-px bg-[#e4e7ec]" />
        <div className="space-y-1">
          {SECTION_ITEMS.map((item) => {
            const active = activeKey === item.key;
            return (
              <button
                key={item.key}
                type="button"
                aria-current={active ? 'location' : undefined}
                className={[
                  'group relative flex w-full cursor-pointer items-center gap-3 rounded-lg border-0 px-2 py-2 text-left transition-colors',
                  active ? 'bg-[rgba(254,44,85,0.08)]' : 'bg-transparent hover:bg-[#f7f8fa]',
                ].join(' ')}
                onClick={() => onSelect(item.key)}
              >
                <span
                  aria-hidden
                  className={[
                    'relative z-10 h-[11px] w-[11px] shrink-0 rounded-full border transition-all duration-200',
                    active
                      ? 'border-[var(--yak-brand-color)] bg-[var(--yak-brand-color)] shadow-[0_0_0_3px_rgba(254,44,85,0.12)]'
                      : 'border-[#d0d5dd] bg-[#98a2b3] group-hover:border-[#98a2b3]',
                  ].join(' ')}
                />
                <span
                  className={[
                    'text-[12px] leading-5 transition-colors',
                    active
                      ? 'font-semibold text-[var(--yak-brand-color)]'
                      : 'font-normal text-[#667085] group-hover:text-[#344054]',
                  ].join(' ')}
                >
                  {intl.formatMessage({ id: item.messageId })}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </nav>
  );
}

function SectionCard({ id, title, children }: {
  id: SectionKey;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section id={id} className="rounded-xl bg-white px-6 py-5">
      <div className="mb-5 text-[14px] font-semibold text-[#161823]">{title}</div>
      {children}
    </section>
  );
}

function resolveInternalScheduleName(
  workflow: WorkflowDefinition,
  schedule: WorkflowSchedule | undefined,
  fallback: string,
) {
  return schedule?.name?.trim() || fallback;
}

export default function WorkflowScheduleConfigPage() {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;
  const location = useLocation();
  const routeParams = useParams<{ id?: string }>();
  const pageRootRef = useRef<HTMLDivElement>(null);
  const locatingSectionRef = useRef<SectionKey | null>(null);
  const locateTimerRef = useRef<number>();
  const [form] = Form.useForm<FormValues>();

  const workflowId = useMemo(
    () =>
      routeParams.id ||
      new URLSearchParams(location.search).get('workflowId') ||
      '',
    [location.search, routeParams.id],
  );
  const [workflow, setWorkflow] = useState<WorkflowDefinition>();
  const [schedules, setSchedules] = useState<WorkflowSchedule[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activeSection, setActiveSection] = useState<SectionKey>('schedule-basic');
  const [ledgerOpen, setLedgerOpen] = useState(false);
  const [ledgerBackfill, setLedgerBackfill] = useState<WorkflowBackfill>();
  const [backfillOpen, setBackfillOpen] = useState(false);
  const [backfillHistoryOpen, setBackfillHistoryOpen] = useState(false);

  const primarySchedule = useMemo(
    () =>
      [...schedules].sort((left, right) =>
        String(left.createTime || '').localeCompare(String(right.createTime || '')),
      )[0],
    [schedules],
  );
  const canEdit = Boolean(
    workflow && workflow.status !== 'ONLINE' && primarySchedule?.status !== 'ONLINE',
  );

  const load = useCallback(async (silent = false) => {
    if (!workflowId) return;
    if (!silent) setLoading(true);
    try {
      const [workflowData, scheduleData] = await Promise.all([
        getWorkflowDefinition(workflowId),
        listWorkflowSchedules({ workflowId }),
      ]);
      setWorkflow(workflowData);
      setSchedules(scheduleData || []);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.schedule.loadFailed' }),
      );
    } finally {
      if (!silent) setLoading(false);
    }
  }, [workflowId]);

  useEffect(() => {
    if (!workflowId) {
      history.replace('/workflow/definitions');
      return;
    }
    void load();
  }, [load, workflowId]);

  useEffect(() => {
    if (!workflow) return;
    form.setFieldsValue({
      cronExpression: primarySchedule?.cronExpression || '0 0 2 * * ?',
      timezone: primarySchedule?.timezone || 'Asia/Shanghai',
      effectiveRange:
        primarySchedule?.startTime && primarySchedule?.endTime
          ? [dayjs(primarySchedule.startTime), dayjs(primarySchedule.endTime)]
          : undefined,
      executionStrategy: primarySchedule?.executionStrategy || 'SERIAL_WAIT',
      misfireStrategy: primarySchedule?.misfireStrategy || 'FIRE_ONCE',
    });
  }, [form, primarySchedule, workflow]);

  const updateActiveSection = useCallback(() => {
    const container = pageRootRef.current;
    if (!container || locatingSectionRef.current) return;
    const maxScrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
    if (maxScrollTop - container.scrollTop <= SCROLL_BOTTOM_THRESHOLD) {
      setActiveSection(LAST_SECTION_KEY);
      return;
    }
    const threshold = container.getBoundingClientRect().top + 140;
    let nextActive: SectionKey = SECTION_ITEMS[0].key;
    SECTION_ITEMS.forEach((item) => {
      const element = document.getElementById(item.key);
      if (element && element.getBoundingClientRect().top <= threshold) nextActive = item.key;
    });
    setActiveSection(nextActive);
  }, []);

  useEffect(() => {
    const container = pageRootRef.current;
    if (!container || !workflow) return undefined;
    let animationFrameId = 0;
    const handleViewportChange = () => {
      window.cancelAnimationFrame(animationFrameId);
      animationFrameId = window.requestAnimationFrame(updateActiveSection);
    };
    container.addEventListener('scroll', handleViewportChange, { passive: true });
    window.addEventListener('resize', handleViewportChange);
    updateActiveSection();
    return () => {
      window.cancelAnimationFrame(animationFrameId);
      container.removeEventListener('scroll', handleViewportChange);
      window.removeEventListener('resize', handleViewportChange);
    };
  }, [updateActiveSection, workflow]);

  useEffect(
    () => () => {
      if (locateTimerRef.current) window.clearTimeout(locateTimerRef.current);
    },
    [],
  );

  const handleSectionLocate = (key: SectionKey) => {
    const container = pageRootRef.current;
    const element = document.getElementById(key);
    if (!container || !element) return;
    if (locateTimerRef.current) window.clearTimeout(locateTimerRef.current);
    locatingSectionRef.current = key;
    setActiveSection(key);
    const maxScrollTop = Math.max(0, container.scrollHeight - container.clientHeight);
    const containerRect = container.getBoundingClientRect();
    const elementRect = element.getBoundingClientRect();
    const expectedTop =
      container.scrollTop + elementRect.top - containerRect.top - SECTION_TOP_OFFSET;
    const nextScrollTop =
      key === LAST_SECTION_KEY
        ? maxScrollTop
        : Math.min(Math.max(expectedTop, 0), maxScrollTop);
    container.scrollTo({ top: nextScrollTop, behavior: 'smooth' });
    locateTimerRef.current = window.setTimeout(() => {
      locatingSectionRef.current = null;
      updateActiveSection();
    }, LOCATE_LOCK_DURATION);
  };

  const handleSave = async () => {
    if (!workflow || !canEdit) return;
    try {
      const values = await form.validateFields();
      const payload = {
        name: resolveInternalScheduleName(
          workflow,
          primarySchedule,
          intl.formatMessage(
            { id: 'pages.workflow.schedule.internalName' },
            { name: workflow.name },
          ),
        ),
        cronExpression: values.cronExpression.trim(),
        timezone: values.timezone,
        startTime: values.effectiveRange?.[0]?.toISOString(),
        endTime: values.effectiveRange?.[1]?.toISOString(),
        executionStrategy: values.executionStrategy,
        misfireStrategy: values.misfireStrategy,
        input: primarySchedule?.input || {},
      };
      setSaving(true);
      if (primarySchedule) await updateWorkflowSchedule(primarySchedule.id, payload);
      else await createWorkflowSchedule(workflow.id, payload);
      message.success(intlRef.current.formatMessage({ id: 'pages.workflow.schedule.saved' }));
      await load(true);
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({ id: 'pages.workflow.schedule.saveFailed' }),
      );
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = () => {
    if (!primarySchedule || !canEdit) return;
    Modal.confirm({
      centered: true,
      title: intl.formatMessage({ id: 'pages.workflow.schedule.deleteTitle' }),
      content: intl.formatMessage({ id: 'pages.workflow.schedule.deleteContent' }),
      okText: intl.formatMessage({ id: 'pages.workflow.common.delete' }),
      cancelText: intl.formatMessage({ id: 'pages.workflow.common.cancel' }),
      okYakButtonProps: { danger: true },
      async onOk() {
        try {
          await deleteWorkflowSchedule(primarySchedule.id);
          message.success(intlRef.current.formatMessage({ id: 'pages.workflow.schedule.deleted' }));
          await load(true);
        } catch (error) {
          message.error(
            error instanceof Error
              ? error.message
              : intlRef.current.formatMessage({ id: 'pages.workflow.schedule.deleteFailed' }),
          );
        }
      },
    });
  };

  const openBackfillLedger = (backfill: WorkflowBackfill) => {
    setLedgerBackfill(backfill);
    setBackfillHistoryOpen(false);
    setLedgerOpen(true);
  };

  if (!workflowId) return null;
  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f8fa]">
        <Spin size="large" />
      </div>
    );
  }
  if (!workflow) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f8fa]">
        <Empty
          description={intl.formatMessage({ id: 'pages.workflow.schedule.notFound' })}
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        >
          <YakButton onClick={() => history.push('/workflow/definitions')}>
            {intl.formatMessage({ id: 'pages.workflow.schedule.backDefinitions' })}
          </YakButton>
        </Empty>
      </div>
    );
  }

  const workflowStatusId = WORKFLOW_STATUS_MESSAGE_IDS[workflow.status];
  const workflowStatusLabel = workflowStatusId
    ? intl.formatMessage({ id: workflowStatusId })
    : workflow.status;

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="h-[calc(100vh-64px)] overflow-hidden bg-[#f7f8fa] text-[#161823]">
        <div ref={pageRootRef} className="h-full overflow-y-auto overscroll-contain scroll-smooth">
          <div className="mx-auto grid w-full max-w-[1280px] grid-cols-1 gap-6 px-6 pb-6 pt-6 max-xl:max-w-[1040px] xl:grid-cols-[minmax(0,1fr)_160px]">
            <div className="min-w-0">
              <main className="space-y-4">
                <div className="rounded-xl bg-white px-6 py-4">
                  <div className="flex flex-wrap items-center justify-between gap-4">
                    <div className="flex min-w-0 items-center gap-3">
                      <YakButton
                        type="text"
                        size="small"
                        icon={<ArrowLeft size={16} />}
                        className="!h-8 !w-8 !shrink-0 !px-0"
                        onClick={() => history.push('/workflow/definitions')}
                      />
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <h1 className="m-0 truncate text-[18px] font-semibold leading-8 text-[#161823]">
                            {intl.formatMessage({ id: 'pages.workflow.schedule.title' })}
                          </h1>
                          <span className="rounded-md bg-[#f2f4f7] px-2 py-1 text-[11px] font-medium text-[#667085]">
                            {workflowStatusLabel}
                          </span>
                        </div>
                        <div className="truncate text-[12px] text-[#667085]">{workflow.name}</div>
                      </div>
                    </div>

                    {primarySchedule ? (
                      <div className="flex shrink-0 items-center gap-1.5">
                        <Tooltip
                          title={
                            workflow.status !== 'ONLINE'
                              ? intl.formatMessage({ id: 'pages.workflow.schedule.backfillRequiresOnline' })
                              : undefined
                          }
                        >
                          <span>
                            <YakButton
                              size="small"
                              disabled={workflow.status !== 'ONLINE'}
                              icon={<DatabaseBackup size={14} />}
                              onClick={() => setBackfillOpen(true)}
                            >
                              {intl.formatMessage({ id: 'pages.workflow.schedule.backfill' })}
                            </YakButton>
                          </span>
                        </Tooltip>
                        <YakButton
                          size="small"
                          icon={<ListTree size={14} />}
                          onClick={() => {
                            setLedgerBackfill(undefined);
                            setLedgerOpen(true);
                          }}
                        >
                          {intl.formatMessage({ id: 'pages.workflow.schedule.triggerLedger' })}
                        </YakButton>
                        <YakButton
                          size="small"
                          icon={<History size={14} />}
                          onClick={() => setBackfillHistoryOpen(true)}
                        >
                          {intl.formatMessage({ id: 'pages.workflow.schedule.backfillHistory' })}
                        </YakButton>
                      </div>
                    ) : null}
                  </div>
                </div>

                {schedules.length > 1 ? (
                  <div className="rounded-lg bg-[#fffaeb] px-4 py-2.5 text-[12px] text-[#7a2e0e]">
                    {intl.formatMessage(
                      { id: 'pages.workflow.schedule.multipleSchedules' },
                      { count: schedules.length },
                    )}
                  </div>
                ) : null}
                {!canEdit ? (
                  <div className="rounded-lg bg-[#f2f4f7] px-4 py-2.5 text-[12px] text-[#667085]">
                    {intl.formatMessage({ id: 'pages.workflow.schedule.readonly' })}
                  </div>
                ) : null}

                <ConfigProvider variant="filled">
                  <Form form={form} layout="vertical" requiredMark="optional" disabled={!canEdit}>
                    <div className="space-y-4">
                      <SectionCard
                        id="schedule-basic"
                        title={intl.formatMessage({ id: 'pages.workflow.schedule.section.basic' })}
                      >
                        <Form.Item
                          name="cronExpression"
                          label={intl.formatMessage({ id: 'pages.workflow.schedule.cron' })}
                          rules={[
                            {
                              required: true,
                              message: intl.formatMessage({ id: 'pages.workflow.schedule.cronRequired' }),
                            },
                          ]}
                        >
                          <CronSchedulerInput disabled={!canEdit} />
                        </Form.Item>
                        <div className="-mt-3 mb-5 flex flex-wrap items-center gap-1.5">
                          <span className="mr-1 text-[11px] text-[#98a2b3]">
                            {intl.formatMessage({ id: 'pages.workflow.schedule.quickSet' })}
                          </span>
                          {CRON_PRESETS.map((preset) => (
                            <YakButton
                              key={preset.value}
                              size="small"
                              type="text"
                              disabled={!canEdit}
                              className="!h-7 !bg-[#f7f8fa] !px-2.5 !text-[11px] !text-[#667085] hover:!bg-[#eef0f3]"
                              onClick={() => form.setFieldValue('cronExpression', preset.value)}
                            >
                              {intl.formatMessage({ id: preset.messageId })}
                            </YakButton>
                          ))}
                        </div>
                        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                          <Form.Item
                            name="timezone"
                            label={intl.formatMessage({ id: 'pages.workflow.schedule.timezone' })}
                            rules={[{ required: true }]}
                          >
                            <Select
                              options={[
                                { value: 'Asia/Shanghai', label: 'Asia/Shanghai' },
                                { value: 'Asia/Tokyo', label: 'Asia/Tokyo' },
                                { value: 'UTC', label: 'UTC' },
                              ]}
                            />
                          </Form.Item>
                          <Form.Item
                            name="effectiveRange"
                            label={intl.formatMessage({ id: 'pages.workflow.schedule.effectiveRange' })}
                          >
                            <DatePicker.RangePicker showTime className="w-full" />
                          </Form.Item>
                        </div>
                      </SectionCard>

                      <SectionCard
                        id="schedule-strategy"
                        title={intl.formatMessage({ id: 'pages.workflow.schedule.section.strategy' })}
                      >
                        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                          <Form.Item
                            name="executionStrategy"
                            label={intl.formatMessage({ id: 'pages.workflow.schedule.executionStrategy' })}
                            rules={[{ required: true }]}
                          >
                            <Select
                              options={[
                                { value: 'SERIAL_WAIT', label: intl.formatMessage({ id: 'pages.workflow.schedule.execution.serialWait' }) },
                                { value: 'SERIAL_DISCARD', label: intl.formatMessage({ id: 'pages.workflow.schedule.execution.serialDiscard' }) },
                                { value: 'PARALLEL', label: intl.formatMessage({ id: 'pages.workflow.schedule.execution.parallel' }) },
                              ]}
                            />
                          </Form.Item>
                          <Form.Item
                            name="misfireStrategy"
                            label={intl.formatMessage({ id: 'pages.workflow.schedule.misfireStrategy' })}
                            rules={[{ required: true }]}
                          >
                            <Select
                              options={[
                                { value: 'FIRE_ONCE', label: intl.formatMessage({ id: 'pages.workflow.schedule.misfire.fireOnce' }) },
                                { value: 'SKIP', label: intl.formatMessage({ id: 'pages.workflow.schedule.misfire.skip' }) },
                              ]}
                            />
                          </Form.Item>
                        </div>
                      </SectionCard>
                    </div>
                  </Form>
                </ConfigProvider>
              </main>

              <footer className="sticky bottom-0 z-50 mt-4 rounded-xl bg-white px-6 py-3">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <YakButton
                      type="primary"
                      loading={saving}
                      disabled={!canEdit}
                      icon={<Save size={15} />}
                      className={[
                        '!h-9 !min-w-[112px] !rounded-lg !px-5 !font-medium',
                        canEdit
                          ? '!text-white'
                          : '!border-[#e3e6eb] !bg-[#f2f3f5] !text-[#a5acb6] !shadow-none',
                      ].join(' ')}
                      onClick={() => void handleSave()}
                    >
                      {intl.formatMessage({ id: 'pages.workflow.schedule.save' })}
                    </YakButton>
                    <YakButton
                      disabled={saving}
                      className="!h-9 !min-w-[96px] !rounded-lg !border-0 !bg-[#f2f3f5] !px-5 !font-medium !text-[#344054] hover:!bg-[#e9eaec]"
                      onClick={() => history.push('/workflow/definitions')}
                    >
                      {intl.formatMessage({ id: 'pages.workflow.common.back' })}
                    </YakButton>
                  </div>
                  {primarySchedule && canEdit ? (
                    <YakButton danger type="text" icon={<Trash2 size={14} />} onClick={handleDelete}>
                      {intl.formatMessage({ id: 'pages.workflow.schedule.delete' })}
                    </YakButton>
                  ) : null}
                </div>
              </footer>
            </div>

            <aside className="hidden xl:block">
              <div className="sticky top-6">
                <SectionNavigator activeKey={activeSection} onSelect={handleSectionLocate} />
              </div>
            </aside>
          </div>
        </div>
      </div>

      <BackfillDrawer
        open={backfillOpen}
        schedule={primarySchedule}
        onClose={() => setBackfillOpen(false)}
        onCreated={() => load(true)}
      />
      <BackfillHistoryDrawer
        open={backfillHistoryOpen}
        workflowId={workflow.id}
        scheduleId={primarySchedule?.id}
        onClose={() => setBackfillHistoryOpen(false)}
        onOpenTriggers={openBackfillLedger}
      />
      <TriggerLedgerDrawer
        open={ledgerOpen}
        schedule={primarySchedule}
        backfillId={ledgerBackfill?.id}
        backfillName={ledgerBackfill?.name}
        onClose={() => {
          setLedgerOpen(false);
          setLedgerBackfill(undefined);
        }}
      />
    </ConfigProvider>
  );
}
