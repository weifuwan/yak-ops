import {
  copyCustomQualityTemplate,
  createCustomQualityTemplate,
  createQualityTemplateFolder,
  deleteCustomQualityTemplate,
  deleteQualityTemplateFolder,
  getQualityTemplateCatalogSummary,
  listCustomQualityTemplates,
  listQualityTemplateFolders,
  listQualityTemplates,
  updateCustomQualityTemplate,
  updateQualityTemplateFolder,
  type CopyCustomTemplatePayload,
  type SaveCustomTemplatePayload,
  type SaveTemplateFolderPayload,
  type TemplateFolderView,
  type TemplateListView,
  type TemplateView,
} from '@/services/data-quality';
import { useIntl } from '@umijs/max';
import { Form, Modal, message } from 'antd';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
} from 'react';

import { flattenTemplateFolders } from '../utils';

const DEFAULT_LEFT_WIDTH = 280;
const MIN_LEFT_WIDTH = 230;
const MAX_LEFT_WIDTH = 460;

export type TemplateTabKey = 'SYSTEM' | 'CUSTOM';
type FolderSelection = 'ALL' | 'ROOT' | number;
type FolderDialogMode = 'create' | 'edit';

interface CatalogMeta {
  systemTotal: number;
  customTotal: number;
  systemDimensions: Record<string, number>;
  customDimensions: Record<string, number>;
}

const EMPTY_TEMPLATE_LIST: TemplateListView = {
  records: [],
  summary: {
    total: 0,
    systemTotal: 0,
    customTotal: 0,
    dimensions: {},
  },
};

const EMPTY_CATALOG_META: CatalogMeta = {
  systemTotal: 0,
  customTotal: 0,
  systemDimensions: {},
  customDimensions: {},
};

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

