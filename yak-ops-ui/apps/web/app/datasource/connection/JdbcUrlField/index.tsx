import {
  Input,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@yak-ops/yak-ui";
import { Link2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef } from "react";

import type { DataSourceFormInstance } from "../../editor/formRuntime";
import { useFormValue } from "../../editor/formRuntime";
import { useIntl } from "../../i18n";
import type { DynamicFormJdbcUrlLinkage } from "../../model/types";
import {
  buildJdbcUrlFromTemplate,
  parseJdbcUrlByTemplate,
  type JdbcUrlStructuredValue,
} from "./utils";

const toNamePath = (field?: string, fallback?: string) =>
  (field?.trim() || fallback || "").split(".").filter(Boolean);

const sourceSignature = (value: JdbcUrlStructuredValue) =>
  JSON.stringify([value.host || "", value.port || "", value.database || ""]);

export interface JdbcUrlFieldProps {
  value?: string;
  onChange?: (value: string) => void;
  form: DataSourceFormInstance;
  linkage?: DynamicFormJdbcUrlLinkage;
  placeholder?: string;
  disabled?: boolean;
}

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
    () => toNamePath(linkage?.hostField, "host"),
    [linkage?.hostField],
  );
  const portPath = useMemo(
    () => toNamePath(linkage?.portField, "port"),
    [linkage?.portField],
  );
  const databasePath = useMemo(
    () => toNamePath(linkage?.databaseField, "database"),
    [linkage?.databaseField],
  );

  const host = useFormValue(hostPath, form) as string | undefined;
  const port = useFormValue(portPath, form) as number | undefined;
  const database = useFormValue(databasePath, form) as string | undefined;

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
  }, [applyParsedFields, database, host, linkage, onChange, port]);

  const handleChange = (nextValue: string) => {
    currentValueRef.current = nextValue;
    onChange?.(nextValue);

    if (!linkage?.template) return;
    const parsed = parseJdbcUrlByTemplate(linkage, nextValue);
    if (parsed) applyParsedFields(parsed);
  };

  return (
    <div className="relative">
      <Input
        value={value}
        disabled={disabled}
        placeholder={placeholder}
        className={linkage?.template ? "pr-9" : undefined}
        onChange={(event) => handleChange(event.target.value)}
      />
      {linkage?.template ? (
        <Tooltip>
          <TooltipTrigger
            aria-label={intl.formatMessage({
              id: "pages.datasource.jdbc.linkageTooltip",
            })}
            className="absolute right-2 top-1/2 inline-flex -translate-y-1/2 cursor-help text-[#98a2b3]"
          >
            <Link2 size={14} />
          </TooltipTrigger>
          <TooltipContent>
            {intl.formatMessage({
              id: "pages.datasource.jdbc.linkageTooltip",
            })}
          </TooltipContent>
        </Tooltip>
      ) : null}
    </div>
  );
};

export default JdbcUrlField;
