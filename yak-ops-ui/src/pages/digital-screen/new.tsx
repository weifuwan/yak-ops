import {
  ScreenRenderer,
  builtinScreenTemplates,
  listScreenTemplateCategories,
} from '@/components/screen-engine';
import type { ScreenTemplate } from '@/components/screen-engine';
import { history } from '@umijs/max';
import { Button, Input, Modal, message } from 'antd';
import { ArrowLeft, Eye, Search } from 'lucide-react';
import { useMemo, useState } from 'react';
import { createDigitalScreen } from './screen-service';

const categories = ['全部', ...listScreenTemplateCategories()];

export default function DigitalScreenTemplatePage() {
  const [category, setCategory] = useState('全部');
  const [keyword, setKeyword] = useState('');
  const [previewTemplate, setPreviewTemplate] = useState<ScreenTemplate>();
  const [selectedTemplate, setSelectedTemplate] = useState<ScreenTemplate>();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [creating, setCreating] = useState(false);

  const templates = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    return builtinScreenTemplates.filter((template) => {
      if (category !== '全部' && template.category !== category) return false;
      if (!value) return true;
      return [template.name, template.description, template.category]
        .some((field) => String(field || '').toLowerCase().includes(value));
    });
  }, [category, keyword]);

  const openCreate = (template: ScreenTemplate) => {
    setSelectedTemplate(template);
    setName(template.name);
    setDescription('');
  };

  const createScreen = async () => {
    if (!selectedTemplate) return;
    if (!name.trim()) {
      message.warning('请输入大屏名称');
      return;
    }

    setCreating(true);
    try {
      const screen = await createDigitalScreen({
        name,
        description,
        templateId: selectedTemplate.id,
      });
      message.success('数字化大屏已创建');
      setSelectedTemplate(undefined);
      history.push(`/digital-screen/${screen.id}/edit`);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '创建数字化大屏失败');
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-48px)] bg-[#f6f7f8]">
      <div className="min-h-[calc(100vh-64px)] rounded-[10px] bg-white px-6 py-5">
        <div className="flex items-center gap-3">
          <Button
            type="text"
            icon={<ArrowLeft size={16} />}
            onClick={() => history.push('/digital-screen')}
          />
          <div>
            <div className="text-[18px] font-semibold leading-7 text-[#161823]">选择大屏模板</div>
            <div className="mt-0.5 text-[12px] text-[#8a9099]">模板已预设布局与视觉样式，创建后直接进入配置</div>
          </div>
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-b border-[#eceef1] pb-3">
          <div className="flex flex-wrap items-center gap-1">
            {categories.map((item) => {
              const active = category === item;
              return (
                <button
                  key={item}
                  type="button"
                  onClick={() => setCategory(item)}
                  className={[
                    'h-8 rounded-[6px] border-0 px-3 text-[13px] transition-colors',
                    active
                      ? 'bg-[#f0f1f2] font-semibold text-[#161823]'
                      : 'bg-transparent text-[#8a9099] hover:bg-[#f7f8f9] hover:text-[#444950]',
                  ].join(' ')}
                >
                  {item}
                </button>
              );
            })}
          </div>
          <Input
            allowClear
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            prefix={<Search size={14} className="text-[#98a2b3]" />}
            placeholder="搜索模板"
            className="w-[220px]"
            variant="filled"
          />
        </div>

        {templates.length === 0 ? (
          <div className="flex h-[380px] items-center justify-center text-[13px] text-[#98a2b3]">
            没有匹配的大屏模板
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-x-5 gap-y-6 pt-5 xl:grid-cols-2 2xl:grid-cols-3">
            {templates.map((template) => (
              <article
                key={template.id}
                className="group overflow-hidden rounded-[8px] border border-[#e7e9ec] bg-white transition-colors hover:border-[#cfd4da]"
              >
                <div className="relative overflow-hidden bg-[#111827]">
                  <ScreenRenderer template={template} className="pointer-events-none" />
                  <div className="absolute inset-0 flex items-center justify-center gap-2 bg-black/0 opacity-0 transition-all group-hover:bg-black/25 group-hover:opacity-100">
                    <Button
                      size="small"
                      icon={<Eye size={13} />}
                      onClick={() => setPreviewTemplate(template)}
                    >
                      预览
                    </Button>
                    <Button type="primary" size="small" onClick={() => openCreate(template)}>
                      使用模板
                    </Button>
                  </div>
                </div>

                <div className="flex items-start justify-between gap-4 px-4 py-3.5">
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[14px] font-semibold text-[#161823]">{template.name}</div>
                    <div className="mt-1 line-clamp-2 min-h-[36px] text-[12px] leading-[18px] text-[#8a9099]">
                      {template.description || '预设数字化大屏模板'}
                    </div>
                    <div className="mt-2 flex items-center gap-2 text-[11px] text-[#a3a8b0]">
                      <span>{template.category}</span>
                      <span>·</span>
                      <span>{template.width} × {template.height}</span>
                      <span>·</span>
                      <span>{template.components.length} 个组件</span>
                    </div>
                  </div>
                  <Button type="primary" ghost size="small" onClick={() => openCreate(template)}>
                    使用
                  </Button>
                </div>
              </article>
            ))}
          </div>
        )}
      </div>

      <Modal
        open={Boolean(previewTemplate)}
        title={previewTemplate ? `${previewTemplate.name} · 模板预览` : '模板预览'}
        width="min(1200px, 92vw)"
        footer={previewTemplate ? [
          <Button key="close" onClick={() => setPreviewTemplate(undefined)}>关闭</Button>,
          <Button
            key="use"
            type="primary"
            onClick={() => {
              const template = previewTemplate;
              setPreviewTemplate(undefined);
              openCreate(template);
            }}
          >
            使用此模板
          </Button>,
        ] : null}
        onCancel={() => setPreviewTemplate(undefined)}
        destroyOnClose
      >
        {previewTemplate ? (
          <div className="overflow-hidden rounded-[6px] border border-[#e6e8eb] bg-[#111827]">
            <ScreenRenderer template={previewTemplate} />
          </div>
        ) : null}
      </Modal>

      <Modal
        open={Boolean(selectedTemplate)}
        title="创建数字化大屏"
        okText="创建并进入配置"
        cancelText="取消"
        confirmLoading={creating}
        onOk={() => void createScreen()}
        onCancel={() => setSelectedTemplate(undefined)}
        destroyOnClose
      >
        {selectedTemplate ? (
          <div className="pt-2">
            <div className="mb-4 overflow-hidden rounded-[6px] border border-[#e6e8eb] bg-[#111827]">
              <ScreenRenderer template={selectedTemplate} className="pointer-events-none" />
            </div>
            <label className="block text-[12px] font-medium text-[#444950]">
              大屏名称
              <Input
                value={name}
                onChange={(event) => setName(event.target.value)}
                className="mt-2"
                maxLength={80}
                autoFocus
              />
            </label>
            <label className="mt-4 block text-[12px] font-medium text-[#444950]">
              描述
              <Input.TextArea
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                className="mt-2"
                rows={3}
                maxLength={200}
                placeholder="可选"
              />
            </label>
          </div>
        ) : null}
      </Modal>
    </div>
  );
}
