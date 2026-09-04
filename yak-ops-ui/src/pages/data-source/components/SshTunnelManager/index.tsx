import { useIntl } from '@umijs/max';
import { Input, InputNumber, Select, Switch } from 'antd';
import { KeyRound, Network, ShieldCheck } from 'lucide-react';

import type { SshTunnelConfigValue } from '../../types';

interface IntlFormatter {
  formatMessage: (descriptor: { id: string }) => string;
}

const DEFAULT_VALUE: SshTunnelConfigValue = {
  enabled: false,
  host: '',
  port: 22,
  username: '',
  authType: 'PASSWORD',
  password: '',
  privateKey: '',
  passphrase: '',
  strictHostKeyChecking: false,
  knownHosts: '',
};

export interface SshTunnelManagerProps {
  value?: SshTunnelConfigValue;
  onChange?: (value: SshTunnelConfigValue) => void;
  disabled?: boolean;
}

export const getSshTunnelValidationMessage = (
  value: SshTunnelConfigValue | undefined,
  intl: IntlFormatter,
): string | undefined => {
  if (!value?.enabled) return undefined;
  if (!value.host?.trim()) {
    return intl.formatMessage({ id: 'pages.datasource.ssh.validation.host' });
  }
  if (!value.port || value.port < 1 || value.port > 65535) {
    return intl.formatMessage({ id: 'pages.datasource.ssh.validation.port' });
  }
  if (!value.username?.trim()) {
    return intl.formatMessage({ id: 'pages.datasource.ssh.validation.username' });
  }

  const authType = value.authType || 'PASSWORD';
  if (authType === 'PASSWORD' && !value.password) {
    return intl.formatMessage({ id: 'pages.datasource.ssh.validation.password' });
  }
  if (authType === 'PRIVATE_KEY' && !value.privateKey?.trim()) {
    return intl.formatMessage({ id: 'pages.datasource.ssh.validation.privateKey' });
  }
  if (value.strictHostKeyChecking && !value.knownHosts?.trim()) {
    return intl.formatMessage({ id: 'pages.datasource.ssh.validation.knownHosts' });
  }
  return undefined;
};

/**
 * 标准 SSH 隧道配置组件。
 *
 * 仅通过 value/onChange 与表单交互，不依赖 FormInstance 或特定字段 key，
 * 可以被动态 Schema、普通表单及后续独立连接配置复用。
 */
