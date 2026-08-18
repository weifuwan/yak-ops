import {
  assertValidScreenTemplate,
  builtinScreenTemplates,
} from '@/components/screen-engine';
import type { ScreenTemplate } from '@/components/screen-engine';

const CUSTOM_STORAGE_KEY = 'yak-ops:screen-custom-templates:v1';
const BUILTIN_SETTINGS_STORAGE_KEY = 'yak-ops:screen-builtin-template-settings:v1';

export type ManagedScreenTemplateStatus = 'draft' | 'published' | 'offline';
export type ManagedScreenTemplateSource = 'builtin' | 'custom';

interface StoredCustomTemplate {
  template: ScreenTemplate;
  status: ManagedScreenTemplateStatus;
  sort: number;
  createdAt: string;
  updatedAt: string;
  derivedFrom?: string;
}

interface BuiltinTemplateSetting {
  status: 'published' | 'offline';
  sort: number;
}

export interface ManagedScreenTemplate {
  id: string;
  template: ScreenTemplate;
  source: ManagedScreenTemplateSource;
  status: ManagedScreenTemplateStatus;
  sort: number;
  createdAt?: string;
  updatedAt?: string;
  derivedFrom?: string;
}

const now = () => new Date().toISOString();

const cloneTemplate = (template: ScreenTemplate): ScreenTemplate => (
  JSON.parse(JSON.stringify(template)) as ScreenTemplate
);

