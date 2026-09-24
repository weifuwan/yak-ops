import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useSyncExternalStore,
  type ReactNode,
} from "react";

export type FormNamePath = string | number | Array<string | number>;

export interface FormRule {
  required?: boolean;
  min?: number;
  max?: number;
  pattern?: RegExp;
  message?: string;
  type?: "number";
  validator?: (
    value: unknown,
    values: Record<string, unknown>,
  ) => void | Promise<void>;
}

export interface FormFieldPatch {
  name: FormNamePath;
  value?: unknown;
  errors?: string[];
}

export interface FormError {
  name: FormNamePath;
  errors: string[];
}

export class DataSourceFormValidationError extends Error {
  errorFields: FormError[];

  constructor(errorFields: FormError[]) {
    super("Datasource form validation failed");
    this.name = "DataSourceFormValidationError";
    this.errorFields = errorFields;
  }
}

export interface DataSourceFormInstance<T extends Record<string, unknown> = Record<string, unknown>> {
  getFieldValue: (name: FormNamePath) => unknown;
  getFieldsValue: (_all?: boolean) => T;
  getFieldError: (name: FormNamePath) => string[];
  setFieldValue: (name: FormNamePath, value: unknown) => void;
  setFieldsValue: (patch: Partial<T>) => void;
  setFields: (fields: FormFieldPatch[]) => void;
  resetFields: () => void;
  validateFields: (names?: FormNamePath[]) => Promise<T>;
  registerField: (name: FormNamePath, rules?: FormRule[]) => () => void;
  subscribe: (listener: () => void) => () => void;
  getVersion: () => number;
}

const normalizePath = (name: FormNamePath): Array<string | number> => {
  if (Array.isArray(name)) return name;
  if (typeof name === "number") return [name];
  return name.split(".").filter(Boolean);
};

const pathKey = (name: FormNamePath) => normalizePath(name).join(".");

const cloneValue = <T,>(value: T): T => {
  if (Array.isArray(value)) return value.map((item) => cloneValue(item)) as T;
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, item]) => [
        key,
        cloneValue(item),
      ]),
    ) as T;
  }
  return value;
};

const getAtPath = (root: unknown, name: FormNamePath): unknown => {
  return normalizePath(name).reduce<unknown>((current, part) => {
    if (current == null || typeof current !== "object") return undefined;
    return (current as Record<string | number, unknown>)[part];
  }, root);
};

const setAtPath = (
  root: Record<string, unknown>,
  name: FormNamePath,
  value: unknown,
): Record<string, unknown> => {
  const path = normalizePath(name);
  if (path.length === 0) return root;

  const next = cloneValue(root);
  let cursor: Record<string | number, unknown> = next;

  path.forEach((part, index) => {
    if (index === path.length - 1) {
      cursor[part] = value;
      return;
    }

    const nextPart = path[index + 1];
    const current = cursor[part];
    if (current == null || typeof current !== "object") {
      cursor[part] = typeof nextPart === "number" ? [] : {};
    } else {
      cursor[part] = cloneValue(current);
    }
    cursor = cursor[part] as Record<string | number, unknown>;
  });

  return next;
};

const isEmptyValue = (value: unknown) =>
  value === undefined ||
  value === null ||
  value === "" ||
  (Array.isArray(value) && value.length === 0);

const validateRule = async (
  rule: FormRule,
  value: unknown,
  values: Record<string, unknown>,
) => {
  if (rule.required && isEmptyValue(value)) {
    throw new Error(rule.message || "Required");
  }

  if (!isEmptyValue(value)) {
    if (rule.type === "number" && typeof value !== "number") {
      throw new Error(rule.message || "Invalid number");
    }

    const measurable =
      typeof value === "number"
        ? value
        : typeof value === "string" || Array.isArray(value)
          ? value.length
          : undefined;

    if (rule.min !== undefined && measurable !== undefined && measurable < rule.min) {
      throw new Error(rule.message || "Value is too small");
    }
    if (rule.max !== undefined && measurable !== undefined && measurable > rule.max) {
      throw new Error(rule.message || "Value is too large");
    }
    if (rule.pattern && typeof value === "string" && !rule.pattern.test(value)) {
      throw new Error(rule.message || "Invalid value");
    }
  }

  await rule.validator?.(value, values);
};

const createDataSourceForm = <
  T extends Record<string, unknown> = Record<string, unknown>,
