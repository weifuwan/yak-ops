import { ScreenRenderer } from '@/components/screen-engine';
import type { ScreenTemplate } from '@/components/screen-engine';
import {
  copyManagedScreenTemplate,
  deleteCustomScreenTemplate,
  listManagedScreenTemplateCategories,
  listManagedScreenTemplates,
  parseAndImportManagedScreenTemplate,
  setManagedScreenTemplateStatus,
  stringifyScreenTemplate,
  updateCustomScreenTemplate,
} from '@/services/screen-template-service';
import type {
  ManagedScreenTemplate,
  ManagedScreenTemplateStatus,
} from '@/services/screen-template-service';
import { Button, Input, Modal, Popconfirm, Select, message } from 'antd';
import { Copy, Eye, FileJson, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { useMemo, useState } from 'react';
import { fetchDigitalScreens } from '@/pages/digital-screen/screen-service';

const statusLabel: Record<ManagedScreenTemplateStatus, string> = {
  draft: '草稿',
  published: '已发布',
  offline: '已下架',
};

const statusClass: Record<ManagedScreenTemplateStatus, string> = {
  draft: 'bg-[#f3f4f6] text-[#667085]',
  published: 'bg-[#edf8f2] text-[#27845a]',
  offline: 'bg-[#fff4ed] text-[#b54708]',
};

const parseTemplate = (json: string): ScreenTemplate => {
  try {
    return JSON.parse(json) as ScreenTemplate;
  } catch {
    throw new Error('模板 JSON 格式不正确');
  }
};

export default function ScreenTemplateSettingsPanel() {
  const [revision, setRevision] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<'all' | ManagedScreenTemplateStatus>('all');
  const [category, setCategory] = useState('全部');
  const [preview, setPreview] = useState<ManagedScreenTemplate>();
  const [editing, setEditing] = useState<ManagedScreenTemplate>();
  const [editJson, setEditJson] = useState('');
  const [importOpen, setImportOpen] = useState(false);
  const [importJson, setImportJson] = useState('');
  const [saving, setSaving] = useState(false);

  const records = useMemo(() => listManagedScreenTemplates(), [revision]);
  const categories = useMemo(() => ['全部', ...listManagedScreenTemplateCategories()], [revision]);
  const filtered = useMemo(() => {
    const value = keyword.trim().toLowerCase();
    return records.filter((record) => {
      if (status !== 'all' && record.status !== status) return false;
      if (category !== '全部' && record.template.category !== category) return false;
      if (!value) return true;
      return [
        record.template.name,
        record.template.description,
        record.template.category,
        record.id,
      ].some((field) => String(field || '').toLowerCase().includes(value));
    });
  }, [category, keyword, records, status]);

  const refresh = () => setRevision((value) => value + 1);

  const copyTemplate = (record: ManagedScreenTemplate) => {
    try {
      copyManagedScreenTemplate(record.id);
      refresh();
      message.success('已复制为自定义草稿模板');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '复制模板失败');
    }
  };

  const toggleStatus = (record: ManagedScreenTemplate) => {
    try {
      const next: ManagedScreenTemplateStatus = record.status === 'published' ? 'offline' : 'published';
      setManagedScreenTemplateStatus(record.id, next);
      refresh();
      message.success(next === 'published' ? '模板已发布' : '模板已下架');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新模板状态失败');
    }
  };

  const openEdit = (record: ManagedScreenTemplate) => {
    setEditing(record);
    setEditJson(stringifyScreenTemplate(record.template));
  };

  const saveEdit = () => {
    if (!editing) return;
    setSaving(true);
    try {
      const template = parseTemplate(editJson);
      updateCustomScreenTemplate(editing.id, template);
      setEditing(undefined);
      refresh();
      message.success('模板已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存模板失败');
    } finally {
      setSaving(false);
    }
  };

  const importTemplate = () => {
    setSaving(true);
    try {
      parseAndImportManagedScreenTemplate(importJson);
      setImportOpen(false);
      setImportJson('');
      refresh();
      message.success('模板已导入为草稿');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '导入模板失败');
    } finally {
      setSaving(false);
    }
  };

  const removeTemplate = async (record: ManagedScreenTemplate) => {
    try {
      const screens = await fetchDigitalScreens();
      const usage = screens.filter((screen) => screen.templateId === record.id).length;
      if (usage > 0) {
        message.warning(`当前模板已被 ${usage} 个数字化大屏使用，请先处理这些大屏`);
        return;
      }
      deleteCustomScreenTemplate(record.id);
      refresh();
      message.success('模板已删除');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除模板失败');
    }
  };

  const count = (target: ManagedScreenTemplateStatus) => records.filter((item) => item.status === target).length;

  return (
    <div>
      <div className="flex items-start justify-between gap-6">
        <div>
          <div className="text-[18px] font-semibold leading-7 text-[#161823]">大屏模板</div>
          <div className="mt-1 text-[12px] leading-5 text-[#8a9099]">
            维护数字化大屏创建时可使用的模板
          </div>
        </div>
        <Button
          type="primary"
          icon={<Plus size={14} />}
          onClick={() => {
            setImportJson('');
            setImportOpen(true);
          }}
        >
          导入模板
        </Button>
      </div>

      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-b border-[#eceef1] pb-3">
        <div className="flex items-center gap-1">
          {([
            ['all', '全部', records.length],
            ['published', '已发布', count('published')],
            ['draft', '草稿', count('draft')],
            ['offline', '已下架', count('offline')],
          ] as const).map(([key, label, total]) => (
            <button
              key={key}
              type="button"
              onClick={() => setStatus(key)}
              className={[
                'h-8 rounded-[6px] border-0 px-3 text-[13px] transition-colors',
                status === key
                  ? 'bg-[#f0f1f2] font-semibold text-[#161823]'
                  : 'bg-transparent text-[#8a9099] hover:bg-[#f7f8f9] hover:text-[#444950]',
              ].join(' ')}
            >
              {label}<span className="ml-1 text-[11px] font-normal text-[#a3a8b0]">{total}</span>
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2">
          <Select
            value={category}
            onChange={setCategory}
            options={categories.map((item) => ({ label: item, value: item }))}
            className="w-[130px]"
            variant="filled"
          />
          <Input
            allowClear
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            prefix={<Search size={14} className="text-[#98a2b3]" />}
            placeholder="搜索模板"
            className="w-[200px]"
            variant="filled"
          />
        </div>
      </div>

      {filtered.length === 0 ? (
        <div className="flex h-[320px] items-center justify-center text-[13px] text-[#98a2b3]">
          没有匹配的大屏模板
        </div>
      ) : (
        <div className="mt-4 overflow-hidden rounded-[8px] border border-[#e7e9ec]">
          <div className="grid grid-cols-[minmax(280px,1fr)_110px_90px_90px_210px] items-center bg-[#fafafa] px-4 py-2.5 text-[11px] font-medium text-[#8a9099]">
            <div>模板</div>
            <div>分类</div>
            <div>来源</div>
            <div>状态</div>
            <div className="text-right">操作</div>
          </div>
          {filtered.map((record) => (
            <div
              key={record.id}
              className="grid min-h-[96px] grid-cols-[minmax(280px,1fr)_110px_90px_90px_210px] items-center border-t border-[#eef0f2] px-4 py-3"
            >
              <div className="flex min-w-0 items-center gap-3">
                <button
                  type="button"
                  className="w-[118px] shrink-0 overflow-hidden rounded-[5px] border border-[#e5e7ea] bg-[#111827] p-0"
                  onClick={() => setPreview(record)}
                >
                  <ScreenRenderer template={record.template} className="pointer-events-none" />
                </button>
                <div className="min-w-0">
                  <div className="truncate text-[13px] font-semibold text-[#161823]">{record.template.name}</div>
                  <div className="mt-1 line-clamp-2 text-[11px] leading-[17px] text-[#98a2b3]">
                    {record.template.description || `${record.template.width} × ${record.template.height}`}
                  </div>
                </div>
              </div>
              <div className="truncate text-[12px] text-[#667085]">{record.template.category}</div>
              <div className="text-[12px] text-[#667085]">{record.source === 'builtin' ? '官方' : '自定义'}</div>
              <div>
                <span className={`rounded-[4px] px-2 py-1 text-[11px] font-medium ${statusClass[record.status]}`}>
                  {statusLabel[record.status]}
                </span>
              </div>
              <div className="flex items-center justify-end gap-1">
                <Button type="text" size="small" icon={<Eye size={13} />} onClick={() => setPreview(record)}>
                  预览
                </Button>
                <Button type="text" size="small" icon={<Copy size={13} />} onClick={() => copyTemplate(record)}>
                  复制
                </Button>
                {record.source === 'custom' ? (
                  <Button type="text" size="small" icon={<Pencil size={13} />} onClick={() => openEdit(record)}>
                    编辑
                  </Button>
                ) : null}
                <Button type="text" size="small" onClick={() => toggleStatus(record)}>
                  {record.status === 'published' ? '下架' : '发布'}
                </Button>
                {record.source === 'custom' ? (
                  <Popconfirm
                    title="删除模板"
                    description="删除后无法恢复，已被大屏使用的模板不会允许删除。"
                    okText="删除"
                    cancelText="取消"
                    onConfirm={() => void removeTemplate(record)}
                  >
                    <Button type="text" size="small" danger icon={<Trash2 size={13} />} />
                  </Popconfirm>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={Boolean(preview)}
        title={preview ? `${preview.template.name} · 模板预览` : '模板预览'}
        width="min(1200px, 92vw)"
        footer={<Button onClick={() => setPreview(undefined)}>关闭</Button>}
        onCancel={() => setPreview(undefined)}
        destroyOnClose
      >
        {preview ? (
          <div className="overflow-hidden rounded-[6px] border border-[#e6e8eb] bg-[#111827]">
            <ScreenRenderer template={preview.template} />
          </div>
        ) : null}
      </Modal>

      <Modal
        open={importOpen}
        title="导入大屏模板"
        okText="导入为草稿"
        cancelText="取消"
        width={760}
        confirmLoading={saving}
        onOk={importTemplate}
        onCancel={() => setImportOpen(false)}
        destroyOnClose
      >
        <div className="pt-2">
          <div className="mb-3 flex items-center gap-2 text-[12px] text-[#667085]">
            <FileJson size={14} /> 粘贴符合 ScreenTemplate 协议的 JSON；模板 ID 会自动生成，避免覆盖已有模板。
          </div>
          <Input.TextArea
            value={importJson}
            onChange={(event) => setImportJson(event.target.value)}
            rows={18}
            spellCheck={false}
            placeholder={'{\n  "version": 1,\n  "name": "我的模板",\n  ...\n}'}
            className="font-mono text-[12px]"
          />
        </div>
      </Modal>

      <Modal
        open={Boolean(editing)}
        title={editing ? `编辑模板 · ${editing.template.name}` : '编辑模板'}
        okText="保存"
        cancelText="取消"
        width={820}
        confirmLoading={saving}
        onOk={saveEdit}
        onCancel={() => setEditing(undefined)}
        destroyOnClose
      >
        <div className="pt-2">
          <div className="mb-3 text-[12px] leading-5 text-[#667085]">
            当前阶段通过 JSON 维护模板布局和样式，不提供自由拖拽模板设计器。模板 ID 会保持不变。
          </div>
          <Input.TextArea
            value={editJson}
            onChange={(event) => setEditJson(event.target.value)}
            rows={20}
            spellCheck={false}
            className="font-mono text-[12px]"
          />
        </div>
      </Modal>
    </div>
  );
}
