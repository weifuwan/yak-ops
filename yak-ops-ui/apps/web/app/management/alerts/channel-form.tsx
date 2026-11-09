import {
  Button,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { useEffect, useMemo, useState } from "react";

import {
  alertChannelTypeLabel,
  alertParamLabel,
  createAlertChannel,
  parseAlertPluginParams,
  testAlertChannel,
  updateAlertChannel,
  type AlertChannelRecord,
  type AlertPluginDefinition,
  type AlertPluginParam,
} from "@/service/alert";

const HORIZONTAL_FIELD_CLASS = "grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3";

interface Props {
  open: boolean;
  record?: AlertChannelRecord;
  plugins: AlertPluginDefinition[];
  onClose: () => void;
  onSaved: () => void;
}

function optionLabel(value: unknown, fallback: string) {
  if (value === "success") return "成功";
  if (value === "failure") return "失败";
  if (value === "all") return "全部";
  return alertParamLabel(fallback);
}

export function AlertChannelForm({ open, record, plugins, onClose, onSaved }: Props) {
  const [pluginId, setPluginId] = useState<number>();
  const [name, setName] = useState("");
  const [params, setParams] = useState<AlertPluginParam[]>([]);
  const [values, setValues] = useState<Record<string, unknown>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);

  const selectedPlugin = useMemo(
    () => plugins.find((plugin) => plugin.id === pluginId),
    [plugins, pluginId],
  );

  useEffect(() => {
    if (!open) return;
    const nextPluginId = record?.pluginDefineId ?? plugins[0]?.id;
    const source =
      record?.pluginInstanceParams ??
      plugins.find((plugin) => plugin.id === nextPluginId)?.pluginParams ??
      "";
    const nextParams = parseAlertPluginParams(source);
    setPluginId(nextPluginId);
    setName(record?.instanceName ?? "");
    setParams(nextParams);
    setValues(
      Object.fromEntries(nextParams.map((param) => [param.field, param.value ?? ""])),
    );
    setErrors({});
  }, [open, record, plugins]);

  const applyPlugin = (id: number) => {
    const plugin = plugins.find((item) => item.id === id);
    const nextParams = parseAlertPluginParams(plugin?.pluginParams);
    setPluginId(id);
    setParams(nextParams);
    setValues(
      Object.fromEntries(nextParams.map((param) => [param.field, param.value ?? ""])),
    );
    setErrors({});
  };

  const updateValue = (field: string, value: unknown) => {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  const validate = () => {
    const nextErrors: Record<string, string> = {};
    if (!name.trim()) nextErrors.name = "请输入通知渠道名称";
    for (const param of params) {
      const required = param.validate?.some((rule) => rule.required);
      if (!required) continue;
      const value = values[param.field];
      if (value === undefined || value === null || String(value).trim() === "") {
        nextErrors[param.field] =
          param.validate?.find((rule) => rule.required)?.message ||
          `请输入${param.title || param.name || param.field}`;
      }
    }
    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const serializedParams = () =>
    JSON.stringify(
      params.map((param) => ({
        ...param,
        value: values[param.field] ?? param.value ?? null,
      })),
    );

  const handleTest = async () => {
    if (!pluginId || testing || !validate()) return;
    setTesting(true);
    try {
      await testAlertChannel(pluginId, serializedParams());
      toast.success("测试消息发送成功");
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async () => {
    if (!pluginId || saving || !validate()) return;
    setSaving(true);
    try {
      const instanceName = name.trim();
      const pluginInstanceParams = serializedParams();
      if (record) {
        await updateAlertChannel(record.id, instanceName, pluginInstanceParams);
      } else {
        await createAlertChannel(pluginId, instanceName, pluginInstanceParams);
      }
      onClose();
      onSaved();
    } finally {
      setSaving(false);
    }
  };

  const visibleParams = params.filter(
    (param) => !param.hidden && param.display !== false && param.field !== "warningType",
  );

  return (
    <Modal
      open={open}
      centered
      width={620}
      bodyClassName="py-3"
      title={record ? "编辑通知渠道" : "新建通知渠道"}
      onClose={() => {
        if (!saving && !testing) onClose();
      }}
      footer={
        <>
          <Button
            size="sm"
            variant="secondary"
            isDisabled={saving || testing}
            onClick={() => void handleTest()}
          >
            测试发送
          </Button>
          <div className="flex-1" />
          <Button
            size="sm"
            variant="secondary"
            isDisabled={saving || testing}
            onClick={onClose}
          >
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            isPending={saving}
            isDisabled={testing || !pluginId}
            onClick={() => void handleSubmit()}
          >
            {record ? "保存" : "创建"}
          </Button>
        </>
      }
    >
      <div className="space-y-2.5">
        <Field className={HORIZONTAL_FIELD_CLASS}>
          <FieldLabel required className="pt-1.5">
            渠道类型
          </FieldLabel>
          {record ? (
            <Input
              size="small"
              variant="outlined"
              value={alertChannelTypeLabel(record.alertPluginName || selectedPlugin?.pluginName)}
              disabled
            />
          ) : (
            <Select<string>
              size="small"
              items={Object.fromEntries(
                plugins.map((plugin) => [String(plugin.id), alertChannelTypeLabel(plugin.pluginName)]),
              )}
              value={pluginId ? String(pluginId) : null}
              onValueChange={(value) => {
                if (value) applyPlugin(Number(value));
              }}
            >
              <SelectTrigger variant="outlined">
                <SelectValue placeholder="请选择渠道类型" />
              </SelectTrigger>
              <SelectContent>
                {plugins.map((plugin) => (
                  <SelectItem key={plugin.id} value={String(plugin.id)}>
                    <SelectItemText>{alertChannelTypeLabel(plugin.pluginName)}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </Field>

        <Field className={HORIZONTAL_FIELD_CLASS} invalid={Boolean(errors.name)}>
          <FieldLabel required htmlFor="alert-channel-name" className="pt-1.5">
            名称
          </FieldLabel>
          <div className="min-w-0">
            <Input
              id="alert-channel-name"
              size="small"
              variant="outlined"
              maxLength={128}
              value={name}
              placeholder="请输入通知渠道名称"
              onChange={(event) => {
                setName(event.target.value);
                setErrors((current) => ({ ...current, name: "" }));
              }}
            />
            <FieldError match={Boolean(errors.name)} className="mt-1">
              {errors.name}
            </FieldError>
          </div>
        </Field>

        {visibleParams.map((param) => {
          const required = param.validate?.some((rule) => rule.required);
          const value = values[param.field] ?? "";
          const label = alertParamLabel(param.title || param.name, param.field);
          const placeholder =
            typeof param.props?.placeholder === "string" ? param.props.placeholder : undefined;

          return (
            <Field
              key={param.field}
              className={HORIZONTAL_FIELD_CLASS}
              invalid={Boolean(errors[param.field])}
            >
              <FieldLabel required={required} className="pt-1.5">
                {label}
              </FieldLabel>
              <div className="min-w-0">
                {param.type === "radio" || param.type === "select" ? (
                  <Select<string>
                    size="small"
                    items={Object.fromEntries(
                      (param.options || []).map((option) => [
                        String(option.value),
                        optionLabel(option.value, option.label),
                      ]),
                    )}
                    value={value === "" ? null : String(value)}
                    onValueChange={(next) => updateValue(param.field, next ?? "")}
                  >
                    <SelectTrigger variant="outlined">
                      <SelectValue placeholder={placeholder || "请选择"} />
                    </SelectTrigger>
                    <SelectContent>
                      {(param.options || []).map((option) => (
                        <SelectItem
                          key={String(option.value)}
                          value={String(option.value)}
                          disabled={option.disabled}
                        >
                          <SelectItemText>
                            {optionLabel(option.value, option.label)}
                          </SelectItemText>
                          <SelectItemIndicator />
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                ) : (param.props?.rows || 0) > 1 ? (
                  <Textarea
                    size="small"
                    variant="outlined"
                    rows={Number(param.props?.rows) || 3}
                    value={String(value)}
                    placeholder={placeholder}
                    onValueChange={(next) => updateValue(param.field, String(next || ""))}
                  />
                ) : (
                  <Input
                    size="small"
                    variant="outlined"
                    type={param.type === "input-number" ? "number" : param.props?.type || "text"}
                    value={String(value)}
                    placeholder={placeholder}
                    onChange={(event) => updateValue(param.field, event.target.value)}
                  />
                )}
                <FieldError match={Boolean(errors[param.field])} className="mt-1">
                  {errors[param.field]}
                </FieldError>
              </div>
            </Field>
          );
        })}
      </div>
    </Modal>
  );
}
