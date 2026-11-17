export type ShellLocalParam = {
  prop: string;
  direct: "IN" | "OUT";
  type: string;
  value: string;
  sensitive: boolean;
};

export type ShellTaskForm = {
  rawScript: string;
  resourceList: string[];
  localParams: ShellLocalParam[];
};

export function readShellTaskObject(value: unknown): Record<string, unknown> {
  if (typeof value === "string") {
    try {
      const parsed: unknown = JSON.parse(value);
      return parsed && typeof parsed === "object" && !Array.isArray(parsed)
        ? (parsed as Record<string, unknown>)
        : {};
    } catch {
      return {};
    }
  }

  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function readString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function readResourceName(value: unknown): string {
  if (typeof value === "string") return value;
  if (!value || typeof value !== "object") return "";
  return readString((value as Record<string, unknown>).resourceName);
}

export function parseShellTaskForm(value: unknown): ShellTaskForm {
  const raw = readShellTaskObject(value);
  const resourceList = Array.isArray(raw.resourceList)
    ? raw.resourceList.map(readResourceName).filter(Boolean)
    : [];
  const localParams = Array.isArray(raw.localParams)
    ? raw.localParams.map((item) => {
        const param = readShellTaskObject(item);
        return {
          prop: readString(param.prop),
          direct: param.direct === "OUT" ? "OUT" : "IN",
          type: readString(param.type) || "VARCHAR",
          value: readString(param.value),
          sensitive: param.sensitive === true,
        } satisfies ShellLocalParam;
      })
    : [];

  return {
    rawScript: readString(raw.rawScript),
    resourceList,
    localParams,
  };
}

export function buildShellTaskParams(
  form: ShellTaskForm,
  originalValue?: unknown,
): Record<string, unknown> {
  const original = readShellTaskObject(originalValue);

  return {
    ...original,
    rawScript: form.rawScript,
    resourceList: form.resourceList
      .map((resourceName) => resourceName.trim())
      .filter(Boolean)
      .map((resourceName) => ({ resourceName })),
    localParams: form.localParams
      .filter((item) => item.prop.trim())
      .map((item) => ({
        ...item,
        prop: item.prop.trim(),
        value: item.value ?? "",
      })),
  };
}
