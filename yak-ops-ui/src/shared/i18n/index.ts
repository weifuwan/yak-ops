import messages from "@/locales/zh-CN/data-source";

const LOGIN_MESSAGES: Record<string, string> = {
  "pages.login.success": "登录成功！",
};

const MESSAGE_MAP: Record<string, string> = {
  ...messages,
  ...LOGIN_MESSAGES,
};

type MessageDescriptor = {
  id: string;
  defaultMessage?: string;
};

const interpolate = (
  template: string,
  values?: Record<string, string | number>,
) => {
  if (!values) return template;
  return template.replace(/\{([^}]+)\}/g, (match, key: string) => {
    const value = values[key];
    return value === undefined || value === null ? match : String(value);
  });
};

export const intl = {
  formatMessage(
    descriptor: MessageDescriptor,
    values?: Record<string, string | number>,
  ) {
    const template =
      MESSAGE_MAP[descriptor.id] ?? descriptor.defaultMessage ?? descriptor.id;
    return interpolate(template, values);
  },
};

export const useIntl = () => intl;
