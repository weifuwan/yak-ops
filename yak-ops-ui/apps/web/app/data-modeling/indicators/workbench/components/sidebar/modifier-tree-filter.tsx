import { useState, type KeyboardEvent } from "react";
import {
  Database,
  Folder,
  Globe,
  Layers,
} from "lucide-react";
import {
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectSearch,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

type ModifierDwLayer = "PUBLIC_LAYER" | "APPLICATION_LAYER" | "NONE_LAYER";
type ModifierScopeType = "DATA_DOMAIN" | "BUSINESS_TYPE" | "SUBJECT_DOMAIN" | "DATA_MART";

type FilterOption = {
  value: string;
  label: string;
};

const DW_LAYER_OPTIONS: Array<{ value: ModifierDwLayer; label: string }> = [
  { value: "PUBLIC_LAYER", label: "公共层" },
  { value: "APPLICATION_LAYER", label: "应用层" },
  { value: "NONE_LAYER", label: "未分层" },
];

const SCOPE_OPTIONS: Array<{ value: ModifierScopeType; label: string }> = [
  { value: "DATA_DOMAIN", label: "数据域" },
  { value: "BUSINESS_TYPE", label: "业务分类" },
  { value: "SUBJECT_DOMAIN", label: "主题域" },
  { value: "DATA_MART", label: "数据集市" },
];

const SCOPE_FILTER_OPTIONS: Record<ModifierScopeType, FilterOption[]> = {
  DATA_DOMAIN: [
    { value: "DEFAULT", label: "默认" },
    { value: "CUSTOMER_SERVICE", label: "客服域" },
    { value: "LOGISTICS", label: "物流域" },
    { value: "TOOLS_AND_SERVICES", label: "工具和服务域" },
    { value: "COMMON", label: "公共域" },
    { value: "CONSUMER", label: "消费者域" },
    { value: "MERCHANT", label: "商家域" },
    { value: "PRODUCT", label: "商品域" },
    { value: "TRAFFIC", label: "流量域" },
    { value: "MARKETING", label: "营销域" },
    { value: "TRADE", label: "交易域" },
  ],
  BUSINESS_TYPE: [
    { value: "DEFAULT", label: "默认" },
    { value: "CUSTOMER_OPERATION", label: "客户运营" },
    { value: "PRODUCT_MANAGEMENT", label: "商品管理" },
    { value: "ORDER_MANAGEMENT", label: "订单管理" },
    { value: "MARKETING_OPERATION", label: "营销运营" },
  ],
  SUBJECT_DOMAIN: [
    { value: "DEFAULT", label: "默认" },
    { value: "CUSTOMER_SUBJECT", label: "客户主题" },
    { value: "PRODUCT_SUBJECT", label: "商品主题" },
    { value: "TRADE_SUBJECT", label: "交易主题" },
    { value: "MARKETING_SUBJECT", label: "营销主题" },
  ],
  DATA_MART: [
    { value: "DEFAULT", label: "默认" },
    { value: "OPERATION_MART", label: "经营分析集市" },
    { value: "CUSTOMER_MART", label: "客户分析集市" },
    { value: "PRODUCT_MART", label: "商品分析集市" },
  ],
};

function ScopeIcon({ scope }: { scope: ModifierScopeType }) {
  const className = "size-[15px] shrink-0 text-[#3a3d51]";

  if (scope === "BUSINESS_TYPE") return <Layers className={className} />;
  if (scope === "SUBJECT_DOMAIN") return <Folder className={className} />;
  if (scope === "DATA_MART") return <Database className={className} />;
  return <Globe className={className} />;
}

type ModifierTreeFilterProps = {
  keyword: string;
  onKeywordChange: (value: string) => void;
};

export function ModifierTreeFilter({
  keyword,
  onKeywordChange,
}: ModifierTreeFilterProps) {
  const [dwLayer, setDwLayer] = useState<ModifierDwLayer>("PUBLIC_LAYER");
  const [scopeType, setScopeType] = useState<ModifierScopeType>("DATA_DOMAIN");
  const [scopeValue, setScopeValue] = useState("");
  const [scopeKeyword, setScopeKeyword] = useState("");

  const activeScope = SCOPE_OPTIONS.find((option) => option.value === scopeType) ?? SCOPE_OPTIONS[0];
  const scopeOptions = SCOPE_FILTER_OPTIONS[scopeType];
  const visibleScopeOptions = scopeOptions.filter((option) =>
    option.label.toLocaleLowerCase().includes(scopeKeyword.trim().toLocaleLowerCase()),
  );

  const handleLayerKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;

    event.preventDefault();
    const direction = event.key === "ArrowRight" ? 1 : -1;
    const nextIndex = (index + direction + DW_LAYER_OPTIONS.length) % DW_LAYER_OPTIONS.length;
    const nextOption = DW_LAYER_OPTIONS[nextIndex];
    if (!nextOption) return;

    setDwLayer(nextOption.value);
    event.currentTarget.parentElement
      ?.querySelectorAll<HTMLButtonElement>('[role="radio"]')[nextIndex]
      ?.focus();
  };

  return (
    <div className="flex w-full shrink-0 flex-col gap-2 px-2.5 pb-2">
      <div
        role="radiogroup"
        aria-label="数仓分层"
        className="flex h-6 w-full overflow-hidden rounded-[4px] border border-[#d7dce5] bg-white"
      >
        {DW_LAYER_OPTIONS.map((option, index) => {
          const checked = dwLayer === option.value;

          return (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={checked}
              tabIndex={checked ? 0 : -1}
              onClick={() => setDwLayer(option.value)}
              onKeyDown={(event) => handleLayerKeyDown(event, index)}
              className={[
                "relative flex h-full min-w-0 flex-1 items-center justify-center px-1 text-[11px] leading-none transition-colors",
                index > 0 ? "border-l border-[#d7dce5]" : "",
                checked
                  ? "z-[1] bg-[#f4f7ff] font-medium text-[#4169df] shadow-[inset_0_0_0_1px_#cbd7ff]"
                  : "text-[#606875] hover:bg-[#f8f9fc]",
              ].join(" ")}
            >
              <span className="truncate">{option.label}</span>
            </button>
          );
        })}
      </div>

      <div className="flex h-6 w-full min-w-0">
        <Select
          size="small"
          value={scopeType}
          onValueChange={(value) => {
            setScopeType(String(value || "DATA_DOMAIN") as ModifierScopeType);
            setScopeValue("");
            setScopeKeyword("");
          }}
        >
          <SelectTrigger
            aria-label="选择目录类型"
            variant="outlined"
            className="!h-6 !w-[60px] !min-w-[60px] !shrink-0 !rounded-r-none !px-2 [&>span:last-child>svg]:!size-3"
          >
            <SelectValue>
              {() => <ScopeIcon scope={scopeType} />}
            </SelectValue>
          </SelectTrigger>
          <SelectContent className="!min-w-[148px]">
            {SCOPE_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value} className="!min-h-7 !px-2">
                <ScopeIcon scope={option.value} />
                <SelectItemText>{option.label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          size="small"
          value={scopeValue || undefined}
          onValueChange={(value) => setScopeValue(String(value || ""))}
        >
          <SelectTrigger
            aria-label={`选择${activeScope.label}`}
            variant="outlined"
            className="!h-6 !min-w-0 !flex-1 !rounded-l-none !border-l-0 !px-2 [&>span:last-child>svg]:!size-3"
          >
            <SelectValue placeholder={`请选择${activeScope.label}`} />
          </SelectTrigger>
          <SelectContent
            header={
              <SelectSearch
                size="small"
                value={scopeKeyword}
                placeholder={`搜索${activeScope.label}`}
                onChange={(event) => setScopeKeyword(event.target.value)}
              />
            }
            emptyContent={visibleScopeOptions.length === 0 ? `没有匹配的${activeScope.label}` : undefined}
          >
            {visibleScopeOptions.map((option) => (
              <SelectItem key={option.value} value={option.value} className="!min-h-7 !px-2">
                <SelectItemText>{option.label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Input
        size="small"
        variant="outlined"
        value={keyword}
        placeholder="搜索"
        className="!h-6 !rounded-[4px] !px-2 text-[11px]"
        onChange={(event) => onKeywordChange(event.target.value)}
      />
    </div>
  );
}
