import { ScreenRenderer, getScreenTemplateById } from '@/components/screen-engine';
import { history, useParams } from '@umijs/max';
import { Button, message } from 'antd';
import { ArrowLeft, Pencil } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import type { DigitalScreenInstance } from './model';
import { fetchDigitalScreen } from './screen-service';

export default function DigitalScreenViewerPage() {
  const { id } = useParams<{ id: string }>();
  const [screen, setScreen] = useState<DigitalScreenInstance>();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    void fetchDigitalScreen(id)
      .then(setScreen)
      .catch((error) => message.error(error instanceof Error ? error.message : '加载数字化大屏失败'))
      .finally(() => setLoading(false));
  }, [id]);

  const template = useMemo(
    () => (screen ? getScreenTemplateById(screen.templateId) : undefined),
    [screen],
  );

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

  return (
    <div className="group relative flex h-screen w-screen items-center justify-center overflow-hidden bg-[#070b13]">
      <div
        className="max-h-screen max-w-screen"
        style={{ width: `min(100vw, calc(100vh * ${ratio}))` }}
      >
        <ScreenRenderer template={template} />
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

      <div className="absolute bottom-3 right-4 rounded-[4px] bg-black/35 px-2 py-1 text-[10px] text-white/35 opacity-0 transition-opacity group-hover:opacity-100">
        {screen.name}
      </div>
    </div>
  );
}
