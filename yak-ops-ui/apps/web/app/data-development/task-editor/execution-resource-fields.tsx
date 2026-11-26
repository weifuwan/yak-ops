import { useMemo, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import { useAuth } from "@/hooks/use-auth";
import {
  listAllEnvironments,
  type EnvironmentRecord,
} from "@/service/environment";
import { listWorkerGroupNames } from "@/service/worker-group";

const FIELD_CLASS =
  "grid grid-cols-[116px_minmax(0,1fr)] items-center gap-3 max-md:grid-cols-1";
const FIELD_LABEL_CLASS = "text-xs text-[#687385]";

type SelectOption = {
  value: string;
  label: string;
  disabled?: boolean;
};

function SettingField({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className={FIELD_CLASS}>
      <span className={FIELD_LABEL_CLASS}>{label}</span>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

function SettingsSelect({
  value,
  options,
  disabled,
  onChange,
  placeholder,
}: {
  value: string;
  options: SelectOption[];
  disabled?: boolean;
  onChange: (value: string) => void;
  placeholder: string;
}) {
  const items = useMemo(
    () => Object.fromEntries(options.map((option) => [option.value, option.label])),
    [options],
  );

  return (
    <Select<string>
      size="small"
      items={items}
      value={value}
      disabled={disabled}
      onValueChange={(next) => {
        if (next) onChange(next);
      }}
    >
      <SelectTrigger variant="outlined" className="w-full">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent emptyContent={options.length === 0 ? "暂无可选项" : undefined}>
        {options.map((option) => (
          <SelectItem
            key={option.value}
            value={option.value}
            disabled={option.disabled}
          >
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

/**
 * An environment must be explicitly bound to the selected worker group.
 * An environment without worker-group bindings is not selectable.
 */
export function isEnvironmentAvailableForWorkerGroup(
  environment: Pick<EnvironmentRecord, "workerGroups">,
  workerGroup: string,
): boolean {
  const workerGroups = environment.workerGroups ?? [];
  return Boolean(workerGroup) && workerGroups.length > 0 && workerGroups.includes(workerGroup);
}

export function ExecutionResourceFields({
  workerGroup,
  environmentCode,
  disabled = false,
  onChange,
}: {
  workerGroup: string;
  environmentCode: string;
  disabled?: boolean;
  onChange: (workerGroup: string, environmentCode: string) => void;
}) {
  const { currentUser } = useAuth();
  const userId = currentUser?.id ?? "";

  const workerGroupsQuery = useQuery({
    queryKey: ["data-development", "execution-resources", "worker-groups", userId],
    queryFn: ({ signal }) => listWorkerGroupNames(signal),
    enabled: Boolean(userId),
    staleTime: 30_000,
  });
  const environmentsQuery = useQuery({
    queryKey: ["data-development", "execution-resources", "environments", userId],
    queryFn: ({ signal }) => listAllEnvironments(signal),
    enabled: Boolean(userId),
    staleTime: 30_000,
  });

  const workerGroups = useMemo(() => {
    const values = workerGroupsQuery.data ?? [];
    return values.includes("default") ? values : ["default", ...values];
  }, [workerGroupsQuery.data]);

  const workerGroupOptions = useMemo(() => {
    const options = workerGroups.map((group) => ({
      value: group,
      label: group,
    }));
    if (workerGroup && !workerGroups.includes(workerGroup)) {
      options.unshift({
        value: workerGroup,
        label: workerGroup + "（当前配置）",
      });
    }
    return options;
  }, [workerGroup, workerGroups]);

  const environments = environmentsQuery.data ?? [];
  const availableEnvironments = useMemo(
    () =>
      environments.filter((environment) =>
        isEnvironmentAvailableForWorkerGroup(environment, workerGroup),
      ),
    [environments, workerGroup],
  );

  const environmentOptions = useMemo(() => {
    const options: SelectOption[] = [
      { value: "-1", label: "不使用环境" },
    ];
    const selectedEnvironment = environments.find(
      (environment) => String(environment.code) === environmentCode,
    );
    const selectedEnvironmentIsAvailable = availableEnvironments.some(
      (environment) => String(environment.code) === environmentCode,
    );

    // Keep a saved value visible until the user changes it, even if its
    // environment is no longer bound to the selected worker group.
    if (environmentCode !== "-1" && !selectedEnvironmentIsAvailable) {
      options.push({
        value: environmentCode,
        label: selectedEnvironment
          ? selectedEnvironment.name + "（当前配置）"
          : "当前配置环境（" + environmentCode + "）",
      });
    }

    options.push(
      ...availableEnvironments.map((environment) => ({
        value: String(environment.code),
        label: environment.name,
      })),
    );
    return options;
  }, [availableEnvironments, environmentCode, environments]);

  const handleWorkerGroupChange = (nextWorkerGroup: string) => {
    const selectedEnvironment = environments.find(
      (environment) => String(environment.code) === environmentCode,
    );
    const nextEnvironmentCode =
      selectedEnvironment &&
      !isEnvironmentAvailableForWorkerGroup(selectedEnvironment, nextWorkerGroup)
        ? "-1"
        : environmentCode;

    onChange(nextWorkerGroup, nextEnvironmentCode);
  };

  return (
    <>
      <SettingField label="Worker 分组">
        <div className="space-y-1">
          <SettingsSelect
            value={workerGroup}
            options={workerGroupOptions}
            disabled={
              disabled ||
              workerGroupsQuery.isPending ||
              workerGroupsQuery.isError
            }
            onChange={handleWorkerGroupChange}
            placeholder={
              workerGroupsQuery.isPending
                ? "正在加载 Worker 分组"
                : "请选择 Worker 分组"
            }
          />
          {workerGroupsQuery.isError ? (
            <button
              type="button"
              onClick={() => void workerGroupsQuery.refetch()}
              className="text-[11px] text-[var(--yak-components-muted-text)] hover:underline"
            >
              Worker 分组加载失败，点击重试
            </button>
          ) : null}
        </div>
      </SettingField>

      <SettingField label="执行环境">
        <div className="space-y-1">
          <SettingsSelect
            value={environmentCode}
            options={environmentOptions}
            disabled={
              disabled ||
              environmentsQuery.isPending ||
              environmentsQuery.isError
            }
            onChange={(nextEnvironmentCode) =>
              onChange(workerGroup, nextEnvironmentCode)
            }
            placeholder={
              environmentsQuery.isPending
                ? "正在加载执行环境"
                : "请选择执行环境"
            }
          />
          {environmentsQuery.isError ? (
            <button
              type="button"
              onClick={() => void environmentsQuery.refetch()}
              className="text-[11px] text-[var(--yak-components-muted-text)] hover:underline"
            >
              执行环境加载失败，点击重试
            </button>
          ) : null}
        </div>
      </SettingField>
    </>
  );
}