>(): DataSourceFormInstance<T> => {
  let values = {} as T;
  let version = 0;
  const errors = new Map<string, string[]>();
  const rules = new Map<string, FormRule[]>();
  const listeners = new Set<() => void>();

  const notify = () => {
    version += 1;
    listeners.forEach((listener) => listener());
  };

  const setValue = (name: FormNamePath, value: unknown, clearError = true) => {
    values = setAtPath(values, name, value) as T;
    if (clearError) errors.delete(pathKey(name));
  };

  const instance: DataSourceFormInstance<T> = {
    getFieldValue: (name) => getAtPath(values, name),
    getFieldsValue: () => cloneValue(values),
    getFieldError: (name) => errors.get(pathKey(name)) || [],
    setFieldValue: (name, value) => {
      setValue(name, value);
      notify();
    },
    setFieldsValue: (patch) => {
      Object.entries(patch).forEach(([key, value]) => setValue(key, value));
      notify();
    },
    setFields: (fields) => {
      fields.forEach((field) => {
        if ("value" in field) setValue(field.name, field.value, false);
        if (field.errors) {
          if (field.errors.length > 0) errors.set(pathKey(field.name), field.errors);
          else errors.delete(pathKey(field.name));
        }
      });
      notify();
    },
    resetFields: () => {
      values = {} as T;
      errors.clear();
      notify();
    },
    validateFields: async (names) => {
      const targets = names?.map(pathKey) ?? Array.from(rules.keys());
      const nextErrors: FormError[] = [];
      const allValues = cloneValue(values) as Record<string, unknown>;

      for (const key of targets) {
        const fieldRules = rules.get(key) || [];
        const value = getAtPath(values, key);
        const fieldErrors: string[] = [];

        for (const rule of fieldRules) {
          try {
            await validateRule(rule, value, allValues);
          } catch (error) {
            fieldErrors.push(
              error instanceof Error && error.message ? error.message : "Invalid value",
            );
            break;
          }
        }

        if (fieldErrors.length > 0) {
          errors.set(key, fieldErrors);
          nextErrors.push({ name: key, errors: fieldErrors });
        } else {
          errors.delete(key);
        }
      }

      notify();

      if (nextErrors.length > 0) {
        throw new DataSourceFormValidationError(nextErrors);
      }

      return cloneValue(values);
    },
    registerField: (name, fieldRules = []) => {
      const key = pathKey(name);
      rules.set(key, fieldRules);
      return () => {
        rules.delete(key);
        errors.delete(key);
      };
    },
    subscribe: (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    getVersion: () => version,
  };

  return instance;
};

type AnyFormInstance = DataSourceFormInstance<any>;

const FormContext = createContext<AnyFormInstance | null>(null);

export function useDataSourceForm<
  T extends Record<string, unknown> = Record<string, unknown>,
>(): DataSourceFormInstance<T> {
  const ref = useRef<DataSourceFormInstance<T>>();
  if (!ref.current) ref.current = createDataSourceForm<T>();
  return ref.current;
}

export function DataSourceFormProvider({
  children,
  form,
}: {
  children: ReactNode;
  form: AnyFormInstance;
}) {
  return <FormContext.Provider value={form}>{children}</FormContext.Provider>;
}

export function useDataSourceFormInstance() {
  const form = useContext(FormContext);
  if (!form) throw new Error("useDataSourceFormInstance must be used inside DataSourceFormProvider");
  return form;
}

export function useFormValues<T extends Record<string, unknown>>(
  form: DataSourceFormInstance<T>,
): T {
  useSyncExternalStore(form.subscribe, form.getVersion, form.getVersion);
  return form.getFieldsValue();
}

export function useFormValue(
  name: FormNamePath,
  form?: DataSourceFormInstance,
): unknown {
  const contextForm = useContext(FormContext);
  const target = form ?? contextForm;
  if (!target) throw new Error("useFormValue requires a form instance");
  useSyncExternalStore(target.subscribe, target.getVersion, target.getVersion);
  return target.getFieldValue(name);
}

export interface DataSourceFormFieldState {
  value: unknown;
  error?: string;
  invalid: boolean;
  setValue: (value: unknown) => void;
  validate: () => Promise<void>;
}

export function DataSourceFormField({
  children,
  name,
  rules = [],
}: {
  children: (state: DataSourceFormFieldState) => ReactNode;
  name: FormNamePath;
  rules?: FormRule[];
}) {
  const form = useDataSourceFormInstance();
  const value = useFormValue(name, form);
  const error = form.getFieldError(name)[0];

  useEffect(() => form.registerField(name, rules), [form, name, rules]);

  return (
    <>
      {children({
        value,
        error,
        invalid: Boolean(error),
        setValue: (next) => form.setFieldValue(name, next),
        validate: async () => {
          await form.validateFields([name]);
        },
      })}
    </>
  );
}
