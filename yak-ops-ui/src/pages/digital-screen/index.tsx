import { ScreenRenderer, getScreenTemplateById } from '@/components/screen-engine';
import { history } from '@umijs/max';
import { Button, Input, Popconfirm, message } from 'antd';
import { Copy, Eye, Monitor, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import type { DigitalScreenInstance, DigitalScreenStatus } from './model';
import {
  deleteDigitalScreen,
  duplicateDigitalScreen,
  fetchDigitalScreens,
} from './screen-service';

type StatusFilter = 'all' | DigitalScreenStatus;

const formatTime = (value: string) => value.replace('T', ' ').slice(0, 16);

export default function DigitalScreenListPage() {
  const [screens, setScreens] = useState<DigitalScreenInstance[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<StatusFilter>('all');

  const loadScreens = useCallback(async () => {
    setLoading(true);
    try {
      setScreens(await fetchDigitalScreens());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载数字化大屏失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadScreens();
  }, [loadScreens]);

  const filteredScreens = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    return screens.filter((screen) => {
      if (status !== 'all' && screen.status !== status) return false;
      if (!value) return true;
      const template = getScreenTemplateById(screen.templateId);
      return [screen.name, screen.description, template?.name]
        .some((field) => String(field || '').toLowerCase().includes(value));
    });
  }, [keyword, screens, status]);

  const statusItems: Array<{ key: StatusFilter; label: string; count: number }> = [
    { key: 'all', label: '全部', count: screens.length },
    { key: 'draft', label: '草稿', count: screens.filter((item) => item.status === 'draft').length },
    { key: 'published', label: '已发布', count: screens.filter((item) => item.status === 'published').length },
  ];

  const handleDuplicate = async (screen: DigitalScreenInstance) => {
    try {
      const duplicated = await duplicateDigitalScreen(screen.id);
      message.success('已复制大屏');
      await loadScreens();
      history.push(`/digital-screen/${duplicated.id}/edit`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '复制大屏失败');
    }
  };

  const handleDelete = async (screen: DigitalScreenInstance) => {
    try {
      await deleteDigitalScreen(screen.id);
      message.success('大屏已删除');
      await loadScreens();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除大屏失败');
    }
  };

  return (
    <div className="min-h-[calc(100vh-48px)] bg-[#f6f7f8]">
      <div className="min-h-[calc(100vh-64px)] rounded-[10px] bg-white px-6 py-5">
        <div className="flex items-start justify-between gap-6">
          <div>
            <div className="text-[18px] font-semibold leading-7 text-[#161823]">数字化大屏</div>
            <div className="mt-1 text-[12px] leading-5 text-[#8a9099]">
              基于预设模板快速创建数据展示大屏
            </div>
          </div>
          <Button
            type="primary"
            icon={<Plus size={15} />}
            onClick={() => history.push('/digital-screen/new')}
          >
            新建大屏
          </Button>
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-b border-[#eceef1] pb-3">
          <div className="flex items-center gap-1">
            {statusItems.map((item) => {
              const active = status === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => setStatus(item.key)}
                  className={[
                    'h-8 rounded-[6px] border-0 px-3 text-[13px] transition-colors',
                    active
                      ? 'bg-[#f0f1f2] font-semibold text-[#161823]'
                      : 'bg-transparent text-[#8a9099] hover:bg-[#f7f8f9] hover:text-[#444950]',
                  ].join(' ')}
                >
                  {item.label}
                  <span className="ml-1 text-[11px] font-normal text-[#a3a8b0]">{item.count}</span>
                </button>
              );
            })}
          </div>

          <Input
            allowClear
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            prefix={<Search size={14} className="text-[#98a2b3]" />}
            placeholder="搜索大屏"
            className="w-[220px]"
            variant="filled"
          />
        </div>

        {loading && screens.length === 0 ? (
          <div className="flex h-[360px] items-center justify-center text-[13px] text-[#98a2b3]">
            正在加载数字化大屏...
          </div>
        ) : filteredScreens.length === 0 ? (
          <div className="flex h-[420px] flex-col items-center justify-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-[12px] bg-[#f5f6f7] text-[#98a2b3]">
              <Monitor size={22} strokeWidth={1.7} />
            </div>
            <div className="mt-3 text-[13px] font-medium text-[#667085]">
              {keyword || status !== 'all' ? '没有匹配的数字化大屏' : '暂无数字化大屏'}
            </div>
            {!keyword && status === 'all' ? (
              <Button
                type="link"
                className="mt-1 px-0 text-[12px]"
                onClick={() => history.push('/digital-screen/new')}
              >
                从模板创建第一个大屏
              </Button>
            ) : null}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-x-5 gap-y-6 pt-5 xl:grid-cols-2 2xl:grid-cols-3">
            {filteredScreens.map((screen) => {
              const template = getScreenTemplateById(screen.templateId);
              return (
                <article
                  key={screen.id}
                  className="group overflow-hidden rounded-[8px] border border-[#e7e9ec] bg-white transition-[border-color,background-color] hover:border-[#d8dce1]"
                >
                  <button
                    type="button"
                    onClick={() => history.push(`/digital-screen/${screen.id}/edit`)}
                    className="relative block w-full overflow-hidden border-0 bg-[#111827] p-0 text-left"
                  >
                    {template ? (
                      <ScreenRenderer template={template} className="pointer-events-none" />
                    ) : (
                      <div className="flex aspect-video items-center justify-center text-[12px] text-white/60">
                        模板不存在
                      </div>
                    )}
                    <div className="absolute inset-0 flex items-center justify-center bg-black/0 opacity-0 transition-all group-hover:bg-black/20 group-hover:opacity-100">
                      <span className="flex h-8 items-center gap-1.5 rounded-[6px] bg-white/95 px-3 text-[12px] font-medium text-[#161823]">
                        <Pencil size={13} /> 编辑
                      </span>
                    </div>
                  </button>

                  <div className="px-4 py-3.5">
                    <div className="flex min-w-0 items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <button
                          type="button"
                          onClick={() => history.push(`/digital-screen/${screen.id}/edit`)}
                          className="max-w-full truncate border-0 bg-transparent p-0 text-left text-[14px] font-semibold text-[#161823] hover:underline"
                        >
                          {screen.name}
                        </button>
                        <div className="mt-1 flex items-center gap-2 text-[11px] text-[#98a2b3]">
                          <span>{template?.name || '未知模板'}</span>
                          <span className="text-[#d7dade]">·</span>
                          <span>{formatTime(screen.updatedAt)}</span>
                        </div>
                      </div>
                      <span className={[
                        'shrink-0 rounded-[4px] px-2 py-1 text-[11px] font-medium',
                        screen.status === 'published'
                          ? 'bg-[#edf8f2] text-[#27845a]'
                          : 'bg-[#f4f5f6] text-[#7b818a]',
                      ].join(' ')}>
                        {screen.status === 'published' ? '已发布' : '草稿'}
                      </span>
                    </div>

                    <div className="mt-3 flex items-center justify-end gap-1 border-t border-[#f0f1f2] pt-2.5">
                      <Button
                        type="text"
                        size="small"
                        icon={<Eye size={13} />}
                        onClick={() => history.push(`/digital-screen/${screen.id}`)}
                      >
                        预览
                      </Button>
                      <Button
                        type="text"
                        size="small"
                        icon={<Copy size={13} />}
                        onClick={() => void handleDuplicate(screen)}
                      >
                        复制
                      </Button>
                      <Popconfirm
                        title="删除大屏"
                        description={`确定删除“${screen.name}”吗？`}
                        okText="删除"
                        cancelText="取消"
                        onConfirm={() => void handleDelete(screen)}
                      >
                        <Button type="text" size="small" danger icon={<Trash2 size={13} />}>
                          删除
                        </Button>
                      </Popconfirm>
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
