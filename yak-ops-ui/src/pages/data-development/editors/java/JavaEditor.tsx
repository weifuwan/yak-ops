import YakButton from '@/components/YakButton';
import { useIntl } from '@umijs/max';
import { Input, Typography } from 'antd';
import { Plus, Trash2 } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';

import { FileSuffixIcon } from '@/pages/resource-management/components/FileSuffixIcon';
import ResourcePicker, { type ResourcePickerValue } from '../../components/ResourcePicker';
import {
  updateEditorSessionConfig,
  useEditorSession,
} from '../session/editorSessionStore';
import type {
  DevelopmentEditorContext,
  DevelopmentEditorRunResultContext,
} from '../types';

interface ResourceEntry {
  id: string;
  name: string;
  version?: number;
}

interface JavaTaskConfigJson {
  resources?: ResourceEntry[];
  resourceId?: string;
  resourceName?: string;
  resourceVersion?: number;
  checksum?: string;
  mainClass?: string;
  jvmArgs?: string;
  programArgs?: string;
  envVars?: Record<string, string>;
  timeoutSeconds?: number;
}

const parseConfigJson = (configJson: string): JavaTaskConfigJson => {
  try {
    return JSON.parse(configJson) as JavaTaskConfigJson;
  } catch {
    return {};
  }
};

const buildConfigJson = (config: JavaTaskConfigJson): string => {
  const cleaned: Record<string, unknown> = {};
  if (config.resources && config.resources.length > 0) {
    cleaned.resources = config.resources.map((resource) => {
      const entry: Record<string, unknown> = {
        resourceId: resource.id,
        name: resource.name,
      };
      if (resource.version != null) entry.resourceVersion = resource.version;
      return entry;
    });
  }
  if (config.mainClass) cleaned.mainClass = config.mainClass;
  if (config.jvmArgs) cleaned.jvmArgs = config.jvmArgs;
  if (config.programArgs) cleaned.programArgs = config.programArgs;
  if (config.envVars && Object.keys(config.envVars).length > 0) {
    cleaned.envVars = config.envVars;
  }
  if (config.timeoutSeconds != null) cleaned.timeoutSeconds = config.timeoutSeconds;
  return JSON.stringify(cleaned);
};

const extractSuffix = (name?: string): string | undefined => {
  if (!name) return undefined;
  const dot = name.lastIndexOf('.');
  return dot > 0 ? name.substring(dot + 1).toLowerCase() : undefined;
};

const normaliseResources = (config: JavaTaskConfigJson): ResourceEntry[] => {
  if (config.resources && config.resources.length > 0) return config.resources;
  if (config.resourceId && config.resourceId !== '' && config.resourceId !== '0') {
    return [
      {
        id: config.resourceId,
        name: config.resourceName || config.resourceId,
        version: config.resourceVersion,
      },
    ];
  }
  return [];
};

