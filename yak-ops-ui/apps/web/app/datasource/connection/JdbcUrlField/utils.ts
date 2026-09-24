import type { DynamicFormJdbcUrlLinkage } from '../../types';

export interface JdbcUrlStructuredValue {
  host?: string;
  port?: number;
  database?: string;
  suffix?: string;
}

const PLACEHOLDER_PATTERN = /\{(host|port|database)\}/g;

const escapeRegExp = (value: string) =>
  value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const normalizeHostForUrl = (host: string) => {
  const value = host.trim();
  if (!value) return value;
  if (value.startsWith('[') && value.endsWith(']')) return value;
  return value.includes(':') ? `[${value}]` : value;
};

const normalizeParsedHost = (host: string) => {
  const value = host.trim();
  if (value.startsWith('[') && value.endsWith(']')) {
    return value.slice(1, -1);
  }
  return value;
};

const buildTemplateMatcher = (template: string) => {
  const fieldOrder: Array<'host' | 'port' | 'database'> = [];
  let source = '^';
  let lastIndex = 0;

  for (const match of template.matchAll(PLACEHOLDER_PATTERN)) {
    const index = match.index ?? 0;
    source += escapeRegExp(template.slice(lastIndex, index));
    const field = match[1] as 'host' | 'port' | 'database';
    fieldOrder.push(field);

    if (field === 'host') {
      // 用户名密码写进 URL 时保持为“手工自定义 URL”，不要误识别成 host。
      source += '(\\[[^\\]]+\\]|[^@/?;]+?)';
    } else if (field === 'port') {
      source += '(\\d{1,5})';
    } else {
      source += '([^?;]+)';
    }
    lastIndex = index + match[0].length;
  }

  source += escapeRegExp(template.slice(lastIndex));
  source += '([?;].*)?$';
  return {
    pattern: new RegExp(source),
    fieldOrder,
  };
};

export const buildJdbcUrlFromTemplate = (
  linkage: DynamicFormJdbcUrlLinkage,
  value: JdbcUrlStructuredValue,
): string | undefined => {
  const template = linkage.template?.trim();
  const host = value.host?.trim();
  const port = Number(value.port);
  const database = value.database?.trim();

  if (!template || !host || !database || !Number.isInteger(port)) return undefined;
  if (port < 1 || port > 65535) return undefined;

  let url = template
    .replaceAll('{host}', normalizeHostForUrl(host))
    .replaceAll('{port}', String(port))
    .replaceAll('{database}', database);

  if (linkage.preserveSuffix !== false && value.suffix) {
    url += value.suffix;
  }
  return url;
};

export const parseJdbcUrlByTemplate = (
  linkage: DynamicFormJdbcUrlLinkage,
  url?: string,
): JdbcUrlStructuredValue | undefined => {
  const template = linkage.template?.trim();
  const value = url?.trim();
  if (!template || !value) return undefined;

  const { pattern, fieldOrder } = buildTemplateMatcher(template);
  const match = value.match(pattern);
  if (!match) return undefined;

  const parsed: JdbcUrlStructuredValue = {};
  fieldOrder.forEach((field, index) => {
    const matchedValue = match[index + 1];
    if (field === 'host') {
      parsed.host = normalizeParsedHost(matchedValue);
    } else if (field === 'port') {
      const port = Number(matchedValue);
      if (!Number.isInteger(port) || port < 1 || port > 65535) return;
      parsed.port = port;
    } else {
      parsed.database = matchedValue.trim();
    }
  });

  const suffix = match[fieldOrder.length + 1];
  if (suffix) parsed.suffix = suffix;

  if (!parsed.host || !parsed.port || !parsed.database) return undefined;
  return parsed;
};
