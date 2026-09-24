import type { FormRule } from '../../formRuntime';

import type {
  DynamicFormField,
  DynamicFormFieldRule,
  DynamicFormSchemaResponse,
  DynamicFormSection,
  DynamicFormVisibilityCondition,
  DynamicFormVisibilityOperator,
} from '../../../types';

export interface DynamicKeyValueRow {
  key: string;
  value: string;
}

export const transformRules = (
  rules: DynamicFormFieldRule[] | undefined,
  fieldType?: DynamicFormField['type'],
): FormRule[] => {
  if (!rules) return [];

  return rules.map((rule) => {
    const formRule: FormRule = {
      message: rule.message,
      ...(fieldType === 'NUMBER' ? { type: 'number' as const } : {}),
    };
    if (rule.required === true) formRule.required = true;
    if (typeof rule.min === 'number') formRule.min = rule.min;
    if (typeof rule.max === 'number') formRule.max = rule.max;
    if (rule.pattern) {
      try {
        formRule.pattern = new RegExp(rule.pattern);
      } catch {
        // 后端插件配置错误时不让整个表单崩溃，服务端仍会再次校验。
      }
    }
    return formRule;
  });
};

/** 将历史 JSON 对象或 JSON 字符串统一转换成 Key / Value 行数据。 */
export const toKeyValueRows = (value: unknown): DynamicKeyValueRow[] => {
  if (value === undefined || value === null || value === '') return [];

  if (Array.isArray(value)) {
    return value
      .filter((item) => item && typeof item === 'object')
      .map((item) => {
        const row = item as Record<string, unknown>;
        return {
          key: String(row.key ?? ''),
          value: String(row.value ?? ''),
        };
      });
  }

  if (typeof value === 'string') {
    try {
      return toKeyValueRows(JSON.parse(value));
    } catch {
      return [];
    }
  }

  if (typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>).map(([key, rowValue]) => ({
      key,
      value: rowValue === undefined || rowValue === null ? '' : String(rowValue),
    }));
  }

  return [];
};

export const getConfigInitialValues = (fields: DynamicFormField[]) => {
  const initialValues: Record<string, unknown> = {};
  fields.forEach((field) => {
    initialValues[field.key] = getFieldDefaultValue(field);
  });
  return initialValues;
};

export const getFieldDefaultValue = (field: DynamicFormField) => {
  const value = field.defaultValue;
  if (value === undefined || value === null || value === '') {
    return field.type === 'CUSTOM_SELECT' ? [] : value;
  }

  switch (field.type) {
    case 'NUMBER':
      return Number(value);
    case 'SWITCH':
      return typeof value === 'boolean' ? value : value === 'true';
    case 'CUSTOM_SELECT':
      return toKeyValueRows(value);
    default:
      return value;
  }
};

/**
 * 将详情接口中保存的 JSON 对象转换成动态表单组件需要的值形态。
 * CUSTOM_SELECT 在编辑态使用行数据，但保存协议仍保持 JSON 对象。
 */
export const normalizeConfigValuesForForm = (
  fields: DynamicFormField[],
  values?: Record<string, unknown>,
): Record<string, unknown> => {
  if (!values) return {};
  const normalized = { ...values };
  fields.forEach((field) => {
    if (field.type === 'CUSTOM_SELECT' && field.key in normalized) {
      normalized[field.key] = toKeyValueRows(normalized[field.key]);
    }
  });
  return normalized;
};

const normalizeOperator = (
  operator?: DynamicFormVisibilityOperator | string,
  condition?: DynamicFormVisibilityCondition,
): DynamicFormVisibilityOperator => {
  const value = operator?.trim().toUpperCase();
  if (
    value === 'EQUALS' ||
    value === 'NOT_EQUALS' ||
    value === 'IN' ||
    value === 'NOT_IN' ||
    value === 'TRUTHY' ||
    value === 'FALSY'
  ) {
    return value;
  }
  return condition && 'value' in condition ? 'EQUALS' : 'TRUTHY';
};

const normalizeVisibilityConditions = (
  field: DynamicFormField,
): DynamicFormVisibilityCondition[] => {
  const raw = field.visibleWhen;
  if (!raw) return [];
  const conditions = Array.isArray(raw) ? raw : [raw];
  const dependencies = field.dependsOn || [];

  return conditions.map((condition, index) => ({
    ...condition,
    field:
      condition.field?.trim() || dependencies[index]?.trim() || dependencies[0]?.trim(),
    operator: normalizeOperator(condition.operator, condition),
  }));
};

/**
 * 归一字段联动依赖：显式 dependsOn 与 visibleWhen 中引用的字段会合并去重。
 */
export const getFieldDependencies = (field: DynamicFormField): string[] => {
  const conditions = normalizeVisibilityConditions(field);
  return Array.from(
    new Set(
      [...(field.dependsOn || []), ...conditions.map((condition) => condition.field)]
        .map((value) => value?.trim())
        .filter((value): value is string => Boolean(value)),
    ),
  );
};

