import {
  Button,
  Combobox,
  ComboboxContent,
  ComboboxInput,
  ComboboxItem,
  ComboboxItemIndicator,
  Field,
  FieldError,
  Input,
} from "@yak-ops/yak-ui";
import { normalizePropertyKey } from "./model";
import type { useDataSourceForm } from "./use-data-source-form";

export function AdvancedProperties({ form }: { form: ReturnType<typeof useDataSourceForm> }) {
  const {
    intl,
    values,
    errors,
    propertyKeyOptions,
    driverPropertyMetadata,
    propertyKeysLoading,
    propertyKeysError,
    propertyKeyMap,
    selectedSuggestedPropertyKeys,
    syncSuggestedProperties,
    patchProperty,
    addProperty,
    removeProperty,
  } = form;

  const driverPropertyMap = new Map(
    driverPropertyMetadata.map((property) => [normalizePropertyKey(property.name), property] as const),
  );

  const selectedSuggestedPropertyKeySet = new Set(
    selectedSuggestedPropertyKeys.map(normalizePropertyKey),
  );

  return (
    <div className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
      <span className="pt-1.5 text-xs font-medium text-[var(--yak-components-field-label)]">
        {intl.formatMessage({ id: "pages.datasource.form.advancedProperties" })}
      </span>
      <Field invalid={Boolean(errors.properties)} className="min-w-0 !gap-0">
        <div className="flex items-start gap-2">
          {propertyKeysLoading || propertyKeyOptions.length > 0 ? (
            <div className="min-w-0 flex-1">
              <Combobox<string, true>
              multiple
              size="small"
              items={propertyKeyOptions}
              value={selectedSuggestedPropertyKeys}
              disabled={propertyKeysLoading}
              onValueChange={syncSuggestedProperties}
              onOpenChange={(nextOpen, eventDetails) => {
                if (!nextOpen && eventDetails.reason === "item-press") eventDetails.cancel();
              }}
            >
              <ComboboxInput
                variant="outlined"
                aria-label={intl.formatMessage({ id: "pages.datasource.form.propertyKey" })}
                placeholder={intl.formatMessage({
                  id: propertyKeysLoading
                    ? "pages.datasource.form.propertyKeysLoading"
                    : "pages.datasource.form.propertySelectPlaceholder",
                })}
              />
              <ComboboxContent
                emptyText={intl.formatMessage({ id: "pages.datasource.form.propertySelectEmpty" })}
              >
                {(key: string) => (
                  <ComboboxItem key={key} value={key}>
                    <span
                      className="min-w-0 flex-1 truncate"
                      title={driverPropertyMap.get(normalizePropertyKey(key))?.description || key}
                    >
                      {key}
                    </span>
                    <ComboboxItemIndicator />
                  </ComboboxItem>
                )}
              </ComboboxContent>
              </Combobox>
            </div>
          ) : null}
          <Button variant="secondary" size="sm" onClick={addProperty}>
            {intl.formatMessage({ id: "pages.datasource.form.addCustomProperty" })}
          </Button>
        </div>

        {propertyKeysError ? (
          <p role="alert" className="mt-1 text-xs text-[var(--yak-color-danger)]">
            {intl.formatMessage({ id: "pages.datasource.form.propertyKeysLoadFailed" })}
          </p>
        ) : null}

        {values.properties.length > 0 ? (
          <div className="mt-2 overflow-hidden border border-[var(--yak-components-panel-border)]">
            <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_56px] bg-[var(--yak-color-hover)] text-xs font-medium text-[var(--yak-components-field-label)]">
              <div className="px-2.5 py-2">
                {intl.formatMessage({ id: "pages.datasource.form.propertyKey" })}
              </div>
              <div className="px-2.5 py-2">
                {intl.formatMessage({ id: "pages.datasource.form.propertyValue" })}
              </div>
              <div className="px-2.5 py-2 text-center">
                {intl.formatMessage({ id: "pages.datasource.table.actions" })}
              </div>
            </div>
            {values.properties.map((property, index) => {
              const normalizedKey = normalizePropertyKey(property.key);
              const suggestedKey = propertyKeyMap.get(normalizedKey);
              const driverProperty = driverPropertyMap.get(normalizedKey);
              const rowPropertyKeyOptions = propertyKeyOptions.filter(
                (key) =>
                  normalizePropertyKey(key) === normalizedKey ||
                  !selectedSuggestedPropertyKeySet.has(normalizePropertyKey(key)),
              );

              return (
                <div
                  key={"property-" + index}
                  className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_56px] items-start gap-2 border-t border-[var(--yak-components-panel-border)] p-2"
                >
                  {suggestedKey ? (
                    <Combobox<string>
                      size="small"
                      items={rowPropertyKeyOptions}
                      value={suggestedKey}
                      onValueChange={(key) => {
                        if (key) patchProperty(index, "key", key);
                      }}
                    >
                      <ComboboxInput
                        variant="outlined"
                        aria-label={intl.formatMessage({ id: "pages.datasource.form.propertyKey" })}
                        placeholder={intl.formatMessage({
                          id: "pages.datasource.form.propertyKeyPlaceholder",
                        })}
                      />
                      <ComboboxContent
                        emptyText={intl.formatMessage({
                          id: "pages.datasource.form.propertySelectEmpty",
                        })}
                      >
                        {(key: string) => (
                          <ComboboxItem key={key} value={key}>
                            <span
                      className="min-w-0 flex-1 truncate"
                      title={driverPropertyMap.get(normalizePropertyKey(key))?.description || key}
                    >
                      {key}
                    </span>
                            <ComboboxItemIndicator />
                          </ComboboxItem>
                        )}
                      </ComboboxContent>
                    </Combobox>
                  ) : (
                    <Input
                      size="small"
                      variant="outlined"
                      value={property.key}
                      placeholder={intl.formatMessage({
                        id: "pages.datasource.form.propertyKeyPlaceholder",
                      })}
                      onChange={(event) => patchProperty(index, "key", event.target.value)}
                    />
                  )}
                  <Input
                    size="small"
                    variant="outlined"
                    value={property.value}
                    title={driverProperty?.description || undefined}
                    placeholder={
                      driverProperty?.choices?.length
                        ? driverProperty.choices.join(" / ")
                        : intl.formatMessage({ id: "pages.datasource.form.propertyValuePlaceholder" })
                    }
                    onChange={(event) => patchProperty(index, "value", event.target.value)}
                  />
                  <Button
                    size="sm"
                    variant="ghost"
                    className="px-1 text-xs font-normal text-[var(--yak-color-primary)]"
                    onClick={() => removeProperty(index)}
                  >
                    {intl.formatMessage({ id: "pages.datasource.form.deleteProperty" })}
                  </Button>
                </div>
              );
            })}
          </div>
        ) : null}
        <FieldError match={Boolean(errors.properties)} className="mt-1">
          {errors.properties}
        </FieldError>
      </Field>
    </div>
  );
}
