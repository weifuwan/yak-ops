import {
  Button,
  Drawer,
  DrawerBody,
  DrawerContent,
  DrawerDescription,
  DrawerTitle,
  toast,
} from "@yak-ops/yak-ui";
import { X } from "lucide-react";
import {
  forwardRef,
  useImperativeHandle,
  useRef,
  useState,
} from "react";

import {
  createDataSource,
  testDataSourceConnectionWithParams,
  updateDataSource,
} from "@/service/datasource";
import { useIntl } from "../i18n";
import { getDataSourceGroupList } from "../model/constants";
import DatabaseIcons from "../model/icons/DatabaseIcons";
import type { DataSourceRecord } from "../model/types";
import {
  DataSourceOperateType,
  type DataSourceFormValues,
  type DataSourceModalOpenPayload,
  type DataSourceModalRef,
} from "./types";
import {
  buildSubmitPayload,
  normalizeConnectionFormValues,
  parseOriginalJson,
} from "./formModel";
import { useDataSourceForm } from "./formRuntime";
import DataSourceTypeSelector from "./DataSourceTypeSelector";
import DynamicDataSourceForm from "./DynamicDataSourceForm";

const DRAWER_WIDTH = 620;

const DataSourceEditor = forwardRef<DataSourceModalRef>((_, ref) => {
  const intl = useIntl();
  const basicForm = useDataSourceForm<DataSourceFormValues>();
  const configForm = useDataSourceForm<Record<string, unknown>>();
  const [open, setOpen] = useState(false);
  const [operateType, setOperateType] = useState(DataSourceOperateType.Create);
  const [currentRecord, setCurrentRecord] = useState<DataSourceRecord>();
  const [selectedDbType, setSelectedDbType] = useState("");
  const [showFormStep, setShowFormStep] = useState(false);
  const [hideBackButton, setHideBackButton] = useState(false);
  const [testing, setTesting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const successCallbackRef = useRef<(() => void) | undefined>();

  const isCreateMode = operateType === DataSourceOperateType.Create;
  const isEditMode = operateType === DataSourceOperateType.Edit;
  const busy = testing || submitting;
  const dataSourceGroups = getDataSourceGroupList(intl);

  const resetEditorState = () => {
    setCurrentRecord(undefined);
    setSelectedDbType("");
    setShowFormStep(false);
    setHideBackButton(false);
    setTesting(false);
    setSubmitting(false);
    successCallbackRef.current = undefined;
    basicForm.resetFields();
    configForm.resetFields();
  };

  const handleClose = () => {
    if (!busy) setOpen(false);
  };

  const initializeEditForm = (record: DataSourceRecord) => {
    basicForm.setFieldsValue({
      name: record.name || "",
      environment: record.environment || "",
      remark: record.remark || "",
    });
  };

  useImperativeHandle(ref, () => ({
    open: ({
      operateType: nextOperateType,
      currentRecord: nextRecord,
      onSuccess,
      dbType,
      hideBack,
    }: DataSourceModalOpenPayload) => {
      resetEditorState();
      successCallbackRef.current = onSuccess;
      setOperateType(nextOperateType);
      setCurrentRecord(nextRecord);

      if (nextOperateType === DataSourceOperateType.Edit && nextRecord) {
        setSelectedDbType(nextRecord.dbType || "");
        setShowFormStep(true);
        setHideBackButton(true);
        initializeEditForm(nextRecord);
      } else if (nextOperateType === DataSourceOperateType.Create && dbType) {
        setSelectedDbType(dbType);
        setShowFormStep(true);
        setHideBackButton(Boolean(hideBack));
      }

      setOpen(true);
    },
    close: handleClose,
  }));

  const handleSelectDbType = (dbType: string) => {
    basicForm.resetFields();
    configForm.resetFields();
    setSelectedDbType(dbType);
    setShowFormStep(true);
    setHideBackButton(false);
  };

  const handleBackToTypeSelection = () => {
    if (busy) return;
    setShowFormStep(false);
    setSelectedDbType("");
    setHideBackButton(false);
    basicForm.resetFields();
    configForm.resetFields();
  };

  const handleTestConnection = async () => {
    if (testing || submitting) return;

    try {
      setTesting(true);
      const connectionValues = normalizeConnectionFormValues(
        await configForm.validateFields(),
      );
      const connected = await testDataSourceConnectionWithParams({
        dataSourceId: isEditMode ? currentRecord?.id : undefined,
        dbType: selectedDbType,
        connJson: JSON.stringify({
          ...connectionValues,
          dbType: selectedDbType,
        }),
      });

      if (connected) {
        toast.success(
          intl.formatMessage({ id: "pages.datasource.test.success" }),
        );
      }
    } catch (error) {
      if (error && typeof error === "object" && "errorFields" in error) return;
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async () => {
    if (submitting || testing) return;

    try {
      setSubmitting(true);
      const basicValues = await basicForm.validateFields();
      const connectionValues = await configForm.validateFields();
      const payload = buildSubmitPayload(
        selectedDbType,
        basicValues,
        connectionValues,
      );

      if (isCreateMode) {
        await createDataSource(payload);
      } else if (currentRecord?.id !== undefined && currentRecord.id !== null) {
        await updateDataSource(currentRecord.id, payload);
      } else {
        return;
      }

      const successCallback = successCallbackRef.current;
      toast.success(
        intl.formatMessage({
          id: isCreateMode
            ? "pages.datasource.modal.message.createSuccess"
            : "pages.datasource.modal.message.updateSuccess",
        }),
      );
      setOpen(false);
      successCallback?.();
    } catch (error) {
      if (error && typeof error === "object" && "errorFields" in error) return;
    } finally {
      setSubmitting(false);
    }
  };

  const drawerTitle = intl.formatMessage({
    id: isEditMode
      ? "pages.datasource.modal.drawerTitle.edit"
      : "pages.datasource.modal.drawerTitle.add",
  });

  const footer = showFormStep ? (
    <div className="flex items-center justify-between gap-3">
      <div>
        {isCreateMode && !hideBackButton ? (
          <Button disabled={busy} onClick={handleBackToTypeSelection}>
            {intl.formatMessage({ id: "pages.datasource.modal.button.lastStep" })}
          </Button>
        ) : (
          <Button disabled={busy} onClick={handleClose}>
            {intl.formatMessage({ id: "pages.datasource.modal.button.cancel" })}
          </Button>
        )}
      </div>

      <div className="flex items-center gap-2">
        <Button
          loading={testing}
          disabled={submitting}
          onClick={() => void handleTestConnection()}
        >
          {intl.formatMessage({ id: "pages.datasource.modal.button.connTest" })}
        </Button>
        <Button
          variant="primary"
          loading={submitting}
          disabled={testing}
          onClick={() => void handleSubmit()}
        >
          {intl.formatMessage({
            id: isCreateMode
              ? "pages.datasource.modal.button.create"
              : "pages.datasource.modal.button.save",
          })}
        </Button>
      </div>
    </div>
  ) : (
    <div className="flex justify-end">
      <Button variant="ghost" disabled={busy} onClick={handleClose}>
        {intl.formatMessage({ id: "pages.datasource.modal.button.cancel" })}
      </Button>
    </div>
  );

  return (
    <Drawer
      open={open}
      side="right"
      disablePointerDismissal={busy}
      onOpenChange={(nextOpen) => {
        if (!nextOpen && busy) return;
        setOpen(nextOpen);
      }}
      onOpenChangeComplete={(visible) => {
        if (!visible) resetEditorState();
      }}
    >
      <DrawerContent width={DRAWER_WIDTH} className="bg-white">
        <div className="flex items-center gap-3 border-b border-[#eef0f3] px-5 py-[15px]">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#eaecf0] bg-[#f7f8fa]">
            <DatabaseIcons dbType={selectedDbType} width="18" height="18" />
          </div>
          <div className="min-w-0 flex-1">
            <DrawerTitle className="truncate text-[15px] font-semibold leading-6 text-[#161823]">
              {drawerTitle}
            </DrawerTitle>
            <DrawerDescription className="sr-only">
              {drawerTitle}
            </DrawerDescription>
          </div>
          <Button
            variant="ghost"
            size="small"
            disabled={busy}
            aria-label={intl.formatMessage({ id: "pages.datasource.modal.button.cancel" })}
            className="h-8 w-8 p-0"
            onClick={handleClose}
          >
            <X size={16} />
          </Button>
        </div>

        <DrawerBody className="p-0">
          {showFormStep ? (
            <div className="h-full overflow-y-auto px-5 py-5">
              <DynamicDataSourceForm
                key={`${operateType}-${selectedDbType}-${currentRecord?.id ?? "create"}`}
                dbType={selectedDbType}
                form={basicForm}
                configForm={configForm}
                operateType={operateType}
                initialConfig={
                  isEditMode
                    ? parseOriginalJson(currentRecord?.originalJson)
                    : undefined
                }
              />
            </div>
          ) : (
            <div className="h-full min-h-0 px-5 py-4">
              <DataSourceTypeSelector
                dataSourceGroups={dataSourceGroups}
                onSelect={handleSelectDbType}
              />
            </div>
          )}
        </DrawerBody>

        <div className="border-t border-[#eef0f3] bg-white px-5 py-3">
          {footer}
        </div>
      </DrawerContent>
    </Drawer>
  );
});

DataSourceEditor.displayName = "DataSourceEditor";

export default DataSourceEditor;