const getValueByPath = (values: Record<string, unknown>, path?: string): unknown => {
  if (!path) return undefined;
  return path.split('.').reduce<unknown>((current, segment) => {
    if (!current || typeof current !== 'object') return undefined;
    return (current as Record<string, unknown>)[segment];
  }, values);
};

const equals = (left: unknown, right: unknown) => Object.is(left, right);

const matchVisibilityCondition = (
  condition: DynamicFormVisibilityCondition,
  values: Record<string, unknown>,
) => {
  const actual = getValueByPath(values, condition.field);
  const operator = normalizeOperator(condition.operator, condition);
  const candidates = condition.values || (Array.isArray(condition.value) ? condition.value : []);

  switch (operator) {
    case 'NOT_EQUALS':
      return !equals(actual, condition.value);
    case 'IN':
      return candidates.some((candidate) => equals(actual, candidate));
    case 'NOT_IN':
      return !candidates.some((candidate) => equals(actual, candidate));
    case 'TRUTHY':
      return Boolean(actual);
    case 'FALSY':
      return !actual;
    case 'EQUALS':
    default:
      return equals(actual, condition.value);
  }
};

/**
 * 判断动态字段当前是否可见。未配置 visibleWhen 时始终显示；多条件使用 AND 语义。
 */
export const isDynamicFieldVisible = (
  field: DynamicFormField,
  values: Record<string, unknown>,
) => {
  const conditions = normalizeVisibilityConditions(field);
  if (conditions.length === 0) return true;
  return conditions.every(
    (condition) => Boolean(condition.field) && matchVisibilityCondition(condition, values),
  );
};

/**
 * 兼容历史 Schema：
 * - driverLocation 曾经以普通 INPUT 字段下发，统一提升为 DRIVER。
 * - JDBC properties 曾经以 TEXTAREA 下发，统一提升为 Key / Value 编辑器。
 */
const normalizeFormField = (field: DynamicFormField): DynamicFormField => {
  const normalized: DynamicFormField = {
    ...field,
    dependsOn: getFieldDependencies(field),
    visibleWhen: normalizeVisibilityConditions(field),
  };
  if (field.key === 'driverLocation' && field.type !== 'DRIVER') {
    normalized.type = 'DRIVER';
  }
  if (field.key === 'properties' && field.type === 'TEXTAREA') {
    normalized.type = 'CUSTOM_SELECT';
    normalized.placeholder = '按键值对添加 JDBC 扩展参数，例如 useSSL = false';
  }
  return normalized;
};

/** 只把 Driver 移到 SSH 前面，不改变插件其它自定义分区的相对顺序。 */
const moveDriverBeforeSsh = (
  sections: DynamicFormSection[],
): DynamicFormSection[] => {
  const driverIndex = sections.findIndex((section) => section.key === 'driver');
  const sshIndex = sections.findIndex((section) => section.key === 'ssh');
  if (driverIndex < 0 || sshIndex < 0 || driverIndex < sshIndex) return sections;

  const reordered = [...sections];
  const [driverSection] = reordered.splice(driverIndex, 1);
  const nextSshIndex = reordered.findIndex((section) => section.key === 'ssh');
  reordered.splice(nextSshIndex, 0, driverSection);
  return reordered;
};

/**
 * 将新版 sections 和旧版 formFields 统一归一成分区结构。
 *
 * 标准能力区确保驱动配置位于 SSH 隧道之前；其它插件自定义 Section 保持原顺序。
 * 新插件优先使用 sections；旧插件无需迁移，扁平 formFields 会自动落到
 * 一个始终展开的“连接参数”分区中。
 */
export const normalizeFormSections = (
  schema?: Pick<DynamicFormSchemaResponse, 'sections' | 'formFields'>,
): DynamicFormSection[] => {
  const sections = (schema?.sections || [])
    .filter((section) => Array.isArray(section?.fields) && section.fields.length > 0)
    .map((section, index) => ({
      ...section,
      key: section.key?.trim() || `section-${index + 1}`,
      title: section.title?.trim() || '连接参数',
      collapsible: section.collapsible === true,
      defaultExpanded: section.defaultExpanded !== false,
      fields: section.fields.map(normalizeFormField),
    }));

  if (sections.length > 0) return moveDriverBeforeSsh(sections);

  const legacyFields = schema?.formFields || [];
  if (legacyFields.length === 0) return [];

  return [
    {
      key: 'connection',
      title: '连接参数',
      collapsible: false,
      defaultExpanded: true,
      fields: legacyFields.map(normalizeFormField),
    },
  ];
};

export const flattenFormSectionFields = (
  sections: DynamicFormSection[],
): DynamicFormField[] => sections.flatMap((section) => section.fields || []);

/** 只给当前为空的字段补默认值。 */
export const patchEmptyWithDefaults = (
  current: Record<string, unknown>,
  defaults: Record<string, unknown>,
) => {
  const patch: Record<string, unknown> = {};
  Object.keys(defaults).forEach((key) => {
    const value = current?.[key];
    if (value === undefined || value === null || value === '') {
      patch[key] = defaults[key];
    }
  });
  return patch;
};
