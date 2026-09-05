import { useIntl } from '@umijs/max';
import { Input, Modal, TreeSelect, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import type { DevelopmentDirectory, DevelopmentId } from '../types';

interface CreateDirectoryModalProps {
  open: boolean;
  directories: DevelopmentDirectory[];
  defaultParentId?: DevelopmentId;
  loading?: boolean;
  onCancel: () => void;
  onSubmit: (parentId: DevelopmentId | undefined, name: string) => void;
}

interface DirectoryTreeOption {
  title: string;
  pathLabel: string;
  value: string;
  key: string;
  searchText: string;
  children?: DirectoryTreeOption[];
}

const ROOT_VALUE = '__root__';

const CreateDirectoryModal = ({
  open,
  directories,
  defaultParentId,
  loading = false,
  onCancel,
  onSubmit,
}: CreateDirectoryModalProps) => {
  const intl = useIntl();
  const [parentId, setParentId] = useState<DevelopmentId>();
  const [name, setName] = useState('');

  const pathTreeData = useMemo<DirectoryTreeOption[]>(() => {
    const childrenOf = (parent?: DevelopmentId): DirectoryTreeOption[] =>
      directories
        .filter((directory) => (directory.parentId || undefined) === parent)
        .sort((left, right) => left.name.localeCompare(right.name, intl.locale))
        .map((directory) => ({
          title: directory.name,
          pathLabel: directory.path,
          value: directory.id,
          key: directory.id,
          searchText: `${directory.name} ${directory.path}`,
          children: childrenOf(directory.id),
        }));

    return [
      {
        title: '/',
        pathLabel: '/',
        value: ROOT_VALUE,
        key: ROOT_VALUE,
        searchText: '/',
        children: childrenOf(),
      },
    ];
  }, [directories, intl.locale]);

  useEffect(() => {
    if (!open) return;
    setParentId(defaultParentId);
    setName('');
  }, [defaultParentId, open]);

  const normalizedName = name.trim();

  const submit = () => {
    if (!normalizedName || loading) return;
    onSubmit(parentId, normalizedName);
  };

  return (
    <Modal
      open={open}
      title={intl.formatMessage({ id: 'pages.dataDevelopment.modal.directory.title' })}
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
          {intl.formatMessage({ id: 'pages.dataDevelopment.modal.node.path' })}
        </Typography.Text>
        <TreeSelect
          value={parentId ?? ROOT_VALUE}
          treeData={pathTreeData}
          treeDefaultExpandAll
          treeLine
          treeNodeLabelProp="pathLabel"
          showSearch
          treeNodeFilterProp="searchText"
          popupMatchSelectWidth
          className="w-full"
          disabled={loading}
          placeholder={intl.formatMessage({ id: 'pages.dataDevelopment.modal.directory.parentPlaceholder' })}
          onChange={(value) => {
            const selected = String(value);
            setParentId(selected === ROOT_VALUE ? undefined : selected);
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

export default CreateDirectoryModal;
