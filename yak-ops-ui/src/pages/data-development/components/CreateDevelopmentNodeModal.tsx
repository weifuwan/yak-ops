import { useIntl } from '@umijs/max';
import { Input, Modal, Select, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import type {
  DevelopmentDirectory,
  DevelopmentId,
  DevelopmentNodeType,
} from '../types';

interface CreateDevelopmentNodeModalProps {
  open: boolean;
  type: DevelopmentNodeType;
  directories: DevelopmentDirectory[];
  defaultDirectoryId?: DevelopmentId;
  loading?: boolean;
  onCancel: () => void;
  onNext: (
    type: DevelopmentNodeType,
    directoryId: DevelopmentId | undefined,
    name: string,
  ) => void;
}

const ROOT_VALUE = '__root__';

const CreateDevelopmentNodeModal = ({
  open,
  type: initialType,
  directories,
  defaultDirectoryId,
  loading = false,
  onCancel,
  onNext,
}: CreateDevelopmentNodeModalProps) => {
  const intl = useIntl();
  const [type, setType] = useState<DevelopmentNodeType>(initialType);
  const [directoryId, setDirectoryId] = useState<DevelopmentId>();
  const [name, setName] = useState('');

  const typeOptions = useMemo(
    () => [
      { label: 'SQL', value: 'SQL' },
      { label: 'Shell', value: 'SHELL' },
      { label: 'Python', value: 'PYTHON' },
      { label: 'Java', value: 'JAVA' },
      {
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.datasetNode' }).replace(/ Node$| 节点$/, ''),
        value: 'DATASET',
      },
      {
        label: intl.formatMessage({ id: 'pages.dataDevelopment.workspace.dataServiceNode' }).replace(/ Node$| 节点$/, ''),
        value: 'DATA_SERVICE',
      },
    ],
    [intl],
  );

  const pathOptions = useMemo(
    () => [
      { label: '/', value: ROOT_VALUE },
      ...directories.map((directory) => ({
        label: directory.path,
        value: directory.id,
      })),
    ],
    [directories],
  );

  useEffect(() => {
    if (!open) return;
    setType(initialType);
    setDirectoryId(defaultDirectoryId);
    setName('');
  }, [defaultDirectoryId, initialType, open]);

  const normalizedName = name.trim();

  const submit = () => {
    if (!normalizedName || loading) return;
    onNext(type, directoryId, normalizedName);
  };

  return (
    <Modal
      open={open}
      title={intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.title' })}
      width={600}
      okText={intl.formatMessage({ id: 'pages.dataDevelopment.common.confirm' })}
      cancelText={intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' })}
      confirmLoading={loading}
      okButtonProps={{ disabled: !normalizedName }}
      destroyOnClose
      maskClosable={!loading}
      closable={!loading}
      onCancel={onCancel}
      onOk={submit}
    >
      <div className="grid grid-cols-[88px_minmax(0,1fr)] items-center gap-y-3 pt-2">
        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          {intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.type' })}
        </Typography.Text>
        <Select
          value={type}
          options={typeOptions}
          className="w-full"
          disabled={loading}
          onChange={(value) => setType(value as DevelopmentNodeType)}
        />

        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          {intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.path' })}
        </Typography.Text>
        <Select
          value={directoryId ?? ROOT_VALUE}
          options={pathOptions}
          showSearch
          optionFilterProp="label"
          className="w-full"
          disabled={loading}
          onChange={(value) => {
            const selected = String(value);
            setDirectoryId(selected === ROOT_VALUE ? undefined : selected);
          }}
        />

        <Typography.Text className="text-[13px] text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          {intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.name' })}
        </Typography.Text>
        <Input
          autoFocus
          value={name}
          maxLength={128}
          disabled={loading}
          placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.namePlaceholder' })}
          onChange={(event) => setName(event.target.value)}
          onPressEnter={submit}
        />
      </div>
    </Modal>
  );
};

export default CreateDevelopmentNodeModal;
