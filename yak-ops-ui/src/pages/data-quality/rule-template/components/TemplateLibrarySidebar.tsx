import { useIntl } from '@umijs/max';
import { Button, Dropdown, Input, Spin } from 'antd';
import {
  ChevronLeft,
  ChevronRight,
  Folder,
  FolderPlus,
  Pencil,
  RefreshCw,
  Search,
  Trash2,
} from 'lucide-react';

import { formatQualityDimension } from '../../i18n';
import type { useQualityTemplateLibrary } from '../hooks/useQualityTemplateLibrary';

type TemplateLibraryModel = ReturnType<typeof useQualityTemplateLibrary>;

interface TemplateLibrarySidebarProps {
  library: TemplateLibraryModel;
}

export default function TemplateLibrarySidebar({
  library,
}: TemplateLibrarySidebarProps) {
  const intl = useIntl();
  const {
    catalogMeta,
    dimension,
    setDimension,
    selectedFolder,
    setSelectedFolder,
    setActiveTab,
    folderKeyword,
    setFolderKeyword,
    folderLoading,
    leftWidth,
    collapsed,
    setCollapsed,
    dimensions,
    visibleFolders,
    selectedFolderId,
    startResize,
    loadFolders,
    openCreateFolder,
    openEditFolder,
    removeFolder,
  } = library;

  return (
    <>
      <aside
        className="shrink-0 overflow-hidden transition-[width] duration-200"
        style={{ width: collapsed ? 0 : leftWidth }}
      >
        <div
          className="h-full overflow-y-auto px-4 py-3"
          style={{ width: leftWidth }}
        >
          <div className="mb-2 text-xs font-semibold text-[#161823]">
            {intl.formatMessage({ id: 'pages.dataQuality.template.dimensionTitle' })}
          </div>
          <div className="space-y-1">
            {dimensions.map((item) => {
              const selected = dimension === item.label;
              return (
                <button
                  key={item.label}
                  type="button"
                  onClick={() => setDimension(item.label)}
                  className={`flex h-8 w-full items-center justify-between border-0 px-2 text-left text-[13px] transition-colors ${
                    selected
                      ? 'bg-[rgba(254,44,85,.08)] font-medium text-[var(--yak-brand-color)]'
                      : 'bg-transparent text-[#30323b] hover:bg-[#f5f5f6]'
                  }`}
                >
                  <span className="truncate">
                    {formatQualityDimension(intl, item.label)}
                  </span>
                  <span
                    className={`ml-3 min-w-7 rounded-full px-2 text-center text-xs leading-5 ${
                      selected
                        ? 'bg-white text-[var(--yak-brand-color)]'
                        : 'bg-[#f2f3f5] text-[#5d616b]'
                    }`}
                  >
                    {item.count}
                  </span>
                </button>
              );
            })}
          </div>

          <div className="mt-5 border-t border-[#eceef0] pt-4">
            <div className="mb-2 flex items-center justify-between">
              <div className="text-xs font-semibold text-[#161823]">
                {intl.formatMessage({
                  id: 'pages.dataQuality.template.customCategories',
                })}
              </div>
              <div className="flex items-center gap-0.5">
                <Button
                  type="text"
                  size="small"
                  icon={<FolderPlus size={14} />}
                  onClick={() => openCreateFolder(selectedFolderId)}
                />
                <Button
                  type="text"
                  size="small"
                  icon={<RefreshCw size={14} />}
                  loading={folderLoading}
                  onClick={() => void loadFolders()}
                />
              </div>
            </div>
            <Input
              allowClear
              variant="filled"
              size="small"
              value={folderKeyword}
              onChange={(event) => setFolderKeyword(event.target.value)}
              prefix={<Search size={13} className="text-[#98a2b3]" />}
              placeholder={intl.formatMessage({
                id: 'pages.dataQuality.template.searchCategory',
              })}
              className="mb-2"
            />
            <button
              type="button"
              onClick={() => {
                setSelectedFolder('ALL');
                setActiveTab('CUSTOM');
              }}
              className={`flex h-8 w-full items-center gap-2 border-0 px-2 text-left text-[13px] ${
                selectedFolder === 'ALL'
                  ? 'bg-[rgba(254,44,85,.08)] text-[var(--yak-brand-color)]'
                  : 'bg-transparent text-[#30323b] hover:bg-[#f5f5f6]'
              }`}
            >
              <Folder size={14} />
              <span className="flex-1">
                {intl.formatMessage({ id: 'pages.dataQuality.template.all' })}
              </span>
              <span className="text-xs text-[#8a8f99]">
                {catalogMeta.customTotal}
              </span>
            </button>
            <button
              type="button"
              onClick={() => {
                setSelectedFolder('ROOT');
                setActiveTab('CUSTOM');
              }}
              className={`flex h-8 w-full items-center gap-2 border-0 px-2 text-left text-[13px] ${
                selectedFolder === 'ROOT'
                  ? 'bg-[rgba(254,44,85,.08)] text-[var(--yak-brand-color)]'
                  : 'bg-transparent text-[#30323b] hover:bg-[#f5f5f6]'
              }`}
            >
              <Folder size={14} />
              <span className="flex-1">
                {intl.formatMessage({
                  id: 'pages.dataQuality.template.uncategorized',
                })}
              </span>
            </button>

            <Spin spinning={folderLoading} size="small">
              <div className="mt-0.5 space-y-0.5">
                {visibleFolders.map((folder) => (
                  <Dropdown
                    key={folder.id}
                    trigger={['contextMenu']}
                    menu={{
                      items: [
                        {
                          key: 'child',
                          icon: <FolderPlus size={14} />,
                          label: intl.formatMessage({
                            id: 'pages.dataQuality.template.newSubfolder',
                          }),
                          onClick: () => openCreateFolder(folder.id),
                        },
                        {
                          key: 'edit',
                          icon: <Pencil size={14} />,
                          label: intl.formatMessage({
                            id: 'pages.dataQuality.template.renameMove',
                          }),
                          onClick: () => openEditFolder(folder),
                        },
                        { type: 'divider' },
                        {
                          key: 'delete',
                          danger: true,
                          icon: <Trash2 size={14} />,
                          label: intl.formatMessage({
                            id: 'pages.dataQuality.template.deleteFolder',
                          }),
                          onClick: () => removeFolder(folder),
                        },
                      ],
                    }}
                  >
                    <button
                      type="button"
                      onClick={() => {
                        setSelectedFolder(folder.id);
                        setActiveTab('CUSTOM');
                      }}
                      className={`flex h-8 w-full items-center gap-2 border-0 pr-2 text-left text-[13px] ${
                        selectedFolder === folder.id
                          ? 'bg-[rgba(254,44,85,.08)] text-[var(--yak-brand-color)]'
                          : 'bg-transparent text-[#30323b] hover:bg-[#f5f5f6]'
                      }`}
                      style={{ paddingLeft: 8 + folder.depth * 16 }}
                    >
                      <Folder size={14} />
                      <span className="min-w-0 flex-1 truncate">
                        {folder.name}
                      </span>
                      <span className="text-xs text-[#8a8f99]">
                        {folder.templateCount}
                      </span>
                    </button>
                  </Dropdown>
                ))}
              </div>
            </Spin>
            <div className="mt-2 text-[11px] leading-5 text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.dataQuality.template.folderHint' })}
            </div>
          </div>
        </div>
      </aside>

      <div
        role="separator"
        onPointerDown={startResize}
        className="relative w-3 shrink-0 cursor-col-resize"
      >
        <div className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-[#e4e7ec]" />
        <button
          type="button"
          onPointerDown={(event) => event.stopPropagation()}
          onClick={() => setCollapsed((value) => !value)}
          className="absolute left-1/2 top-1/2 z-10 flex h-8 w-4 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded border border-[#dfe1e5] bg-white text-[#7b808a]"
        >
          {collapsed ? <ChevronRight size={13} /> : <ChevronLeft size={13} />}
        </button>
      </div>
    </>
  );
}
