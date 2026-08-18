import {
  ScreenRenderer,
  getScreenTemplateById,
} from '@/components/screen-engine';
import type { PublishedDataset } from '@/components/analysis/model';
import { history, useParams } from '@umijs/max';
import { Button, Input, message } from 'antd';
import { ArrowLeft, Database, Eye, Save, Send } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { DataBindingPanel } from './DataBindingPanel';
import type {
  DigitalScreenBindings,
  DigitalScreenComponentBinding,
  DigitalScreenInstance,
} from './model';
import { fetchDigitalScreenDatasets } from './screen-data-service';
import {
  fetchDigitalScreen,
  publishDigitalScreen,
  unpublishDigitalScreen,
  updateDigitalScreen,
} from './screen-service';
import { useScreenRuntimeData } from './use-screen-data';

export default function DigitalScreenEditorPage() {
  const { id } = useParams<{ id: string }>();
  const [screen, setScreen] = useState<DigitalScreenInstance>();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [bindings, setBindings] = useState<DigitalScreenBindings>({});
  const [selectedComponentId, setSelectedComponentId] = useState<string>();
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [datasetsLoading, setDatasetsLoading] = useState(true);
  const [datasetsError, setDatasetsError] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);

  const template = useMemo(
    () => (screen ? getScreenTemplateById(screen.templateId) : undefined),
    [screen],
  );
  const selectedComponent = useMemo(
    () => template?.components.find((component) => component.id === selectedComponentId),
    [template, selectedComponentId],
  );
  const runtime = useScreenRuntimeData(template, bindings, datasets);

  const loadScreen = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      const detail = await fetchDigitalScreen(id);
      setScreen(detail);
      setName(detail.name);
      setDescription(detail.description || '');
      setBindings(detail.bindings || {});
    } catch (error) {
      message.error(error instanceof Error ? error.message : '加载数字化大屏失败');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void loadScreen();
  }, [loadScreen]);

  useEffect(() => {
    let active = true;
    setDatasetsLoading(true);
    setDatasetsError('');
    void fetchDigitalScreenDatasets()
      .then((values) => {
        if (active) setDatasets(values);
      })
      .catch((error) => {
        if (!active) return;
        setDatasets([]);
        setDatasetsError(error instanceof Error ? error.message : '加载 Dataset 失败');
      })
      .finally(() => {
        if (active) setDatasetsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!template) return;
    if (selectedComponentId && template.components.some((component) => component.id === selectedComponentId)) return;
    const preferred = template.components.find((component) => component.type !== 'text')
      || template.components[0];
    setSelectedComponentId(preferred?.id);
  }, [template?.id, selectedComponentId]);

  const save = async (showMessage = true) => {
    if (!id) return undefined;
    if (!name.trim()) {
      message.warning('请输入大屏名称');
      return undefined;
    }

    setSaving(true);
    try {
      const updated = await updateDigitalScreen(id, { name, description, bindings });
      setScreen(updated);
      setBindings(updated.bindings);
      if (showMessage) message.success('大屏已保存');
      return updated;
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存数字化大屏失败');
      return undefined;
    } finally {
      setSaving(false);
    }
  };

  const togglePublish = async () => {
    if (!id || !screen) return;
    setPublishing(true);
    try {
      const saved = await save(false);
      if (!saved) return;
      const updated = saved.status === 'published'
        ? await unpublishDigitalScreen(id)
        : await publishDigitalScreen(id);
      setScreen(updated);
      message.success(updated.status === 'published' ? '大屏已发布' : '大屏已取消发布');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新发布状态失败');
    } finally {
      setPublishing(false);
    }
  };

  const updateSelectedBinding = (next?: DigitalScreenComponentBinding) => {
    if (!selectedComponent) return;
    setBindings((current) => {
      const result = { ...current };
      if (next) result[selectedComponent.id] = next;
      else delete result[selectedComponent.id];
      return result;
    });
  };

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#f4f5f6] text-[13px] text-[#98a2b3]">
        正在加载数字化大屏...
      </div>
    );
  }

  if (!screen) {
    return (
      <div className="flex h-screen flex-col items-center justify-center bg-[#f4f5f6] text-[#667085]">
        <div className="text-[14px]">数字化大屏不存在</div>
        <Button type="link" onClick={() => history.push('/digital-screen')}>返回大屏列表</Button>
      </div>
    );
  }

  const bindableCount = template?.components.filter((component) => component.type !== 'text').length ?? 0;
  const selectedQuerying = selectedComponent
    ? runtime.loadingIds.includes(selectedComponent.id)
    : false;
  const selectedQueryError = selectedComponent
    ? runtime.errors[selectedComponent.id]
    : undefined;

  return (
    <div className="flex h-screen min-w-[1180px] flex-col overflow-hidden bg-[#f4f5f6] text-[#161823]">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-[#e5e7ea] bg-white px-4">
        <div className="flex min-w-0 items-center gap-2">
          <Button
            type="text"
            icon={<ArrowLeft size={16} />}
            onClick={() => history.push('/digital-screen')}
          />
          <div className="h-5 w-px bg-[#e7e9ec]" />
          <Input
            variant="borderless"
            value={name}
            onChange={(event) => setName(event.target.value)}
            className="w-[320px] px-2 text-[14px] font-semibold"
            maxLength={80}
          />
          <span className={[
            'ml-1 rounded-[4px] px-2 py-1 text-[11px] font-medium',
            screen.status === 'published'
              ? 'bg-[#edf8f2] text-[#27845a]'
              : 'bg-[#f2f3f4] text-[#7b818a]',
          ].join(' ')}>
            {screen.status === 'published' ? '已发布' : '草稿'}
          </span>
          <span className="ml-1 text-[11px] text-[#98a2b3]">
            已绑定 {runtime.boundCount}/{bindableCount}
          </span>
          {runtime.loadingCount ? (
            <span className="text-[11px] text-[#8a9099]">正在刷新数据...</span>
          ) : null}
        </div>

        <div className="flex items-center gap-2">
          <Button
            icon={<Eye size={14} />}
            onClick={() => history.push(`/digital-screen/${screen.id}`)}
          >
            预览
          </Button>
          <Button icon={<Save size={14} />} loading={saving} onClick={() => void save()}>
            保存
          </Button>
          <Button
            type={screen.status === 'published' ? 'default' : 'primary'}
            icon={<Send size={14} />}
            loading={publishing}
            onClick={() => void togglePublish()}
          >
            {screen.status === 'published' ? '取消发布' : '发布'}
          </Button>
        </div>
      </header>

      <div className="flex min-h-0 flex-1">
        <main className="min-w-0 flex-1 overflow-auto p-6">
          <div className="mx-auto flex min-h-full max-w-[1400px] items-center justify-center">
            {template ? (
              <div className="w-full overflow-hidden border border-[#dfe2e6] bg-[#111827]">
                <ScreenRenderer
                  template={template}
                  data={runtime.data}
                  selectedComponentId={selectedComponentId}
                  onComponentClick={(component) => setSelectedComponentId(component.id)}
                />
              </div>
            ) : (
              <div className="flex h-[420px] w-full items-center justify-center border border-[#e0e3e7] bg-white text-[13px] text-[#98a2b3]">
                当前模板不存在
              </div>
            )}
          </div>
        </main>

        <aside className="w-[360px] shrink-0 overflow-y-auto border-l border-[#e5e7ea] bg-white">
          <section className="border-b border-[#eceef1] px-5 py-5">
            <div className="text-[13px] font-semibold text-[#161823]">大屏设置</div>
            <label className="mt-4 block text-[12px] text-[#667085]">
              名称
              <Input
                value={name}
                onChange={(event) => setName(event.target.value)}
                className="mt-2"
                variant="filled"
                maxLength={80}
              />
            </label>
            <label className="mt-4 block text-[12px] text-[#667085]">
              描述
              <Input.TextArea
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                className="mt-2"
                variant="filled"
                rows={3}
                maxLength={200}
                placeholder="可选"
              />
            </label>
          </section>

          <section className="border-b border-[#eceef1] px-5 py-5">
            <div className="text-[13px] font-semibold text-[#161823]">模板</div>
            <div className="mt-4 rounded-[7px] bg-[#f6f7f8] p-3">
              <div className="text-[13px] font-medium text-[#444950]">{template?.name || '未知模板'}</div>
              <div className="mt-1 text-[11px] text-[#98a2b3]">
                {template ? `${template.category} · ${template.width} × ${template.height}` : screen.templateId}
              </div>
            </div>
            <div className="mt-2 text-[11px] leading-[18px] text-[#a3a8b0]">
              布局由模板固定。点击左侧组件后，只配置它消费的数据，不修改模板设计。
            </div>
          </section>

          <section className="px-5 py-5">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 text-[13px] font-semibold text-[#161823]">
                <Database size={14} /> 数据绑定
              </div>
              <span className="text-[10px] text-[#a3a8b0]">{datasets.length} 个可用 Dataset</span>
            </div>
            <div className="mt-4">
              <DataBindingPanel
                component={selectedComponent}
                binding={selectedComponent ? bindings[selectedComponent.id] : undefined}
                datasets={datasets}
                datasetsLoading={datasetsLoading}
                datasetsError={datasetsError}
                querying={selectedQuerying}
                queryError={selectedQueryError}
                onChange={updateSelectedBinding}
              />
            </div>
          </section>
        </aside>
      </div>
    </div>
  );
}
