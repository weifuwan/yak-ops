import { history, useLocation, useParams } from '@umijs/max';
import { Button, ConfigProvider, Empty, message, Spin } from 'antd';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';

import { fetchDataSourceAll } from '@/services/data-source/legacy';
import type { DataSourceRecord } from '@/services/data-source';
import { BRAND_THEME } from '@/styles/brand';

import { linkupJobDefinitionApi } from '../../api';
import SyncTaskEditor from '../../detail/components/SyncTaskEditor';
import validateEditorConnectorForms from '../../detail/form-schema/validateEditorConnectorForms';
import { useSmoothWheelScroll } from '../../detail/hooks/useSmoothWheelScroll';
import {
  buildSavePayload,
  extractSavedId,
  isApiSuccess,
  normalizeEditDetail,
  responseMessage,
  type SyncEditorState,
} from '../../detail/model';

const SECTION_ITEMS = [
  {
    key: 'task-basic',
    label: '任务基础信息',
  },
  {
    key: 'sync-config',
    label: '单表同步配置',
  },
  {
    key: 'runtime-params',
    label: '通道配置',
  },
  {
    key: 'schedule-config',
    label: '调度配置',
  },
  {
    key: 'notification-config',
    label: '通知设置',
  },
  {
    key: 'field-mapping',
    label: '字段映射',
  },
] as const;

type SectionKey = (typeof SECTION_ITEMS)[number]['key'];

const LAST_SECTION_KEY =
  SECTION_ITEMS[SECTION_ITEMS.length - 1].key;

/**
 * 判断已经滚动到底部时允许的误差。
 *
 * 浏览器缩放和小数像素可能导致 scrollTop 无法精确等于最大值，
 * 因此保留少量容差。
 */
const SCROLL_BOTTOM_THRESHOLD = 12;

/**
 * 普通区块定位到滚动容器顶部时保留的间距。
 */
const SECTION_TOP_OFFSET = 24;

/**
 * 平滑滚动期间暂时锁定当前选中项。
 */
const LOCATE_LOCK_DURATION = 650;

interface SectionNavigatorProps {
  activeKey: SectionKey;
  onSelect: (key: SectionKey) => void;
}

