import { history, useParams } from '@umijs/max';
import { message, Spin } from 'antd';
import { useEffect, useState } from 'react';
import { realtimeApi } from './api';
import JobEditor from './JobEditor';
import WizardJobEditor from './WizardJobEditor';
import YamlJobEditor from './YamlJobEditor';
import type { ComputeEnvironmentOption, DataSourceOption, RealtimeJob } from './types';

export default function RealtimeSyncDetail() {
  const { id } = useParams<{ id: string }>();
  const [job, setJob] = useState<RealtimeJob>();
  const [dataSources, setDataSources] = useState<DataSourceOption[]>([]);
  const [environments, setEnvironments] = useState<ComputeEnvironmentOption[]>([]);

  useEffect(() => {
    Promise.all([realtimeApi.detail(Number(id)), realtimeApi.dataSources(), realtimeApi.environments()])
      .then(([detail, sources, runtimeEnvironments]) => {
        setJob(detail.data);
        setDataSources(sources.data || []);
        setEnvironments(runtimeEnvironments.data || []);
      })
      .catch((error) => message.error(error?.message || '加载实时同步配置失败'));
  }, [id]);

  if (!job)
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spin />
      </div>
    );

  const editorMode = new URLSearchParams(history.location.search).get('editor');
  if (editorMode === 'yaml') {
    return <YamlJobEditor job={job} onClose={() => history.push('/sync/realtime')} />;
  }
  if (editorMode === 'wizard') {
    return (
      <WizardJobEditor
        job={job}
        dataSources={dataSources}
        onClose={() => history.push('/sync/realtime')}
        onSaved={() => history.push('/sync/realtime')}
      />
    );
  }

  return (
    <JobEditor
      open
      job={job}
      dataSources={dataSources}
      environments={environments}
      onClose={() => history.push('/sync/realtime')}
      onSaved={() => history.push('/sync/realtime')}
    />
  );
}
