import { YakButton } from '@/components/ui';
import {
  createDataSource,
  testDataSourceConnectionWithParams,
  updateDataSource,
} from '@/services/data-source';
import { useIntl } from '@umijs/max';
import { Drawer, Form, message } from 'antd';
import { forwardRef, useImperativeHandle, useRef, useState } from 'react';

import { getDataSourceGroupList } from '../constants';
import DatabaseIcons from '../icon/DatabaseIcons';
import type {
  DataSourceFormValues,
  DataSourceModalOpenPayload,
  DataSourceModalRef,
  DataSourceRecord,
} from '../types';
import { DataSourceOperateType } from '../types';
import {
  buildSubmitPayload,
  normalizeConnectionFormValues,
  parseOriginalJson,
} from '../utils';
import DataSourceTypeSelector from './DataSourceTypeSelector';
import DynamicDataSourceForm from './DynamicDataSourceForm';
import './DataSourceEditorDrawer.less';

const DRAWER_WIDTH = 620;

const AddOrEditDataSourceModal = forwardRef<DataSourceModalRef>((_, ref) => {
  const intl = useIntl();
  const [basicForm] = Form.useForm<DataSourceFormValues>();
  const [configForm] = Form.useForm<Record<string, unknown>>();
  const [open, setOpen] = useState(false);
  const [operateType, setOperateType] = useState(DataSourceOperateType.Create);
  const [currentRecord, setCurrentRecord] = useState<DataSourceRecord>();
  const [selectedDbType, setSelectedDbType] = useState('');
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
    setSelectedDbType('');
    setShowFormStep(false);
    setHideBackButton(false);
    setTesting(false);
    setSubmitting(false);
    successCallbackRef.current = undefined;
    basicForm.resetFields();
    configForm.resetFields();
  };

  const handleClose = () => {
    if (busy) return;
    setOpen(false);
  };

  const handleAfterOpenChange = (visible: boolean) => {
    if (!visible) resetEditorState();
  };

  const initializeEditForm = (record: DataSourceRecord) => {
    basicForm.setFieldsValue({
      name: record.name || '',
      environment: record.environment || '',
      remark: record.remark || '',
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
        setSelectedDbType(nextRecord.dbType || '');
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
    setSelectedDbType('');
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
        message.success(
          intl.formatMessage({ id: 'pages.datasource.test.success' }),
        );
      }
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
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
      message.success(
        intl.formatMessage({
          id: isCreateMode
            ? 'pages.datasource.modal.message.createSuccess'
            : 'pages.datasource.modal.message.updateSuccess',
        }),
      );
      setOpen(false);
      successCallback?.();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
    } finally {
      setSubmitting(false);
    }
  };

  const drawerTitle = intl.formatMessage({
    id: isEditMode
      ? 'pages.datasource.modal.drawerTitle.edit'
      : 'pages.datasource.modal.drawerTitle.add',
  });

  const renderFooter = () => {
    if (!showFormStep) {
      return (
        <div className="flex justify-end">
          <YakButton type="text" disabled={busy} onClick={handleClose}>
            {intl.formatMessage({ id: 'pages.datasource.modal.button.cancel' })}
          </YakButton>
        </div>
      );
    }

    return (
      <div className="flex items-center justify-between gap-3">
        <div>
          {isCreateMode && !hideBackButton ? (
            <YakButton disabled={busy} onClick={handleBackToTypeSelection}>
              {intl.formatMessage({ id: 'pages.datasource.modal.button.lastStep' })}
            </YakButton>
          ) : (
            <YakButton disabled={busy} onClick={handleClose}>
              {intl.formatMessage({ id: 'pages.datasource.modal.button.cancel' })}
            </YakButton>
          )}
        </div>

        <div className="flex items-center gap-2">
          <YakButton
            loading={testing}
            disabled={submitting}
            onClick={() => void handleTestConnection()}
          >
            {intl.formatMessage({ id: 'pages.datasource.modal.button.connTest' })}
          </YakButton>

          <YakButton
            type="primary"
            loading={submitting}
            disabled={testing}
            onClick={() => void handleSubmit()}
          >
            {intl.formatMessage({
              id: isCreateMode
                ? 'pages.datasource.modal.button.create'
                : 'pages.datasource.modal.button.save',
            })}
          </YakButton>
        </div>
      </div>
    );
  };

  return (
    <Drawer
      className="datasource-editor-drawer"
      width={DRAWER_WIDTH}
      placement="right"
      open={open}
      maskClosable={false}
      closable={!busy}
      keyboard={!busy}
      onClose={handleClose}
      afterOpenChange={handleAfterOpenChange}
      destroyOnClose
      styles={{
        header: {
          padding: '15px 20px',
          borderBottom: '1px solid #EEF0F3',
        },
        body: {
          padding: 0,
          overflow: 'hidden',
          background: '#FFFFFF',
        },
        footer: {
          padding: '12px 20px',
          borderTop: '1px solid #EEF0F3',
          background: '#FFFFFF',
        },
      }}
      title={
        <div className="flex min-w-0 items-center gap-3 pr-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#EAECF0] bg-[#F7F8FA]">
            <DatabaseIcons dbType={selectedDbType} width="18" height="18" />
          </div>
          <div className="min-w-0">
            <div className="truncate text-[15px] font-semibold leading-6 text-[#161823]">
              {drawerTitle}
            </div>
          </div>
        </div>
      }
      footer={renderFooter()}
    >
      {showFormStep ? (
        <div className="datasource-editor-drawer__body datasource-editor-drawer__form h-full overflow-y-auto px-5 py-5">
          <DynamicDataSourceForm
            key={`${operateType}-${selectedDbType}-${currentRecord?.id ?? 'create'}`}
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
        <div className="datasource-editor-drawer__body h-full min-h-0 px-5 py-4">
          <DataSourceTypeSelector
            dataSourceGroups={dataSourceGroups}
            onSelect={handleSelectDbType}
          />
        </div>
      )}
    </Drawer>
  );
});

AddOrEditDataSourceModal.displayName = 'AddOrEditDataSourceModal';

export default AddOrEditDataSourceModal;
