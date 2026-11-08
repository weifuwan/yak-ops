import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { COMMON_DB_OPTIONS } from "../constants";
import { useIntl } from "../i18n";

interface Props {
  keyword: string;
  dbType?: string;
  onCreate: () => void;
  onKeywordChange: (value: string) => void;
  onDbTypeChange: (value?: string) => void;
}

export function DataSourceFilters({
  keyword,
  dbType,
  onCreate,
  onKeywordChange,
  onDbTypeChange,
}: Props) {
  const intl = useIntl();
  const dbTypeItems = [
    {
      label: intl.formatMessage({ id: "pages.datasource.toolbar.allTypes" }),
      value: "ALL",
    },
    ...COMMON_DB_OPTIONS.map(({ label, value }) => ({ label, value })),
  ];

  return (
    <section className="flex shrink-0 flex-wrap items-center gap-2">
      <Button size="sm" variant="primary" onClick={onCreate}>
        <Plus size={14} />
        {intl.formatMessage({ id: "pages.datasource.page.create" })}
      </Button>

      <div className="w-[290px]">
        <Select
          size="small"
          items={dbTypeItems}
          value={dbType || "ALL"}
          onValueChange={(value) => onDbTypeChange(value && value !== "ALL" ? value : undefined)}
        >
          <SelectTrigger variant="outlined">
            <span className="mr-2 text-[var(--yak-components-muted-text)]">
              {intl.formatMessage({ id: "pages.datasource.toolbar.typeLabel" })}
            </span>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">
              <SelectItemText>
                {intl.formatMessage({ id: "pages.datasource.toolbar.allTypes" })}
              </SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
            {COMMON_DB_OPTIONS.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                <SelectItemText>{option.label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="relative w-[290px]">
        <span className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[length:var(--yak-font-size-control-small)] text-[var(--yak-components-muted-text)]">
          {intl.formatMessage({ id: "pages.datasource.toolbar.nameLabel" })}
        </span>
        <Input
          size="small"
          variant="outlined"
          value={keyword}
          className="pl-[92px]"
          placeholder={intl.formatMessage({
            id: "pages.datasource.toolbar.namePlaceholder",
          })}
          onChange={(event) => onKeywordChange(event.target.value)}
        />
      </div>
    </section>
  );
}
