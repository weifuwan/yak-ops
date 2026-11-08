import { Button, Modal } from "@yak-ops/yak-ui";
import { COMMON_DB_OPTIONS, normalizeDataSourceType } from "../constants";
import type { DataSourceFormProps } from "./model";
import { useDataSourceForm } from "./use-data-source-form";
import { DataSourceTypeSelector } from "./type-selector";
import { DataSourceFormFields } from "./form-fields";

export function DataSourceForm(props: DataSourceFormProps) {
  const { open, onOpenChange } = props;
  const form = useDataSourceForm(props);
  const {
    intl,
    values,
    editing,
    busy,
    testing,
    submitting,
    createStep,
    setCreateStep,
    handleTest,
    handleSubmit,
  } = form;
  const selectedType = COMMON_DB_OPTIONS.find(
    (option) => option.value === normalizeDataSourceType(values.dbType),
  );

  return (
    <Modal
      open={open}
      width={960}
      onClose={() => {
        if (!busy) onOpenChange(false);
      }}
      title={
        !editing && createStep === "select"
          ? intl.formatMessage({ id: "pages.datasource.wizard.title" })
          : intl.formatMessage(
              {
                id: editing
                  ? "pages.datasource.wizard.editConfigTitle"
                  : "pages.datasource.wizard.configTitle",
              },
              { type: selectedType?.label || values.dbType },
            )
      }
      bodyClassName={createStep === "config" ? "py-3" : undefined}
      footer={
        !editing && createStep === "select" ? (
          <Button variant="secondary" size="sm" isDisabled={busy} onClick={() => onOpenChange(false)}>
            {intl.formatMessage({ id: "pages.datasource.modal.button.cancel" })}
          </Button>
        ) : (
          <div className="flex w-full items-center justify-between">
            <Button variant="secondary"
              size="sm"
              isDisabled={busy}
              onClick={() => {
                if (editing) onOpenChange(false);
                else setCreateStep("select");
              }}
            >
              {intl.formatMessage({
                id: editing
                  ? "pages.datasource.modal.button.cancel"
                  : "pages.datasource.wizard.back",
              })}
            </Button>
            <div className="flex gap-2">
              <Button variant="secondary"
                size="sm"
                isPending={testing}
                isDisabled={submitting}
                onClick={() => void handleTest()}
              >
                {intl.formatMessage({ id: "pages.datasource.modal.button.connTest" })}
              </Button>
              <Button
                size="sm"
                variant="primary"
                isPending={submitting}
                isDisabled={testing}
                onClick={() => void handleSubmit()}
              >
                {intl.formatMessage({
                  id: editing
                    ? "pages.datasource.modal.button.save"
                    : "pages.datasource.wizard.complete",
                })}
              </Button>
            </div>
          </div>
        )
      }
    >
      {!editing && createStep === "select" ? (
        <DataSourceTypeSelector form={form} />
      ) : (
        <DataSourceFormFields form={form} />
      )}
    </Modal>
  );
}
