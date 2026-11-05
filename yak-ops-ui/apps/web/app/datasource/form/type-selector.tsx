import { Input } from "@yak-ops/yak-ui";
import { Search } from "lucide-react";
import { COMMON_DB_OPTIONS } from "../constants";
import DatabaseIcons from "../icons/DatabaseIcons";
import { frequentDataSourceOptions } from "./model";
import type { useDataSourceForm } from "./use-data-source-form";

export function DataSourceTypeSelector({ form }: { form: ReturnType<typeof useDataSourceForm> }) {
  const {
    intl,
    createCategory,
    setCreateCategory,
    createSearch,
    setCreateSearch,
    frequentTypePreferences,
    handleSelectType,
  } = form;
  const normalizedSearch = createSearch.trim().toLowerCase();
  const categories = [
    { value: "RELATIONAL" as const, label: "pages.datasource.wizard.categoryRelational" },
    { value: "ANALYTICAL" as const, label: "pages.datasource.wizard.categoryAnalytical" },
    { value: "OTHER" as const, label: "pages.datasource.wizard.categoryOther" },
  ];
  const visibleOptions = COMMON_DB_OPTIONS.filter((option) => {
    const matchesCategory = createCategory === "ALL" || option.category === createCategory;
    const matchesSearch =
      !normalizedSearch ||
      option.label.toLowerCase().includes(normalizedSearch) ||
      option.value.toLowerCase().includes(normalizedSearch);
    return matchesCategory && matchesSearch;
  });
  const frequentOptions = frequentDataSourceOptions(frequentTypePreferences);

  return (
    <div className="flex h-[420px] flex-col">
      <section className="shrink-0">
        <div className="mb-2.5 text-[13px] font-medium text-[var(--yak-components-field-label)]">
          {intl.formatMessage({ id: "pages.datasource.wizard.commonTypes" })}
        </div>
        <div className="grid grid-cols-3 gap-2 max-sm:grid-cols-1">
          {frequentOptions.map((option) => (
            <button
              key={"frequent-" + option.value}
              type="button"
              className="flex h-10 cursor-pointer items-center gap-2.5 rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] px-3 text-left text-[13px] text-[var(--yak-components-field-label)] outline-none transition-[border-color,background-color] hover:border-[var(--yak-color-primary)] hover:bg-[var(--yak-color-hover)] focus-visible:border-[var(--yak-color-primary)]"
              onClick={() => handleSelectType(option.value)}
            >
              <DatabaseIcons dbType={option.value} width="18" height="18" />
              <span className="min-w-0 truncate">{option.label}</span>
            </button>
          ))}
        </div>
      </section>

      <section className="mt-4 shrink-0">
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className={
              createCategory === "ALL"
                ? "h-8 cursor-pointer rounded-[var(--yak-radius-control-small)] border border-[var(--yak-color-primary)] bg-[var(--yak-color-primary)] px-3 text-xs text-white outline-none"
                : "h-8 cursor-pointer rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-panel-border)] bg-white px-3 text-xs text-[var(--yak-components-muted-text)] outline-none hover:bg-[var(--yak-color-hover)] focus-visible:border-[var(--yak-color-primary)]"
            }
            onClick={() => setCreateCategory("ALL")}
          >
            {intl.formatMessage(
              { id: "pages.datasource.wizard.categoryAll" },
              { count: COMMON_DB_OPTIONS.length },
            )}
          </button>
          {categories.map((category) => (
            <button
              key={category.value}
              type="button"
              className={
                createCategory === category.value
                  ? "h-8 cursor-pointer rounded-[var(--yak-radius-control-small)] border border-[var(--yak-color-primary)] bg-[var(--yak-color-primary)] px-3 text-xs text-white outline-none"
                  : "h-8 cursor-pointer rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-panel-border)] bg-white px-3 text-xs text-[var(--yak-components-muted-text)] outline-none hover:bg-[var(--yak-color-hover)] focus-visible:border-[var(--yak-color-primary)]"
              }
              onClick={() => setCreateCategory(category.value)}
            >
              {intl.formatMessage(
                { id: category.label },
                { count: COMMON_DB_OPTIONS.filter((option) => option.category === category.value).length },
              )}
            </button>
          ))}
        </div>
      </section>

      <div className="relative mt-4 shrink-0">
        <Search
          aria-hidden="true"
          size={15}
          className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
        />
        <Input
          variant="outlined"
          value={createSearch}
          className="pl-9"
          placeholder={intl.formatMessage({
            id: "pages.datasource.wizard.searchPlaceholder",
          })}
          onChange={(event) => setCreateSearch(event.target.value)}
        />
      </div>

      <section className="mt-5 min-h-0 flex-1 overflow-y-auto">
        <div className="mb-2.5 text-[13px] font-medium text-[var(--yak-components-field-label)]">
          {intl.formatMessage({ id: "pages.datasource.wizard.datasourceList" })}
        </div>
        {visibleOptions.length > 0 ? (
          <div className="grid grid-cols-3 gap-2 max-sm:grid-cols-1">
            {visibleOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                className="flex h-10 cursor-pointer items-center gap-2.5 rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-panel-border)] bg-white px-3 text-left text-[13px] text-[var(--yak-components-field-label)] outline-none transition-[border-color,background-color] hover:border-[var(--yak-color-primary)] hover:bg-[var(--yak-color-hover)] focus-visible:border-[var(--yak-color-primary)]"
                onClick={() => handleSelectType(option.value)}
              >
                <DatabaseIcons dbType={option.value} width="18" height="18" />
                <span className="min-w-0 truncate">{option.label}</span>
              </button>
            ))}
          </div>
        ) : (
          <div className="py-10 text-center text-sm text-[var(--yak-components-muted-text)]">
            {intl.formatMessage({ id: "pages.datasource.wizard.empty" })}
          </div>
        )}
      </section>
    </div>
  );
}