const createCustomId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return `custom-${crypto.randomUUID()}`;
  }
  return `custom-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
};

const readJson = <T,>(key: string, fallback: T): T => {
  if (typeof window === 'undefined') return fallback;
  try {
    const raw = window.localStorage.getItem(key);
    return raw ? JSON.parse(raw) as T : fallback;
  } catch {
    return fallback;
  }
};

const writeJson = (key: string, value: unknown) => {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(key, JSON.stringify(value));
};

const readBuiltinSettings = (): Record<string, BuiltinTemplateSetting> => {
  const values = readJson<Record<string, unknown>>(BUILTIN_SETTINGS_STORAGE_KEY, {});
  const settings: Record<string, BuiltinTemplateSetting> = {};
  Object.entries(values || {}).forEach(([id, value]) => {
    if (!value || typeof value !== 'object') return;
    const candidate = value as Partial<BuiltinTemplateSetting>;
    if (candidate.status !== 'published' && candidate.status !== 'offline') return;
    settings[id] = {
      status: candidate.status,
      sort: typeof candidate.sort === 'number' && Number.isFinite(candidate.sort)
        ? candidate.sort
        : 1000,
    };
  });
  return settings;
};

const writeBuiltinSettings = (settings: Record<string, BuiltinTemplateSetting>) => {
  writeJson(BUILTIN_SETTINGS_STORAGE_KEY, settings);
};

const isStatus = (value: unknown): value is ManagedScreenTemplateStatus => (
  value === 'draft' || value === 'published' || value === 'offline'
);

const readCustomTemplates = (): StoredCustomTemplate[] => {
  const values = readJson<unknown>(CUSTOM_STORAGE_KEY, []);
  if (!Array.isArray(values)) return [];

  return values.flatMap((value): StoredCustomTemplate[] => {
    if (!value || typeof value !== 'object') return [];
    const candidate = value as Partial<StoredCustomTemplate>;
    if (!candidate.template || !isStatus(candidate.status)) return [];
    try {
      assertValidScreenTemplate(candidate.template);
    } catch {
      return [];
    }
    const timestamp = now();
    return [{
      template: candidate.template,
      status: candidate.status,
      sort: typeof candidate.sort === 'number' && Number.isFinite(candidate.sort)
        ? candidate.sort
        : 1000,
      createdAt: candidate.createdAt || timestamp,
      updatedAt: candidate.updatedAt || candidate.createdAt || timestamp,
      derivedFrom: candidate.derivedFrom,
    }];
  });
};

const writeCustomTemplates = (templates: StoredCustomTemplate[]) => {
  writeJson(CUSTOM_STORAGE_KEY, templates);
};

const builtinRecords = (): ManagedScreenTemplate[] => {
  const settings = readBuiltinSettings();
  return builtinScreenTemplates.map((template, index) => {
    const setting = settings[template.id];
    return {
      id: template.id,
      template,
      source: 'builtin' as const,
      status: setting?.status ?? 'published',
      sort: setting?.sort ?? (index + 1) * 10,
    };
  });
};

const customRecords = (): ManagedScreenTemplate[] => readCustomTemplates().map((item) => ({
  id: item.template.id,
  template: item.template,
  source: 'custom',
  status: item.status,
  sort: item.sort,
  createdAt: item.createdAt,
  updatedAt: item.updatedAt,
  derivedFrom: item.derivedFrom,
}));

const sortRecords = (left: ManagedScreenTemplate, right: ManagedScreenTemplate) => (
  left.sort - right.sort
  || left.template.name.localeCompare(right.template.name, 'zh-CN')
);

export const listManagedScreenTemplates = () => [
  ...builtinRecords(),
  ...customRecords(),
].sort(sortRecords);

export const listAvailableScreenTemplates = () => listManagedScreenTemplates()
  .filter((record) => record.status === 'published')
  .sort(sortRecords);

export const resolveScreenTemplateById = (id: string) => (
  listManagedScreenTemplates().find((record) => record.id === id)?.template
);

export const getManagedScreenTemplate = (id: string) => (
  listManagedScreenTemplates().find((record) => record.id === id)
);

export const listManagedScreenTemplateCategories = (publishedOnly = false) => [
  ...new Set(
    (publishedOnly ? listAvailableScreenTemplates() : listManagedScreenTemplates())
      .map((record) => record.template.category),
  ),
];

const normalizeCustomTemplate = (template: ScreenTemplate, id: string): ScreenTemplate => {
  const next = cloneTemplate(template);
  next.id = id;
  return assertValidScreenTemplate(next);
};

export const copyManagedScreenTemplate = (sourceId: string) => {
  const source = getManagedScreenTemplate(sourceId);
  if (!source) throw new Error('大屏模板不存在');

  const id = createCustomId();
  const timestamp = now();
  const template = normalizeCustomTemplate({
    ...cloneTemplate(source.template),
    name: `${source.template.name} - 副本`,
  }, id);
  const customs = readCustomTemplates();
  const record: StoredCustomTemplate = {
    template,
    status: 'draft',
    sort: 1000 + customs.length * 10,
    createdAt: timestamp,
    updatedAt: timestamp,
    derivedFrom: source.id,
  };
  writeCustomTemplates([...customs, record]);
  return record;
};

export const importManagedScreenTemplate = (template: ScreenTemplate) => {
  const id = createCustomId();
  const timestamp = now();
  const normalized = normalizeCustomTemplate(template, id);
  const customs = readCustomTemplates();
  const record: StoredCustomTemplate = {
    template: normalized,
    status: 'draft',
    sort: 1000 + customs.length * 10,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
  writeCustomTemplates([...customs, record]);
  return record;
};

export const parseAndImportManagedScreenTemplate = (json: string) => {
  let parsed: ScreenTemplate;
  try {
    parsed = JSON.parse(json) as ScreenTemplate;
  } catch {
    throw new Error('模板 JSON 格式不正确');
  }
  return importManagedScreenTemplate(parsed);
};

export const updateCustomScreenTemplate = (id: string, template: ScreenTemplate) => {
  let updated: StoredCustomTemplate | undefined;
  const nextTemplate = normalizeCustomTemplate(template, id);
  const customs = readCustomTemplates().map((item): StoredCustomTemplate => {
    if (item.template.id !== id) return item;
    const next: StoredCustomTemplate = {
      ...item,
      template: nextTemplate,
      updatedAt: now(),
    };
    updated = next;
    return next;
  });
  if (!updated) throw new Error('自定义大屏模板不存在');
  writeCustomTemplates(customs);
  return updated;
};

export const setManagedScreenTemplateStatus = (
  id: string,
  status: ManagedScreenTemplateStatus,
) => {
  const builtin = builtinScreenTemplates.find((template) => template.id === id);
  if (builtin) {
    if (status === 'draft') throw new Error('内置模板不能设置为草稿');
    const settings = readBuiltinSettings();
    const current = settings[id];
    settings[id] = {
      status,
      sort: current?.sort ?? (builtinScreenTemplates.findIndex((item) => item.id === id) + 1) * 10,
    };
    writeBuiltinSettings(settings);
    return;
  }

  let found = false;
  const customs = readCustomTemplates().map((item): StoredCustomTemplate => {
    if (item.template.id !== id) return item;
    found = true;
    return { ...item, status, updatedAt: now() };
  });
  if (!found) throw new Error('大屏模板不存在');
  writeCustomTemplates(customs);
};

export const deleteCustomScreenTemplate = (id: string) => {
  const customs = readCustomTemplates();
  const next = customs.filter((item) => item.template.id !== id);
  if (next.length === customs.length) throw new Error('自定义大屏模板不存在');
  writeCustomTemplates(next);
};

export const stringifyScreenTemplate = (template: ScreenTemplate) => JSON.stringify(template, null, 2);