export const useQualityTemplateLibrary = () => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const [data, setData] = useState<TemplateListView>(EMPTY_TEMPLATE_LIST);
  const [folders, setFolders] = useState<TemplateFolderView[]>([]);
  const [catalogMeta, setCatalogMeta] =
    useState<CatalogMeta>(EMPTY_CATALOG_META);
  const [dimension, setDimension] = useState('全部');
  const [activeTab, setActiveTab] = useState<TemplateTabKey>('SYSTEM');
  const [selectedFolder, setSelectedFolder] =
    useState<FolderSelection>('ALL');
  const [keyword, setKeyword] = useState('');
  const [folderKeyword, setFolderKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [folderLoading, setFolderLoading] = useState(false);
  const [leftWidth, setLeftWidth] = useState(DEFAULT_LEFT_WIDTH);
  const [collapsed, setCollapsed] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<'create' | 'edit'>('create');
  const [editingTemplate, setEditingTemplate] = useState<TemplateView>();
  const [submitting, setSubmitting] = useState(false);
  const [folderDialogOpen, setFolderDialogOpen] = useState(false);
  const [folderDialogMode, setFolderDialogMode] =
    useState<FolderDialogMode>('create');
  const [editingFolder, setEditingFolder] = useState<TemplateFolderView>();
  const [copyTemplate, setCopyTemplate] = useState<TemplateView>();
  const [folderForm] = Form.useForm<SaveTemplateFolderPayload>();
  const [copyForm] = Form.useForm<CopyCustomTemplatePayload>();
  const dragRef = useRef<{ x: number; width: number } | null>(null);

  const loadFolders = useCallback(async () => {
    setFolderLoading(true);
    try {
      setFolders(await listQualityTemplateFolders());
    } catch (error) {
      message.error(
        errorMessage(
          error,
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.folderLoadFailed',
          }),
        ),
      );
    } finally {
      setFolderLoading(false);
    }
  }, []);

  const loadCatalogMeta = useCallback(async () => {
    try {
      setCatalogMeta(await getQualityTemplateCatalogSummary());
    } catch (error) {
      message.error(
        errorMessage(
          error,
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.statsLoadFailed',
          }),
        ),
      );
    }
  }, []);

  const loadTemplates = useCallback(async () => {
    setLoading(true);
    try {
      const folderId =
        activeTab !== 'CUSTOM' || selectedFolder === 'ALL'
          ? undefined
          : selectedFolder === 'ROOT'
            ? 0
            : selectedFolder;
      const value =
        activeTab === 'CUSTOM'
          ? await listCustomQualityTemplates({
              keyword: keyword.trim() || undefined,
              dimension: dimension === '全部' ? undefined : dimension,
              folderId,
            })
          : await listQualityTemplates({
              keyword: keyword.trim() || undefined,
              dimension: dimension === '全部' ? undefined : dimension,
            });
      setData({
        records:
          activeTab === 'SYSTEM'
            ? value.records.filter((template) => template.builtin)
            : value.records,
        summary: value.summary,
      });
    } catch (error) {
      message.error(
        errorMessage(
          error,
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.loadFailed',
          }),
        ),
      );
    } finally {
      setLoading(false);
    }
  }, [activeTab, dimension, keyword, selectedFolder]);

  useEffect(() => {
    void loadFolders();
  }, [loadFolders]);

  useEffect(() => {
    void loadCatalogMeta();
  }, [loadCatalogMeta]);

  useEffect(() => {
    void loadTemplates();
  }, [loadTemplates]);

  const dimensions = useMemo(() => {
    const source =
      activeTab === 'SYSTEM'
        ? catalogMeta.systemDimensions
        : catalogMeta.customDimensions;
    const total =
      activeTab === 'SYSTEM'
        ? catalogMeta.systemTotal
        : catalogMeta.customTotal;
    return [
      { label: '全部', count: total },
      ...Object.entries(source).map(([label, count]) => ({ label, count })),
    ];
  }, [activeTab, catalogMeta]);

  const visibleFolders = useMemo(() => {
    const flattened = flattenTemplateFolders(folders);
    const normalized = folderKeyword.trim().toLowerCase();
    return normalized
      ? flattened.filter((folder) =>
          folder.name.toLowerCase().includes(normalized),
        )
      : flattened;
  }, [folderKeyword, folders]);

  const relatedRuleCount = useMemo(
    () =>
      data.records.reduce(
        (total, template) => total + Number(template.ruleCount || 0),
        0,
      ),
    [data.records],
  );

  const selectedFolderId =
    typeof selectedFolder === 'number' ? selectedFolder : undefined;

  const startResize = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    const initialWidth = collapsed ? MIN_LEFT_WIDTH : leftWidth;
    if (collapsed) setCollapsed(false);
    dragRef.current = { x: event.clientX, width: initialWidth };

    const move = (current: PointerEvent) => {
      if (!dragRef.current) return;
      setLeftWidth(
        Math.min(
          MAX_LEFT_WIDTH,
          Math.max(
            MIN_LEFT_WIDTH,
            dragRef.current.width + current.clientX - dragRef.current.x,
          ),
        ),
      );
    };

    const end = () => {
      dragRef.current = null;
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', end);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };

    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', end);
  };

  const openCreateFolder = (parentId?: number) => {
    setFolderDialogMode('create');
    setEditingFolder(undefined);
    folderForm.setFieldsValue({ name: '', parentId });
    setFolderDialogOpen(true);
  };

  const openEditFolder = (folder: TemplateFolderView) => {
    setFolderDialogMode('edit');
    setEditingFolder(folder);
    folderForm.setFieldsValue({ name: folder.name, parentId: folder.parentId });
    setFolderDialogOpen(true);
  };

  const refreshLibrary = useCallback(() => {
    void loadFolders();
    void loadCatalogMeta();
    void loadTemplates();
  }, [loadCatalogMeta, loadFolders, loadTemplates]);

  const saveFolder = async () => {
    const payload = await folderForm.validateFields();
    setSubmitting(true);
    try {
      if (folderDialogMode === 'edit' && editingFolder) {
        await updateQualityTemplateFolder(editingFolder.id, payload);
        message.success(
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.folderUpdated',
          }),
        );
      } else {
        await createQualityTemplateFolder(payload);
        message.success(
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.folderCreated',
          }),
        );
      }
      setFolderDialogOpen(false);
      refreshLibrary();
    } catch (error) {
      message.error(
        errorMessage(
          error,
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.folderSaveFailed',
          }),
        ),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const removeFolder = (folder: TemplateFolderView) => {
    Modal.confirm({
      title: intlRef.current.formatMessage(
        { id: 'pages.dataQuality.template.message.deleteFolderTitle' },
        { name: folder.name },
      ),
      content: intlRef.current.formatMessage({
        id: 'pages.dataQuality.template.message.deleteFolderContent',
      }),
      okText: intlRef.current.formatMessage({ id: 'pages.dataQuality.common.delete' }),
      cancelText: intlRef.current.formatMessage({ id: 'pages.dataQuality.common.cancel' }),
      okButtonProps: { danger: true },
      onOk: async () => {
        await deleteQualityTemplateFolder(folder.id);
        if (selectedFolder === folder.id) setSelectedFolder('ALL');
        message.success(
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.folderDeleted',
          }),
        );
        refreshLibrary();
      },
    });
  };

  const openCreateTemplate = () => {
    setDrawerMode('create');
    setEditingTemplate(undefined);
    setDrawerOpen(true);
  };

  const openEditTemplate = (template: TemplateView) => {
    setDrawerMode('edit');
    setEditingTemplate(template);
    setDrawerOpen(true);
  };

  const saveTemplate = async (payload: SaveCustomTemplatePayload) => {
    setSubmitting(true);
    try {
      if (drawerMode === 'edit' && editingTemplate) {
        await updateCustomQualityTemplate(editingTemplate.id, payload);
        message.success(
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.templateUpdated',
          }),
        );
      } else {
        await createCustomQualityTemplate(payload);
        message.success(
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.templateCreated',
          }),
        );
      }
      setDrawerOpen(false);
      refreshLibrary();
    } catch (error) {
      message.error(
        errorMessage(
          error,
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.templateSaveFailed',
          }),
        ),
      );
    } finally {
      setSubmitting(false);
    }
  };

  const removeTemplate = (template: TemplateView) => {
    Modal.confirm({
      title: intlRef.current.formatMessage(
        { id: 'pages.dataQuality.template.message.deleteTemplateTitle' },
        { name: template.name },
      ),
      content: intlRef.current.formatMessage(
        { id: 'pages.dataQuality.template.message.deleteTemplateContent' },
        { count: template.ruleCount },
      ),
      okText: intlRef.current.formatMessage({ id: 'pages.dataQuality.common.delete' }),
      cancelText: intlRef.current.formatMessage({ id: 'pages.dataQuality.common.cancel' }),
      okButtonProps: { danger: true },
      onOk: async () => {
        await deleteCustomQualityTemplate(template.id);
        message.success(
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.templateDeleted',
          }),
        );
        refreshLibrary();
      },
    });
  };

  const openCopy = (template: TemplateView) => {
    setCopyTemplate(template);
    copyForm.setFieldsValue({
      name: `${template.name}${intlRef.current.formatMessage({
        id: 'pages.dataQuality.template.message.copySuffix',
      })}`,
      folderId: template.folderId,
    });
  };

  const saveCopy = async () => {
    if (!copyTemplate) return;
    const payload = await copyForm.validateFields();
    setSubmitting(true);
    try {
      await copyCustomQualityTemplate(copyTemplate.id, payload);
      message.success(
        intlRef.current.formatMessage({
          id: 'pages.dataQuality.template.message.copied',
        }),
      );
      setCopyTemplate(undefined);
      refreshLibrary();
    } catch (error) {
      message.error(
        errorMessage(
          error,
          intlRef.current.formatMessage({
            id: 'pages.dataQuality.template.message.copyFailed',
          }),
        ),
      );
    } finally {
      setSubmitting(false);
    }
  };

  return {
    data,
    folders,
    catalogMeta,
    dimension,
    setDimension,
    activeTab,
    setActiveTab,
    selectedFolder,
    setSelectedFolder,
    keyword,
    setKeyword,
    folderKeyword,
    setFolderKeyword,
    loading,
    folderLoading,
    leftWidth,
    collapsed,
    setCollapsed,
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
    dimensions,
    visibleFolders,
    relatedRuleCount,
    selectedFolderId,
    startResize,
    loadFolders,
    loadCatalogMeta,
    loadTemplates,
    openCreateFolder,
    openEditFolder,
    saveFolder,
    removeFolder,
    openCreateTemplate,
    openEditTemplate,
    saveTemplate,
    removeTemplate,
    openCopy,
    saveCopy,
  };
};