export const JavaEditor = ({ node }: DevelopmentEditorContext) => {
  const intl = useIntl();
  const session = useEditorSession(node.id, node.type);
  const config = useMemo(
    () => parseConfigJson(session.configJson || '{}'),
    [session.configJson],
  );
  const resources = useMemo(() => normaliseResources(config), [config]);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [mainClass, setMainClass] = useState(config.mainClass || '');
  const [jvmArgs, setJvmArgs] = useState(config.jvmArgs || '');
  const [programArgs, setProgramArgs] = useState(config.programArgs || '');

  const updateConfig = useCallback(
    (partial: Partial<JavaTaskConfigJson>) => {
      updateEditorSessionConfig(node.id, buildConfigJson({ ...config, ...partial }));
    },
    [config, node.id],
  );

  const handleResourceSelected = (value: ResourcePickerValue) => {
    if (resources.some((resource) => resource.id === String(value.id))) {
      setPickerOpen(false);
      return;
    }
    updateConfig({
      resources: [
        ...resources,
        { id: String(value.id), name: value.name, version: value.version },
      ],
    });
    setPickerOpen(false);
  };

  const handleRemoveResource = (index: number) => {
    updateConfig({ resources: resources.filter((_, itemIndex) => itemIndex !== index) });
  };

  const hasResources = resources.length > 0;
  const isMultiJar = resources.length > 1;

  return (
    <div className="flex h-full min-h-0 flex-col overflow-auto bg-white p-6">
      <div className="mb-6">
        <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
          <span className="mr-1 text-[rgba(254,44,85,1)]">*</span>
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.referenceJar' })}
        </Typography.Text>
        <Typography.Paragraph className="mb-3 text-[12px] text-[#98a2b3]">
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.jarDescription' })}
        </Typography.Paragraph>

        {hasResources ? (
          <div className="mb-3 space-y-2">
            {resources.map((resource, index) => (
              <div
                key={`${resource.id}-${index}`}
                className="flex items-center gap-3 rounded-lg border border-[#e4e7ec] bg-[#f9fafb] px-4 py-3"
              >
                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-[#eff8ff]">
                  <FileSuffixIcon suffix={extractSuffix(resource.name)} />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-[13px] font-medium text-[#344054]">
                    {resource.name}
                  </div>
                  <div className="text-[11px] text-[#98a2b3]">
                    {resource.version
                      ? `v${resource.version}`
                      : intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.versionLocked' })}
                  </div>
                </div>
                <YakButton
                  size="small"
                  type="text"
                  danger
                  className="!text-[#667085]"
                  icon={<Trash2 size={14} />}
                  onClick={() => handleRemoveResource(index)}
                />
              </div>
            ))}
          </div>
        ) : null}

        <YakButton
          type="text"
          icon={<Plus size={14} />}
          className="w-full !text-[#667085]"
          onClick={() => setPickerOpen(true)}
        >
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.addJar' })}
        </YakButton>
      </div>

      <div className="mb-4">
        <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.mainClass' })}
          {isMultiJar ? <span className="ml-1 text-[rgba(254,44,85,1)]">*</span> : null}
        </Typography.Text>
        <Input
          value={mainClass}
          placeholder={intl.formatMessage({
            id: isMultiJar
              ? 'pages.dataDevelopment.editor.java.mainClassMulti'
              : 'pages.dataDevelopment.editor.java.mainClassSingle',
          })}
          onChange={(event) => setMainClass(event.target.value)}
          onBlur={() => {
            if (mainClass !== (config.mainClass || '')) {
              updateConfig({ mainClass: mainClass || undefined });
            }
          }}
        />
      </div>

      <div className="mb-4">
        <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.jvmArgs' })}
        </Typography.Text>
        <Input
          value={jvmArgs}
          placeholder="-Xmx512m -Dfile.encoding=UTF-8"
          onChange={(event) => setJvmArgs(event.target.value)}
          onBlur={() => {
            if (jvmArgs !== (config.jvmArgs || '')) {
              updateConfig({ jvmArgs: jvmArgs || undefined });
            }
          }}
        />
      </div>

      <div className="mb-4">
        <Typography.Text className="mb-2 block text-[13px] font-medium text-[#344054]">
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.programArgs' })}
        </Typography.Text>
        <Input
          value={programArgs}
          placeholder="--env production --config /path/to/config.yaml"
          onChange={(event) => setProgramArgs(event.target.value)}
          onBlur={() => {
            if (programArgs !== (config.programArgs || '')) {
              updateConfig({ programArgs: programArgs || undefined });
            }
          }}
        />
      </div>

      <ResourcePicker
        open={pickerOpen}
        acceptSuffixes={['.jar']}
        onCancel={() => setPickerOpen(false)}
        onConfirm={handleResourceSelected}
      />
    </div>
  );
};

export const JavaRunConfig = ({ node }: DevelopmentEditorContext) => {
  const intl = useIntl();
  return (
    <div className="text-[12px] leading-6 text-[#667085]">
      <div className="font-medium text-[#344054]">
        {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.runConfig' })}
      </div>
      <div className="mt-2">
        {intl.formatMessage(
          { id: 'pages.dataDevelopment.editor.java.currentNode' },
          { name: node.name },
        )}
      </div>
      <div className="mt-3 border-t border-[#eef0f2] pt-3 text-[11px] leading-5 text-[#98a2b3]">
        <div>{intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.runtimeHint' })}</div>
        <div className="mt-2">
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.configHint' })}
        </div>
      </div>
    </div>
  );
};

export const JavaRunResult = ({ result }: DevelopmentEditorRunResultContext) => {
  const intl = useIntl();
  if (!result) {
    return (
      <div className="flex h-full items-center justify-center text-center">
        <div>
          <div className="text-[13px] font-medium text-[#475467]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.runResult' })}
          </div>
          <div className="mt-1 text-[11px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.runHint' })}
          </div>
        </div>
      </div>
    );
  }

  if (result.status === 'RUNNING') {
    return (
      <div className="flex h-full items-center justify-center text-[12px] text-[#667085]">
        {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.running' })}
      </div>
    );
  }

  if (result.status !== 'SUCCESS') {
    const statusId =
      result.status === 'CANCELLED'
        ? 'pages.dataDevelopment.editor.java.cancelled'
        : result.status === 'TIMEOUT'
          ? 'pages.dataDevelopment.editor.java.timeout'
          : 'pages.dataDevelopment.editor.java.failed';
    return (
      <div className="flex h-full items-center justify-center px-6 text-center">
        <div className="max-w-[680px]">
          <div className="text-[13px] font-medium text-[#b42318]">
            {intl.formatMessage({ id: statusId })}
          </div>
          <div className="mt-2 break-words text-[11px] leading-5 text-[#667085]">
            {result.message || intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.noError' })}
          </div>
          {result.output?.stderr ? (
            <pre className="mt-3 max-h-[240px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#f9fafb] p-3 text-left font-mono text-[11px] text-[#344054]">
              {String(result.output.stderr)}
            </pre>
          ) : null}
          <div className="mt-2 text-[10px] text-[#98a2b3]">
            {intl.formatMessage(
              { id: 'pages.dataDevelopment.editor.java.duration' },
              { duration: result.durationMs },
            )}
          </div>
        </div>
      </div>
    );
  }

  const output = result.output || {};
  const stdout = output.stdout ? String(output.stdout) : '';
  const stderr = output.stderr ? String(output.stderr) : '';
  const exitCode = output.exitCode != null ? String(output.exitCode) : '—';

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex shrink-0 items-center justify-between border-b border-[#eef0f2] bg-[#fafafa] px-3 py-1.5">
        <span className="text-[12px] font-medium text-[#344054]">
          {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.completed' })}
        </span>
        <span className="text-[10px] text-[#98a2b3]">
          {intl.formatMessage(
            { id: 'pages.dataDevelopment.editor.java.exitDuration' },
            { exitCode, duration: result.durationMs },
          )}
        </span>
      </div>
      <div className="min-h-0 flex-1 overflow-auto p-3">
        {stdout ? (
          <div className="mb-3">
            <div className="mb-1 text-[11px] font-medium text-[#475467]">stdout</div>
            <pre className="max-h-[320px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#f9fafb] p-3 font-mono text-[11px] leading-5 text-[#344054]">
              {stdout}
            </pre>
          </div>
        ) : null}
        {stderr ? (
          <div>
            <div className="mb-1 text-[11px] font-medium text-[#475467]">stderr</div>
            <pre className="max-h-[160px] overflow-auto whitespace-pre-wrap break-words rounded-md bg-[#fef3f2] p-3 font-mono text-[11px] leading-5 text-[#b42318]">
              {stderr}
            </pre>
          </div>
        ) : null}
        {!stdout && !stderr ? (
          <div className="text-[12px] text-[#98a2b3]">
            {intl.formatMessage({ id: 'pages.dataDevelopment.editor.java.noOutput' })}
          </div>
        ) : null}
      </div>
    </div>
  );
};
