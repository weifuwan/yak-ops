import { useIntl } from '@umijs/max';
import { Form, Input, Tooltip } from 'antd';
import type { FormInstance } from 'antd';
import { Link2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef } from 'react';

import type { DynamicFormJdbcUrlLinkage } from '../../types';
import {
  buildJdbcUrlFromTemplate,
  parseJdbcUrlByTemplate,
  type JdbcUrlStructuredValue,
} from './utils';

const toNamePath = (field?: string, fallback?: string) =>
  (field?.trim() || fallback || '').split('.').filter(Boolean);

const sourceSignature = (value: JdbcUrlStructuredValue) =>
  JSON.stringify([value.host || '', value.port || '', value.database || '']);

export interface JdbcUrlFieldProps {
  value?: string;
  onChange?: (value: string) => void;
  form: FormInstance;
  linkage?: DynamicFormJdbcUrlLinkage;
  placeholder?: string;
  disabled?: boolean;
}

/**
 * JDBC URL 标准联动组件。
 *
 * - Host / Port / Database 变化时自动生成 URL；
 * - URL 可被模板识别时反向回填结构化字段；
 * - 编辑历史数据时可由现有 URL 补齐结构化字段；
 * - 手工 URL 不可识别时保持原值，不强行覆盖；
 * - 自动生成时尽量保留已有 ?query / ;properties 尾部参数。
 */
const JdbcUrlField = ({
  value,
  onChange,
  form,
  linkage,
  placeholder,
  disabled = false,
}: JdbcUrlFieldProps) => {
  const intl = useIntl();
  const hostPath = useMemo(
    () => toNamePath(linkage?.hostField, 'host'),
    [linkage?.hostField],
  );
  const portPath = useMemo(
    () => toNamePath(linkage?.portField, 'port'),
    [linkage?.portField],
  );
  const databasePath = useMemo(
    () => toNamePath(linkage?.databaseField, 'database'),
    [linkage?.databaseField],
  );

  const host = Form.useWatch(hostPath, form) as string | undefined;
  const port = Form.useWatch(portPath, form) as number | undefined;
  const database = Form.useWatch(databasePath, form) as string | undefined;

  const currentValueRef = useRef(value);
  const previousSourceRef = useRef<string>();
  const ignoreSourceSignatureRef = useRef<string>();
  currentValueRef.current = value;

  const applyParsedFields = useCallback(
    (parsed: JdbcUrlStructuredValue) => {
      ignoreSourceSignatureRef.current = sourceSignature(parsed);
      form.setFields([
        { name: hostPath, value: parsed.host, errors: [] },
        { name: portPath, value: parsed.port, errors: [] },
        { name: databasePath, value: parsed.database, errors: [] },
      ]);
    },
    [databasePath, form, hostPath, portPath],
  );

  useEffect(() => {
    if (!linkage?.template) return;

    const source = { host, port, database };
    const signature = sourceSignature(source);

    if (ignoreSourceSignatureRef.current === signature) {
      ignoreSourceSignatureRef.current = undefined;
      previousSourceRef.current = signature;
      return;
    }

    if (previousSourceRef.current === undefined) {
      previousSourceRef.current = signature;
      const existingUrl = currentValueRef.current?.trim();
      if (existingUrl) {
        const parsed = parseJdbcUrlByTemplate(linkage, existingUrl);
        if (parsed && sourceSignature(parsed) !== signature) {
          applyParsedFields(parsed);
        }
        return;
      }
    } else if (previousSourceRef.current === signature) {
      return;
    } else {
      previousSourceRef.current = signature;
    }

    const currentParsed = parseJdbcUrlByTemplate(
      linkage,
      currentValueRef.current,
    );
    const nextUrl = buildJdbcUrlFromTemplate(linkage, {
      ...source,
      suffix:
        linkage.preserveSuffix === false ? undefined : currentParsed?.suffix,
    });

    if (nextUrl && nextUrl !== currentValueRef.current) {
      currentValueRef.current = nextUrl;
      onChange?.(nextUrl);
    }
  }, [
    applyParsedFields,
    database,
    host,
    linkage,
    onChange,
    port,
  ]);

  const handleChange = (nextValue: string) => {
    currentValueRef.current = nextValue;
    onChange?.(nextValue);

    if (!linkage?.template) return;
    const parsed = parseJdbcUrlByTemplate(linkage, nextValue);
    if (!parsed) return;
    applyParsedFields(parsed);
  };

  return (
    <div>
      <Input
        variant="filled"
        value={value}
        disabled={disabled}
        placeholder={placeholder}
        onChange={(event) => handleChange(event.target.value)}
        suffix={
          linkage?.template ? (
            <Tooltip
              title={intl.formatMessage({
                id: 'pages.datasource.jdbc.linkageTooltip',
              })}
            >
              <Link2 size={14} className="text-[#98a2b3]" />
            </Tooltip>
          ) : undefined
        }
      />
    </div>
  );
};

export default JdbcUrlField;
