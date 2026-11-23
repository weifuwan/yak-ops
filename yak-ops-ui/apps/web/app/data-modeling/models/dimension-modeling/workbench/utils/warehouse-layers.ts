/** Warehouse layers are records, not enum values: the selected option always stores its ID. */

export type WarehouseLayerRecord = {
  id?: string;
  enShortName?: unknown;
  enName?: unknown;
  cnName?: unknown;
  dwType?: unknown;
  dwModelType?: unknown;
};

export type WarehouseLayerOption = {
  value: string;
  label: string;
  dwType: string;
  shortName: string;
};

const LAYER_NAMES: Record<string, string> = {
  SOURCE_LAYER: "贴源层",
  PUBLIC_LAYER: "公共层",
  APPLICATION_LAYER: "应用层",
  NONE_LAYER: "未分层",
};

const MODEL_TABLE_TYPES: Record<string, string[]> = {
  SOURCE: ["SOURCE"],
  DIMENSION: ["DIMENSION"],
  DIM: ["DIM"],
  DIMENSION_DIM: ["DIMENSION", "DIM"],
  DETAIL: ["DETAIL"],
  SUMMARY: ["SUMMARY"],
  APPLICATION: ["APPLICATION"],
};

const LAYER_MODEL_TYPES: Record<string, string[]> = {
  SOURCE_LAYER: ["SOURCE"],
  PUBLIC_LAYER: ["DIMENSION", "DIM", "DIMENSION_DIM", "DETAIL", "SUMMARY"],
  APPLICATION_LAYER: ["DIMENSION", "DIM", "DIMENSION_DIM", "APPLICATION"],
};

const TABLE_LAYER_TYPES: Record<string, string[]> = {
  SOURCE: ["SOURCE_LAYER"],
  DIM: ["PUBLIC_LAYER"],
  DIMENSION: ["PUBLIC_LAYER", "APPLICATION_LAYER"],
  DETAIL: ["PUBLIC_LAYER"],
  SUMMARY: ["PUBLIC_LAYER"],
  APPLICATION: ["APPLICATION_LAYER"],
};

const DEFAULT_SHORT_NAMES: Record<string, string> = {
  SOURCE: "ODS",
  DIM: "DIM",
  DIMENSION: "DIM",
  DETAIL: "DWD",
  SUMMARY: "DWS",
  APPLICATION: "ADS",
};

export function warehouseEnumCode(value: unknown): string {
  if (typeof value === "string") return value.trim().toUpperCase();
  if (!value || typeof value !== "object") return "";
  const item = value as Record<string, unknown>;
  const code = item.code ?? item.value ?? item.name;
  return typeof code === "string" ? code.trim().toUpperCase() : "";
}

function layerName(layer: WarehouseLayerRecord): string {
  for (const candidate of [layer.cnName, layer.enShortName, layer.enName]) {
    if (typeof candidate === "string" && candidate.trim()) return candidate.trim();
  }
  return String(layer.id || "未命名分层");
}

export function warehouseLayerOptions(
  layers: readonly WarehouseLayerRecord[],
  tableType: string,
  currentId = "",
): WarehouseLayerOption[] {
  return layers
    .filter((layer) => {
      const id = String(layer.id || "");
      if (!id) return false;
      // Keep the existing binding visible for edits, including legacy NONE_LAYER rows.
      if (currentId && id === currentId) return true;
      const layerType = warehouseEnumCode(layer.dwType);
      const modelType = warehouseEnumCode(layer.dwModelType);
      if (layerType === "NONE_LAYER") return false;
      if (!(LAYER_MODEL_TYPES[layerType] || []).includes(modelType)) return false;
      if (!tableType) return true;
      return (MODEL_TABLE_TYPES[modelType] || []).includes(tableType)
        && (TABLE_LAYER_TYPES[tableType] || []).includes(layerType);
    })
    .map((layer) => {
      const dwType = warehouseEnumCode(layer.dwType);
      const shortName = String(layer.enShortName || "").trim().toUpperCase();
      const name = layerName(layer);
      return {
        value: String(layer.id),
        label: LAYER_NAMES[dwType] ? LAYER_NAMES[dwType] + " · " + name : name,
        dwType,
        shortName,
      };
    });
}

export function preferredWarehouseLayer(
  options: readonly WarehouseLayerOption[],
  tableType: string,
  preferredDwType: string,
): WarehouseLayerOption | undefined {
  const matchingType = options.filter((option) => option.dwType === preferredDwType);
  return matchingType.find((option) => option.shortName === DEFAULT_SHORT_NAMES[tableType])
    || matchingType[0]
    || options[0];
}
