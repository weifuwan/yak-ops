import messages from "./messages";

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
      messages[descriptor.id as keyof typeof messages] ??
      descriptor.defaultMessage ??
      descriptor.id;
    return interpolate(template, values);
  },
};

export const useIntl = () => intl;