const SshTunnelManager = ({
  value,
  onChange,
  disabled = false,
}: SshTunnelManagerProps) => {
  const intl = useIntl();
  const current: SshTunnelConfigValue = {
    ...DEFAULT_VALUE,
    ...(value || {}),
  };

  const patch = (next: Partial<SshTunnelConfigValue>) => {
    onChange?.({
      ...current,
      ...next,
    });
  };

  return (
    <div className="overflow-hidden rounded-lg border border-[#e8eaed] bg-white">
      <div className="flex items-center justify-between gap-4 px-3.5 py-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-[#f5f6f7] text-[#667085]">
            <Network size={15} />
          </span>
          <div className="min-w-0">
            <div className="text-[13px] font-medium leading-5 text-[#344054]">
              {intl.formatMessage({ id: 'pages.datasource.ssh.enable' })}
            </div>
            <div className="text-[11px] leading-4 text-[#98a2b3]">
              {intl.formatMessage({ id: 'pages.datasource.ssh.description' })}
            </div>
          </div>
        </div>
        <Switch
          size="small"
          checked={current.enabled}
          disabled={disabled}
          onChange={(enabled) => patch({ enabled })}
        />
      </div>

      {current.enabled && (
        <div className="border-t border-[#eef0f3] bg-[#fcfcfd] px-3.5 py-3.5">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
            <div>
              <div className="mb-1.5 text-xs font-medium text-[#475467]">
                {intl.formatMessage({ id: 'pages.datasource.ssh.host' })}
              </div>
              <Input
                variant="filled"
                value={current.host}
                disabled={disabled}
                placeholder={intl.formatMessage({
                  id: 'pages.datasource.ssh.hostPlaceholder',
                })}
                onChange={(event) => patch({ host: event.target.value })}
              />
            </div>

            <div>
              <div className="mb-1.5 text-xs font-medium text-[#475467]">
                {intl.formatMessage({ id: 'pages.datasource.ssh.port' })}
              </div>
              <InputNumber
                variant="filled"
                className="!w-full"
                min={1}
                max={65535}
                value={current.port}
                disabled={disabled}
                placeholder="22"
                onChange={(port) => patch({ port: port ?? 22 })}
              />
            </div>

            <div>
              <div className="mb-1.5 text-xs font-medium text-[#475467]">
                {intl.formatMessage({ id: 'pages.datasource.ssh.username' })}
              </div>
              <Input
                variant="filled"
                value={current.username}
                disabled={disabled}
                placeholder={intl.formatMessage({
                  id: 'pages.datasource.ssh.usernamePlaceholder',
                })}
                onChange={(event) => patch({ username: event.target.value })}
              />
            </div>

            <div>
              <div className="mb-1.5 text-xs font-medium text-[#475467]">
                {intl.formatMessage({ id: 'pages.datasource.ssh.authType' })}
              </div>
              <Select
                variant="filled"
                className="w-full"
                value={current.authType}
                disabled={disabled}
                options={[
                  {
                    label: intl.formatMessage({
                      id: 'pages.datasource.ssh.passwordAuth',
                    }),
                    value: 'PASSWORD',
                  },
                  {
                    label: intl.formatMessage({
                      id: 'pages.datasource.ssh.privateKeyAuth',
                    }),
                    value: 'PRIVATE_KEY',
                  },
                ]}
                onChange={(authType) => patch({ authType })}
              />
            </div>
          </div>

          {current.authType === 'PRIVATE_KEY' ? (
            <div className="mt-3 grid grid-cols-1 gap-3">
              <div>
                <div className="mb-1.5 flex items-center gap-1.5 text-xs font-medium text-[#475467]">
                  <KeyRound size={13} />
                  {intl.formatMessage({ id: 'pages.datasource.ssh.privateKey' })}
                </div>
                <Input.TextArea
                  variant="filled"
                  rows={4}
                  value={current.privateKey}
                  disabled={disabled}
                  placeholder={intl.formatMessage({
                    id: 'pages.datasource.ssh.privateKeyPlaceholder',
                  })}
                  className="font-mono text-xs"
                  onChange={(event) => patch({ privateKey: event.target.value })}
                />
              </div>
              <div>
                <div className="mb-1.5 text-xs font-medium text-[#475467]">
                  {intl.formatMessage({ id: 'pages.datasource.ssh.passphrase' })}
                </div>
                <Input.Password
                  variant="filled"
                  value={current.passphrase}
                  disabled={disabled}
                  placeholder={intl.formatMessage({
                    id: 'pages.datasource.ssh.passphrasePlaceholder',
                  })}
                  onChange={(event) => patch({ passphrase: event.target.value })}
                />
              </div>
            </div>
          ) : (
            <div className="mt-3">
              <div className="mb-1.5 text-xs font-medium text-[#475467]">
                {intl.formatMessage({ id: 'pages.datasource.ssh.password' })}
              </div>
              <Input.Password
                variant="filled"
                value={current.password}
                disabled={disabled}
                placeholder={intl.formatMessage({
                  id: 'pages.datasource.ssh.passwordPlaceholder',
                })}
                onChange={(event) => patch({ password: event.target.value })}
              />
            </div>
          )}

          <div className="mt-3 rounded-md border border-[#eaecf0] bg-white px-3 py-2.5">
            <div className="flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <ShieldCheck size={14} className="text-[#667085]" />
                <div>
                  <div className="text-xs font-medium text-[#475467]">
                    {intl.formatMessage({
                      id: 'pages.datasource.ssh.strictHostKeyChecking',
                    })}
                  </div>
                  <div className="text-[11px] leading-4 text-[#98a2b3]">
                    {intl.formatMessage({
                      id: 'pages.datasource.ssh.strictHostKeyCheckingDescription',
                    })}
                  </div>
                </div>
              </div>
              <Switch
                size="small"
                checked={current.strictHostKeyChecking}
                disabled={disabled}
                onChange={(strictHostKeyChecking) =>
                  patch({ strictHostKeyChecking })
                }
              />
            </div>

            {current.strictHostKeyChecking && (
              <div className="mt-2.5 border-t border-[#f0f1f3] pt-2.5">
                <Input.TextArea
                  variant="filled"
                  rows={3}
                  value={current.knownHosts}
                  disabled={disabled}
                  placeholder={intl.formatMessage({
                    id: 'pages.datasource.ssh.knownHostsPlaceholder',
                  })}
                  className="font-mono text-xs"
                  onChange={(event) => patch({ knownHosts: event.target.value })}
                />
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default SshTunnelManager;
