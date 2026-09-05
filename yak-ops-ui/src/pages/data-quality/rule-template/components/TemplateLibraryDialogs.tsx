import { useIntl } from '@umijs/max';
import { Form, Input, Modal, Select } from 'antd';

import CustomTemplateDrawer from '../CustomTemplateDrawer';
import type { useQualityTemplateLibrary } from '../hooks/useQualityTemplateLibrary';
import { flattenTemplateFolders } from '../utils';

type TemplateLibraryModel = ReturnType<typeof useQualityTemplateLibrary>;

interface TemplateLibraryDialogsProps {
  library: TemplateLibraryModel;
}

export default function TemplateLibraryDialogs({
  library,
}: TemplateLibraryDialogsProps) {
  const intl = useIntl();
  const {
    folders,
    drawerOpen,
    setDrawerOpen,
    drawerMode,
    editingTemplate,
    submitting,
    folderDialogOpen,
    setFolderDialogOpen,
    folderDialogMode,
    editingFolder,
    copyTemplate,
    setCopyTemplate,
    folderForm,
    copyForm,
    selectedFolderId,
    saveFolder,
    saveTemplate,
    saveCopy,
  } = library;

  return (
    <>
      <CustomTemplateDrawer
        open={drawerOpen}
        mode={drawerMode}
        template={editingTemplate}
        folders={folders}
        defaultFolderId={selectedFolderId}
        submitting={submitting}
        onClose={() => setDrawerOpen(false)}
        onSubmit={saveTemplate}
      />

      <Modal
        title={intl.formatMessage({
          id:
            folderDialogMode === 'create'
              ? 'pages.dataQuality.template.dialog.createFolder'
              : 'pages.dataQuality.template.dialog.editFolder',
        })}
        open={folderDialogOpen}
        confirmLoading={submitting}
        onOk={() => void saveFolder()}
        onCancel={() => setFolderDialogOpen(false)}
        destroyOnClose
      >
        <Form form={folderForm} layout="vertical" className="pt-3">
          <Form.Item
            name="name"
            label={intl.formatMessage({
              id: 'pages.dataQuality.template.dialog.folderName',
            })}
            rules={[
              {
                required: true,
                whitespace: true,
                message: intl.formatMessage({
                  id: 'pages.dataQuality.template.dialog.folderNameRequired',
                }),
              },
            ]}
          >
            <Input
              variant="filled"
              maxLength={100}
              placeholder={intl.formatMessage({
                id: 'pages.dataQuality.template.dialog.folderNamePlaceholder',
              })}
            />
          </Form.Item>
          <Form.Item
            name="parentId"
            label={intl.formatMessage({
              id: 'pages.dataQuality.template.dialog.parentFolder',
            })}
          >
            <Select
              allowClear
              variant="filled"
              placeholder={intl.formatMessage({
                id: 'pages.dataQuality.template.dialog.root',
              })}
              options={flattenTemplateFolders(folders)
                .filter((folder) => folder.id !== editingFolder?.id)
                .map((folder) => ({
                  value: folder.id,
                  label: `${'　'.repeat(folder.depth)}${folder.name}`,
                }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={intl.formatMessage({
          id: 'pages.dataQuality.template.dialog.copyTitle',
        })}
        open={Boolean(copyTemplate)}
        confirmLoading={submitting}
        onOk={() => void saveCopy()}
        onCancel={() => setCopyTemplate(undefined)}
        destroyOnClose
      >
        <Form form={copyForm} layout="vertical" className="pt-3">
          <Form.Item
            name="name"
            label={intl.formatMessage({
              id: 'pages.dataQuality.template.dialog.templateName',
            })}
            rules={[
              {
                required: true,
                whitespace: true,
                message: intl.formatMessage({
                  id: 'pages.dataQuality.template.dialog.templateNameRequired',
                }),
              },
            ]}
          >
            <Input variant="filled" maxLength={100} />
          </Form.Item>
          <Form.Item
            name="folderId"
            label={intl.formatMessage({
              id: 'pages.dataQuality.template.dialog.targetFolder',
            })}
          >
            <Select
              allowClear
              variant="filled"
              placeholder={intl.formatMessage({
                id: 'pages.dataQuality.template.uncategorized',
              })}
              options={flattenTemplateFolders(folders).map((folder) => ({
                value: folder.id,
                label: `${'　'.repeat(folder.depth)}${folder.name}`,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
