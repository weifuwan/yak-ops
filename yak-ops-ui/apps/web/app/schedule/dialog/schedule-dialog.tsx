import {
  Alert,
  Button,
  Modal,
  Spinner,
  Switch,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
} from "@yak-ops/yak-ui";
import { useAuth } from "@/hooks/use-auth";
import { ScheduleForm } from "../form/schedule-form";
import { ScheduleFireHistory } from "../history/fire-history";
import { formatScheduleTime, registrationText, scheduleReasonText } from "../schedule-presentation";
import { useScheduleSession } from "./use-schedule-session";
import type { ScheduleDialogProps } from "./types";

export function ScheduleDialog(props: ScheduleDialogProps) {
  const { currentUser } = useAuth();
  return props.open ? (
    <ScheduleDialogSession
      key={`${currentUser?.id}/${props.targetId}/${props.historyScheduleId || "current"}`}
      {...props}
    />
  ) : null;
}

function ScheduleDialogSession({
  targetId,
  published,
  onClose,
  onChanged,
  onViewInstance,
  historyScheduleId,
}: ScheduleDialogProps) {
  const session = useScheduleSession({
    targetId,
    published,
    onClose,
    onChanged,
    historyScheduleId,
  });
  const {
    tab,
    setTab,
    schedule,
    form,
    setForm,
    dirty,
    loading,
    pending,
    error,
    needsReload,
    historyOnly,
    blocked,
    confirmation,
    setConfirmation,
    save,
    toggle,
    remove,
    reload,
    close,
  } = session;
  const observation = schedule ? registrationText(schedule) : null;
  const fireId = historyScheduleId || schedule?.id;
  return (
    <>
      <Modal
        open
        onClose={close}
        title={historyOnly ? "触发记录" : "调度"}
        width={tab === "fires" ? 980 : 680}
        closeLabel="关闭调度"
        footer={
          tab === "config" ? (
            <>
              <div className="mr-auto">
                {schedule ? (
                  <Button
                    size="sm"
                    variant="danger"
                    isDisabled={pending || isPending || needsReload}
                    onClick={() => setConfirmation("delete")}
                  >
                    删除调度
                  </Button>
                ) : null}
              </div>
              <Button variant="secondary" size="sm" isDisabled={pending} onClick={close}>
                关闭
              </Button>
              <Button
                size="sm"
                variant="primary"
                isPending={pending}
                isDisabled={
                  isPending ||
                  needsReload ||
                  Boolean(schedule?.enabled) ||
                  !form.cronExpression.trim() ||
                  !dirty
                }
                onClick={() => void save()}
              >
                保存
              </Button>
            </>
          ) : (
            <Button variant="secondary" size="sm" onClick={close}>
              关闭
            </Button>
          )
        }
      >
        <Tabs value={tab} onValueChange={setTab}>
          {!historyOnly ? (
            <TabsList>
              <TabsTab value="config">配置</TabsTab>
              <TabsTab value="fires" disabled={!schedule}>
                触发记录
              </TabsTab>
            </TabsList>
          ) : null}
          <TabsPanel value="config" className="space-y-4 pt-4">
            {loading ? (
              <div className="flex justify-center py-8">
                <Spinner label="加载调度" />
              </div>
            ) : (
              <>
                {error ? (
                  <Alert>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span>{error}</span>
                      <Button variant="secondary"
                        size="sm"
                        onClick={() => {
                          if (dirty) setConfirmation("reload");
                          else void reload();
                        }}
                        isDisabled={pending}
                      >
                        重新加载
                      </Button>
                    </div>
                  </Alert>
                ) : null}
                {schedule ? (
                  <div className="flex flex-wrap items-center gap-3 text-xs text-[var(--yak-components-muted-text)]">
                    <label className="flex items-center gap-2">
                      启用调度
                      <Switch
                        size="small"
                        checked={schedule.enabled}
                        disabled={pending || needsReload || dirty || Boolean(blocked)}
                        onCheckedChange={(enabled) => void toggle(enabled)}
                      />
                    </label>
                    {observation ? (
                      <span
                        role="status"
                        className={
                          schedule.registrationStatus === "ERROR"
                            ? "text-[var(--yak-color-danger)]"
                            : ""
                        }
                      >
                        {observation}
                      </span>
                    ) : null}
                    {blocked ? (
                      <span>
                        {published
                          ? scheduleReasonText(schedule.eligibilityReason)
                          : "请先上线任务"}
                      </span>
                    ) : null}
                  </div>
                ) : null}
                {schedule?.enabled ? (
                  <div className="text-xs text-[var(--yak-components-muted-text)]">
                    请先停止调度再修改配置
                  </div>
                ) : null}
                <ScheduleForm
                  value={form}
                  onChange={(value) => {
                    setForm(value);
                  }}
                  disabled={pending || needsReload || Boolean(schedule?.enabled)}
                  allowClear={!schedule}
                />
                {schedule?.enabled && schedule.nextFireTime ? (
                  <div className="text-xs text-[var(--yak-components-muted-text)]">
                    下次触发：{formatScheduleTime(schedule.nextFireTime, schedule.timeZone)}
                  </div>
                ) : null}
              </>
            )}
          </TabsPanel>
          <TabsPanel value="fires" className="pt-4">
            {tab === "fires" && fireId ? (
              <ScheduleFireHistory
                key={fireId}
                scheduleId={fireId}
                onViewInstance={(id) => {
                  onClose();
                  onViewInstance(id);
                }}
              />
            ) : null}
          </TabsPanel>
        </Tabs>
      </Modal>
      <Modal
        open={Boolean(confirmation)}
        title={
          confirmation === "delete"
            ? "删除调度"
            : confirmation === "reload"
              ? "重新加载配置"
              : "放弃修改"
        }
        onClose={() => setConfirmation(undefined)}
        width={420}
        closeLabel="关闭确认"
        footer={
          <>
            <Button variant="secondary" size="sm" onClick={() => setConfirmation(undefined)}>
              取消
            </Button>
            <Button
              size="sm"
              variant="danger"
              onClick={() => {
                if (confirmation === "delete") void remove();
                else if (confirmation === "reload") {
                  setConfirmation(undefined);
                  void reload();
                } else onClose();
              }}
            >
              确认
            </Button>
          </>
        }
      >
        <p className="text-sm">
          {confirmation === "delete"
            ? "删除调度？已提交的实例不受影响。"
            : "未保存的调度修改将被丢弃。"}
        </p>
      </Modal>
    </>
  );
}