function SectionNavigator({
  activeKey,
  onSelect,
}: SectionNavigatorProps) {
  return (
    <nav
      aria-label="配置区块定位"
      className="rounded-xl bg-white px-3 py-4"
    >
      <div className="mb-3 px-2 text-[12px] font-semibold text-[#344054]">
        快速定位
      </div>

      <div className="relative">
        <span
          aria-hidden
          className="
            absolute bottom-4 left-[13px] top-4
            w-px bg-[#e4e7ec]
          "
        />

        <div className="space-y-1">
          {SECTION_ITEMS.map((item) => {
            const active = activeKey === item.key;

            return (
              <button
                key={item.key}
                type="button"
                aria-current={active ? 'location' : undefined}
                className={[
                  'group relative flex w-full cursor-pointer',
                  'items-center gap-3 rounded-lg border-0',
                  'px-2 py-2 text-left transition-colors',
                  active
                    ? 'bg-[rgba(254,44,85,0.08)]'
                    : 'bg-transparent hover:bg-[#f7f8fa]',
                ].join(' ')}
                onClick={() => onSelect(item.key)}
              >
                <span
                  aria-hidden
                  className={[
                    'relative z-10 h-[11px] w-[11px] shrink-0',
                    'rounded-full border transition-all duration-200',
                    active
                      ? [
                          'border-[var(--yak-brand-color)]',
                          'bg-[var(--yak-brand-color)]',
                          'shadow-[0_0_0_3px_rgba(254,44,85,0.12)]',
                        ].join(' ')
                      : [
                          'border-[#d0d5dd]',
                          'bg-[#98a2b3]',
                          'group-hover:border-[#98a2b3]',
                        ].join(' '),
                  ].join(' ')}
                />

                <span
                  className={[
                    'text-[12px] leading-5 transition-colors',
                    active
                      ? 'font-semibold text-[var(--yak-brand-color)]'
                      : [
                          'font-normal text-[#667085]',
                          'group-hover:text-[#344054]',
                        ].join(' '),
                  ].join(' ')}
                >
                  {item.label}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </nav>
  );
}

const validateTaskConfig = (
  editor: SyncEditorState,
): string | null => {
  if (!editor.basic.jobName.trim()) {
    return '请输入任务名称';
  }

  if (!editor.source.dataSourceId) {
    return '请选择来源数据源';
  }

  if (!editor.sink.dataSourceId) {
    return '请选择目标数据源';
  }

  const source = editor.source.config || {};
  const sink = editor.sink.config || {};

  if (source.readMode === 'sql') {
    if (!source.sql?.trim()) {
      return '请填写来源查询 SQL';
    }
  } else if (!source.table) {
    return '请选择来源表';
  }

  if (sink.autoCreateTable) {
    if (!sink.targetTableName?.trim()) {
      return '请输入目标表名';
    }
  } else if (!sink.table) {
    return '请选择目标表';
  }

  if (
    String(sink.writeMode || '').toLowerCase() === 'upsert' &&
    !sink.primaryKey?.trim()
  ) {
    return 'Upsert 写入模式需要配置主键字段';
  }

  if (editor.incremental?.enabled) {
    if (String(editor.source.connectorId || '').toLowerCase() !== 'jdbc') {
      return '全量 + 游标增量仅支持 JDBC 来源';
    }
    if (source.readMode === 'sql') {
      return '全量 + 游标增量暂不支持自定义 SQL';
    }
    if (!editor.incremental.column?.trim()) {
      return '请选择增量游标字段';
    }
    if (String(sink.writeMode || '').toLowerCase() !== 'upsert') {
      return '全量 + 游标增量要求目标端使用 Upsert';
    }
    if (!sink.primaryKey?.trim()) {
      return '全量 + 游标增量要求目标端配置主键';
    }
  }

  if (
    !editor.channel.parallelism ||
    editor.channel.parallelism < 1
  ) {
    return 'Channel 并发数必须大于 0';
  }

  if (
    editor.channel.dirtyDataPolicy === 'skip' &&
    editor.channel.dirtyDataLimit < 0
  ) {
    return '脏数据上限不能小于 0';
  }

  return null;
};

export default function SingleBatchLinkUpConfigPage() {
  const location = useLocation();
  const routeParams = useParams<{ id?: string }>();

  /**
   * 当前页面真正发生滚动的容器。
   */
  const pageRootRef = useRef<HTMLDivElement>(null);

  /**
   * 点击导航进行平滑滚动时，暂时锁定选中项，
   * 避免滚动经过其他区块时发生选中状态闪烁。
   */
  const locatingSectionRef = useRef<SectionKey | null>(null);

  const locateTimerRef = useRef<number>();

  const taskId = useMemo(
    () =>
      routeParams.id ||
      new URLSearchParams(location.search).get('id') ||
      '',
    [location.search, routeParams.id],
  );

  const [editor, setEditor] =
    useState<SyncEditorState | null>(null);

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const [dataSourceLoading, setDataSourceLoading] =
    useState(false);

  const [dataSources, setDataSources] = useState<
    DataSourceRecord[]
  >([]);

  const [activeSection, setActiveSection] =
    useState<SectionKey>('task-basic');

  useSmoothWheelScroll(pageRootRef, true);

  /**
   * 根据当前滚动位置更新右侧快速定位选中项。
   */
  const updateActiveSection = useCallback(() => {
    const container = pageRootRef.current;

    if (!container) {
      return;
    }

    /**
     * 平滑定位期间由点击项控制选中状态，
     * 暂时不让滚动监听覆盖。
     */
    if (locatingSectionRef.current) {
      return;
    }

    const maxScrollTop = Math.max(
      0,
      container.scrollHeight - container.clientHeight,
    );

    const distanceToBottom =
      maxScrollTop - container.scrollTop;

    /**
     * 关键修复：
     *
     * 最后一个字段映射区块后面的内容不够高，
     * 它无法像普通区块一样滚动到容器顶部。
     *
     * 因此只要页面已经滚动到底部，
     * 就直接选中最后一个“字段映射”。
     */
    if (distanceToBottom <= SCROLL_BOTTOM_THRESHOLD) {
      setActiveSection((current) =>
        current === LAST_SECTION_KEY
          ? current
          : LAST_SECTION_KEY,
      );

      return;
    }

    const containerRect =
      container.getBoundingClientRect();

    const threshold =
      containerRect.top + 140;

    let nextActive: SectionKey =
      SECTION_ITEMS[0].key;

    SECTION_ITEMS.forEach((item) => {
      const element =
        document.getElementById(item.key);

      if (
        element &&
        element.getBoundingClientRect().top <= threshold
      ) {
        nextActive = item.key;
      }
    });

    setActiveSection((current) =>
      current === nextActive
        ? current
        : nextActive,
    );
  }, []);

  useEffect(() => {
    const container = pageRootRef.current;

    if (!container || !editor) {
      return undefined;
    }

    let animationFrameId = 0;

    const handleViewportChange = () => {
      window.cancelAnimationFrame(
        animationFrameId,
      );

      animationFrameId =
        window.requestAnimationFrame(
          updateActiveSection,
        );
    };

    container.addEventListener(
      'scroll',
      handleViewportChange,
      {
        passive: true,
      },
    );

    window.addEventListener(
      'resize',
      handleViewportChange,
    );

    updateActiveSection();

    return () => {
      window.cancelAnimationFrame(
        animationFrameId,
      );

      container.removeEventListener(
        'scroll',
        handleViewportChange,
      );

      window.removeEventListener(
        'resize',
        handleViewportChange,
      );
    };
  }, [editor, updateActiveSection]);

  useEffect(() => {
    return () => {
      if (locateTimerRef.current) {
        window.clearTimeout(
          locateTimerRef.current,
        );
      }
    };
  }, []);

  /**
   * 点击快速定位。
   *
   * 不再调用 element.scrollIntoView，
   * 而是直接操作 pageRootRef 对应的滚动容器。
   */
  const handleSectionLocate = (
    key: SectionKey,
  ) => {
    const container = pageRootRef.current;
    const element =
      document.getElementById(key);

    if (!container || !element) {
      return;
    }

    if (locateTimerRef.current) {
      window.clearTimeout(
        locateTimerRef.current,
      );
    }

    locatingSectionRef.current = key;
    setActiveSection(key);

    const maxScrollTop = Math.max(
      0,
      container.scrollHeight - container.clientHeight,
    );

    let nextScrollTop = 0;

    if (key === LAST_SECTION_KEY) {
      /**
       * 字段映射属于最后一个区块，
       * 直接滚动到容器最大位置。
       */
      nextScrollTop = maxScrollTop;
    } else {
      const containerRect =
        container.getBoundingClientRect();

      const elementRect =
        element.getBoundingClientRect();

      const expectedTop =
        container.scrollTop +
        elementRect.top -
        containerRect.top -
        SECTION_TOP_OFFSET;

      nextScrollTop = Math.min(
        Math.max(expectedTop, 0),
        maxScrollTop,
      );
    }

    container.scrollTo({
      top: nextScrollTop,
      behavior: 'smooth',
    });

    /**
     * 等待平滑滚动基本结束后，
     * 解除选中状态锁定并重新校准。
     */
    locateTimerRef.current =
      window.setTimeout(() => {
        locatingSectionRef.current = null;
        updateActiveSection();
      }, LOCATE_LOCK_DURATION);
  };

  const loadDataSources =
    useCallback(async () => {
      try {
        setDataSourceLoading(true);

        const response =
          await fetchDataSourceAll();

        if (!isApiSuccess(response)) {
          message.error(
            responseMessage(
              response,
              '获取数据源失败',
            ),
          );

          setDataSources([]);
          return;
        }

        setDataSources(
          response?.data?.bizData || [],
        );
      } catch (error: any) {
        message.error(
          error?.message ||
            '获取数据源失败',
        );

        setDataSources([]);
      } finally {
        setDataSourceLoading(false);
      }
    }, []);

  const loadTask = useCallback(async () => {
    if (!taskId) {
      return;
    }

    try {
      setLoading(true);

      const response =
        await linkupJobDefinitionApi.selectEditDetail(
          taskId,
        );

      if (
        !isApiSuccess(response) ||
        !response?.data
      ) {
        message.error(
          responseMessage(
            response,
            '获取同步任务失败',
          ),
        );

        setEditor(null);
        return;
      }

      const nextEditor =
        normalizeEditDetail(
          response.data,
          taskId,
        );

      if (nextEditor.mode === 'GUIDE_MULTI') {
        history.replace(
          `/sync/batch-link-up/${encodeURIComponent(
            taskId,
          )}/config/multi?scene=edit`,
        );

        return;
      }

      setEditor(nextEditor);
    } catch (error: any) {
      message.error(
        error?.message ||
          '获取同步任务失败',
      );

      setEditor(null);
    } finally {
      setLoading(false);
    }
  }, [taskId]);

  useEffect(() => {
    if (!taskId) {
      return;
    }

    void Promise.all([
      loadTask(),
      loadDataSources(),
    ]);
  }, [
    loadDataSources,
    loadTask,
    taskId,
  ]);

  const persistEditor = async (
    nextEditor: SyncEditorState,
  ): Promise<SyncEditorState | null> => {
    try {
      setSaving(true);

      const payload =
        buildSavePayload(nextEditor);

      const response =
        await linkupJobDefinitionApi.saveOrUpdateGuideSingle(
          payload,
        );

      if (!isApiSuccess(response)) {
        

        return null;
      }

      const savedEditor: SyncEditorState = {
        ...nextEditor,
        basic: payload.basic,
        id: extractSavedId(
          response,
          nextEditor.id,
        ),
      };

      setEditor(savedEditor);
      message.success('任务配置已保存');

      return savedEditor;
    } catch (error: any) {
      

      return null;
    } finally {
      setSaving(false);
    }
  };

  const handleSave = async () => {
    if (!editor) {
      return;
    }

    const error =
      validateTaskConfig(editor);

    if (error) {
      message.warning(error);
      return;
    }

    const remoteErrors =
      await validateEditorConnectorForms(
        editor,
      );

    if (remoteErrors.length > 0) {
      message.warning(remoteErrors[0]);
      return;
    }

    await persistEditor(editor);
  };

  const handleCancel = () => {
    history.push('/sync/batch-link-up');
  };

  if (!taskId) {
    history.replace('/sync/batch-link-up');
    return null;
  }

  if (loading) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f8fa]">
        <Spin size="large" />
      </div>
    );
  }

  if (!editor) {
    return (
      <div className="flex min-h-[calc(100vh-64px)] items-center justify-center bg-[#f7f8fa]">
        <Empty
          description="未找到单表同步任务"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        >
          <Button onClick={handleCancel}>
            返回任务列表
          </Button>
        </Empty>
      </div>
    );
  }

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="h-[calc(100vh-64px)] overflow-hidden bg-[#f7f8fa] text-[#161823]">
        <div
          ref={pageRootRef}
          className="
            h-full overflow-y-auto
            overscroll-contain
            scroll-smooth
          "
        >
          <div
            className="
              mx-auto grid w-full
              max-w-[1280px]
              grid-cols-1 gap-6
              px-6 pb-6 pt-6
              max-xl:max-w-[1040px]
              xl:grid-cols-[minmax(0,1fr)_160px]
            "
          >
            <div className="min-w-0">
              <main className="pb-4">
                <SyncTaskEditor
                  editor={editor}
                  dataSources={dataSources}
                  dataSourceLoading={
                    dataSourceLoading
                  }
                  onChange={setEditor}
                />
              </main>

              <footer
                className="
                  sticky bottom-0 z-50
                  overflow-hidden rounded-t-lg
                  border border-b-0 border-[#eaecf0]
                  bg-white
                  shadow-[0_-8px_16px_rgba(0,0,0,0.06)]
                "
              >
                <div className="flex min-h-[80px] items-center gap-3 px-8 py-4">
                  <Button
                    type="primary"
                    loading={saving}
                    className="
                      !h-9 !min-w-[120px]
                      !rounded-lg !px-6
                      !font-medium !text-white
                    "
                    onClick={handleSave}
                  >
                    保存配置
                  </Button>

                  <Button
                    disabled={saving}
                    className="
                      !h-9 !min-w-[120px]
                      !rounded-lg !border-0
                      !bg-[#f2f3f5] !px-5
                      !font-medium !text-[#344054]
                      hover:!bg-[#e9eaec]
                    "
                    onClick={handleCancel}
                  >
                    取消
                  </Button>
                </div>
              </footer>
            </div>

            <aside className="hidden xl:block">
              <div className="sticky top-6">
                <SectionNavigator
                  activeKey={activeSection}
                  onSelect={
                    handleSectionLocate
                  }
                />
              </div>
            </aside>
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}
