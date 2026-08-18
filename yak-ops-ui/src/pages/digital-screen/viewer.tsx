import { ScreenRenderer, getScreenTemplateById } from '@/components/screen-engine';
import type { PublishedDataset } from '@/components/analysis/model';
import { history, useParams } from '@umijs/max';
import { Button, message } from 'antd';
import { ArrowLeft, Pencil } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import type { DigitalScreenBindings, DigitalScreenInstance } from './model';
import { fetchDigitalScreenDatasets } from './screen-data-service';
import { fetchDigitalScreen } from './screen-service';
import { useScreenRuntimeData } from './use-screen-data';

const EMPTY_BINDINGS: DigitalScreenBindings = {};

export default function DigitalScreenViewerPage() {
  const { id } = useParams<{ id: string }>();
  const [screen, setScreen] = useState<DigitalScreenInstance>();
  const [datasets, setDatasets] = useState<PublishedDataset[]>([]);
  const [dataError, setDataError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    void fetchDigitalScreen(id)
      .then(setScreen)
      .catch((error) => message.error(error instanceof Error ? error.message : '加载数字化大屏失败'))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    let active = true;
    setDataError('');
    void fetchDigitalScreenDatasets()
      .then((values) => {
        if (active) setDatasets(values);
      })
      .catch((error) => {
        if (!active) return;
        setDatasets([]);
        setDataError(error instanceof Error ? error.message : '加载 Dataset 失败');
      });
    return () => {
      active = false;
    };
  }, []);

  const template = useMemo(
    () => (screen ? getScreenTemplateById(screen.templateId) : undefined),
    [screen],
  );
  const runtime = useScreenRuntimeData(template, screen?.bindings ?? EMPTY_BINDINGS, datasets);

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#070b13] text-[13px] text-white/50">
        正在加载数字化大屏...
      </div>
    );
  }

  if (!screen || !template) {
    return (
      <div className="flex h-screen flex-col items-center justify-center bg-[#070b13] text-white/70">
        <div className="text-[14px]">数字化大屏或模板不存在</div>
        <Button type="link" onClick={() => history.push('/digital-screen')}>返回大屏列表</Button>
      </div>
    );
  }

  const ratio = template.width / template.height;
  const runtimeErrors = Object.values(runtime.errors);

  return (
    <div className="group relative flex h-screen w-screen items-center justify-center overflow-hidden bg-[#070b13]">
      <div
        className="max-h-screen max-w-screen"
        style={{ width: `min(100vw, calc(100vh * ${ratio}))` }}
      >
        <ScreenRenderer template={template} data={runtime.data} />
      </div>

      <div className="absolute left-4 top-4 flex items-center gap-2 opacity-0 transition-opacity group-hover:opacity-100">
        <Button
          className="border-white/10 bg-black/45 text-white backdrop-blur"
          icon={<ArrowLeft size={14} />}
          onClick={() => history.push('/digital-screen')}
        >
          返回
        </Button>
        <Button
          className="border-white/10 bg-black/45 text-white backdrop-blur"
          icon={<Pencil size={14} />}
          onClick={() => history.push(`/digital-screen/${screen.id}/edit`)}
        >
          编辑
        </Button>
      </div>

      {runtime.loadingCount ? (
        <div className="absolute right-4 top-4 rounded-[4px] bg-black/40 px-2.5 py-1.5 text-[10px] text-white/55 backdrop-blur">
          正在刷新 {runtime.loadingCount} 个组件的数据...
        </div>
      ) : null}

      {dataError || runtimeErrors.length ? (
        <div className="absolute bottom-4 left-1/2 max-w-[560px] -translate-x-1/2 rounded-[6px] bg-black/55 px-3 py-2 text-[10px] leading-5 text-white/65 opacity-0 backdrop-blur transition-opacity group-hover:opacity-100">
          {dataError || `${runtimeErrors.length} 个组件的数据查询失败：${runtimeErrors[0]}`}
        </div>
      ) : null}

      <div className="absolute bottom-3 right-4 rounded-[4px] bg-black/35 px-2 py-1 text-[10px] text-white/35 opacity-0 transition-opacity group-hover:opacity-100">
        {screen.name}
      </div>
    </div>
  );
}
