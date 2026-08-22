import {
  CheckCircleOutlined,
  CodeOutlined,
  FormatPainterOutlined,
  SaveOutlined,
  SafetyOutlined,
} from '@ant-design/icons';
import { Button, ConfigProvider, Input, message, Spin, Tag } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { BRAND_THEME } from '@/styles/brand';
import { realtimeApi } from './api';
import type { CdcPipelineSpec, RealtimeJob } from './types';

const EMPTY_TEMPLATE = `version: 1
source:
  dataSourceRef:
sink:
  dataSourceRef:
tables:
  - sourceTable: orders
    sinkTable: orders
    matchMode: EXACT
    keyColumns: [id]
sync:
  startupMode: initial
  schemaEvolution: EVOLVE
runtime:
  parallelism: 1
  checkpointIntervalMs: 60000
  restart:
    strategy: fixed-delay
    attempts: 3
    delayMs: 10000
  sink:
    maxRetries: 3
    batchSize: 1000
    flushIntervalMs: 2000
    maxBatchBytes: 16777216
    statementCacheSize: 128
    strictReplaySafety: true
`;

export default function YamlJobEditor({
  job,
  onClose,
  onSaved,
}: {
  job: RealtimeJob;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [yamlText, setYamlText] = useState('');
  const [initialLoading, setInitialLoading] = useState(true);
  const [parsing, setParsing] = useState(false);
  const [formatting, setFormatting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [validatedSpec, setValidatedSpec] = useState<CdcPipelineSpec>();
  const [validationMessage, setValidationMessage] = useState<string>();

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setInitialLoading(true);
      setValidatedSpec(undefined);
      setValidationMessage(undefined);
      try {
        if (!job.spec) {
          if (!cancelled) setYamlText(EMPTY_TEMPLATE);
          return;
        }
        const response = await realtimeApi.renderYaml(job.spec);
        if (!cancelled) setYamlText(response.data.yaml);
      } catch (error: any) {
        if (!cancelled) {
          setYamlText(EMPTY_TEMPLATE);
          message.error(error?.message || '生成 Yak Realtime YAML 失败');
        }
      } finally {
        if (!cancelled) setInitialLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [job.id, job.spec]);

  const summary = useMemo(() => {
    if (!validatedSpec) return undefined;
    const mode =
      validatedSpec.startupMode === 'initial' ? '初始化并持续同步' : '仅同步新变更';
    return `${validatedSpec.tables.length} 张表 · ${mode} · 并行度 ${validatedSpec.parallelism}`;
  }, [validatedSpec]);

  const parseCurrentYaml = async () => {
    const response = await realtimeApi.parseYaml(yamlText);
    setValidatedSpec(response.data);
    setValidationMessage('YAML 已通过解析与 CdcPipelineSpec 校验');
    return response.data;
  };

  const validate = async () => {
    setParsing(true);
    setValidationMessage(undefined);
    try {
      await parseCurrentYaml();
      message.success('YAML 校验通过');
    } catch (error: any) {
      setValidatedSpec(undefined);
      setValidationMessage(error?.message || 'YAML 校验失败');
      message.error(error?.message || 'YAML 校验失败');
    } finally {
      setParsing(false);
    }
  };

  const format = async () => {
    setFormatting(true);
    setValidationMessage(undefined);
    try {
      const spec = await parseCurrentYaml();
      const rendered = await realtimeApi.renderYaml(spec);
      setYamlText(rendered.data.yaml);
      setValidationMessage('已按 Yak Realtime YAML v1 规范格式化');
      message.success('YAML 已格式化');
    } catch (error: any) {
      setValidatedSpec(undefined);
      setValidationMessage(error?.message || 'YAML 格式化失败');
      message.error(error?.message || 'YAML 格式化失败');
    } finally {
      setFormatting(false);
    }
  };

  const save = async () => {
    setSaving(true);
    setValidationMessage(undefined);
    try {
      const spec = await parseCurrentYaml();
      await realtimeApi.update(job.id, {
        name: job.name,
        description: job.description,
        runtimeEnvironmentId: job.runtimeEnvironmentId,
        spec,
      });
      setValidationMessage('YAML 已解析为统一 Spec 并保存为实时同步草稿');
      message.success('YAML 配置已保存');
      onSaved();
    } catch (error: any) {
      setValidatedSpec(undefined);
      setValidationMessage(error?.message || '保存 YAML 配置失败');
      message.error(error?.message || '保存 YAML 配置失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ConfigProvider theme={BRAND_THEME} variant="filled">
      <div className="min-h-[calc(100vh-64px)] bg-[#f7f8fa] px-6 py-6 text-[#161823]">
        <div className="mx-auto w-full max-w-[1180px]">
          <header className="mb-5 flex items-start justify-between gap-4 rounded-xl bg-white px-7 py-6">
            <div>
              <div className="flex items-center gap-2 text-[12px] font-medium text-[var(--yak-brand-color)]">
                <CodeOutlined />
                YAML 模式 · Yak Realtime YAML v1
              </div>
              <h1 className="mb-0 mt-1 text-[20px] font-semibold text-[#101828]">{job.name}</h1>
              <div className="mt-1 text-[12px] text-[#98a2b3]">
                任务 ID：{job.id} · 运行环境 #{job.runtimeEnvironmentId}
              </div>
            </div>
            <Button disabled={saving} onClick={onClose}>返回任务列表</Button>
          </header>

          <div className="grid grid-cols-[minmax(0,1fr)_300px] gap-5 max-lg:grid-cols-1">
            <section className="overflow-hidden rounded-xl bg-white">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#eaecf0] px-5 py-4">
                <div>
                  <div className="text-[14px] font-semibold text-[#101828]">任务 YAML</div>
                  <div className="mt-1 text-[11px] text-[#98a2b3]">
                    YAML 只描述逻辑 Spec；数据源密码、主机地址和 JDBC URL 不进入配置文件。
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    size="small"
                    icon={<SafetyOutlined />}
                    loading={parsing}
                    disabled={initialLoading || saving || formatting}
                    onClick={() => void validate()}
                  >
                    校验
                  </Button>
                  <Button
                    size="small"
                    icon={<FormatPainterOutlined />}
                    loading={formatting}
                    disabled={initialLoading || saving || parsing}
                    onClick={() => void format()}
                  >
                    格式化
                  </Button>
                  <Button
                    type="primary"
                    danger
                    size="small"
                    icon={<SaveOutlined />}
                    loading={saving}
                    disabled={initialLoading || parsing || formatting}
                    onClick={() => void save()}
                  >
                    保存配置
                  </Button>
                </div>
              </div>

              {initialLoading ? (
                <div className="flex min-h-[560px] items-center justify-center">
                  <Spin tip="正在生成 YAML..." />
                </div>
              ) : (
                <Input.TextArea
                  value={yamlText}
                  spellCheck={false}
                  autoSize={false}
                  className="!min-h-[620px] !resize-none !rounded-none !border-0 !bg-[#101828] !p-5 !font-mono !text-[13px] !leading-6 !text-[#f2f4f7] focus:!shadow-none"
                  onChange={(event) => {
                    setYamlText(event.target.value);
                    setValidatedSpec(undefined);
                    setValidationMessage(undefined);
                  }}
                />
              )}
            </section>

            <aside className="space-y-5">
              <section className="rounded-xl bg-white p-5">
                <div className="text-[13px] font-semibold text-[#101828]">校验状态</div>
                <div className="mt-3 rounded-lg bg-[#f9fafb] px-4 py-3 text-[12px] leading-5 text-[#667085]">
                  {validationMessage || '编辑后点击“校验”，确认 YAML 可以转换为统一 CdcPipelineSpec。'}
                </div>
                {validatedSpec && (
                  <div className="mt-3 rounded-lg border border-[#abefc6] bg-[#ecfdf3] px-4 py-3 text-[12px] text-[#027a48]">
                    <div className="flex items-center gap-1.5 font-medium">
                      <CheckCircleOutlined /> Spec 有效
                    </div>
                    <div className="mt-1">{summary}</div>
                  </div>
                )}
              </section>

              <section className="rounded-xl bg-white p-5">
                <div className="text-[13px] font-semibold text-[#101828]">YAML v1 约定</div>
                <div className="mt-3 space-y-2 text-[12px] leading-5 text-[#667085]">
                  <div><Tag>source/sink</Tag>只填写 `dataSourceRef`。</div>
                  <div><Tag>tables</Tag>`sinkTable` 可省略，默认与 Source 同名。</div>
                  <div><Tag>matchMode</Tag>可省略，默认 `EXACT`。</div>
                  <div><Tag>sync/runtime</Tag>可省略，自动补一期稳定默认值。</div>
                  <div><Tag>keyColumns</Tag>当前必须显式声明，保证 Replay Safety。</div>
                </div>
              </section>

              <section className="rounded-xl border border-[#fedf89] bg-[#fffaeb] p-5 text-[12px] leading-5 text-[#93370d]">
                这里编辑的是 Yak Realtime YAML，不是原生 Flink CDC Pipeline YAML。最终启动时仍由现有 PipelineYamlCompiler 临时编译，并继续使用 SECRET 注入边界。
              </section>
            </aside>
          </div>
        </div>
      </div>
    </ConfigProvider>
  );
}
