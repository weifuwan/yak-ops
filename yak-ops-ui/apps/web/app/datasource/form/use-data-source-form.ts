import { useEffect, useState } from "react";
import { toast } from "@yak-ops/yak-ui";
import { useIntl } from "../i18n";
import { normalizeDataSourceType } from "../constants";
import { parseOracleJdbcUrl } from "@/service/datasource/adapter";
import {
  createDataSource,
  updateDataSource,
  testDataSourceConnectionWithParams,
  testSavedDataSourceConnection,
  datasourceValidationErrors,
  getDataSourceConnectionPropertyKeys,
  getDataSourceOptionRules,
  type DataSourceConnectionParams,
  type DataSourceSavePayload,
  type DataSourceOptionRules,
  type DataSourceDriverProperty,
} from "@/service/datasource";
import {
  listUserPreferences,
  recordUserPreferenceUsage,
  type UserPreferenceRecord,
} from "@/service/preference";
import {
  DEFAULT_HOST,
  DATASOURCE_CREATE_TYPE_SCENE,
  EMPTY_FORM,
  defaultPort,
  parseOriginalJson,
  upsertPreference,
  normalizePropertyKey,
  type DataSourceFormProps,
  type FormValues,
  type FormErrors,
  type FormErrorKey,
  type CreateStep,
  type CreateCategory,
  type JdbcProperty,
} from "./model";

export function useDataSourceForm({ open, record, onOpenChange, onSaved }: DataSourceFormProps) {
  const intl = useIntl();
  const [values, setValues] = useState<FormValues>(EMPTY_FORM);
  const [errors, setErrors] = useState<FormErrors>({});
  const [testing, setTesting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [createStep, setCreateStep] = useState<CreateStep>("select");
  const [createSearch, setCreateSearch] = useState("");
  const [createCategory, setCreateCategory] = useState<CreateCategory>("ALL");
  const [frequentTypePreferences, setFrequentTypePreferences] = useState<UserPreferenceRecord[]>(
    [],
  );
  const [propertyKeyOptions, setPropertyKeyOptions] = useState<string[]>([]);
  const [driverPropertyMetadata, setDriverPropertyMetadata] = useState<DataSourceDriverProperty[]>([]);
  const [optionRules, setOptionRules] = useState<DataSourceOptionRules | null>(null);
  const [propertyKeysLoading, setPropertyKeysLoading] = useState(false);
  const editing = Boolean(record?.id);
  const busy = testing || submitting;
  const isOracle = normalizeDataSourceType(values.dbType) === "ORACLE";
  const isSSH = normalizeDataSourceType(values.dbType) === "SSH";
  const isOceanBase = normalizeDataSourceType(values.dbType) === "OCEANBASE";

  useEffect(() => {
    if (!open) return;
    const dbType = normalizeDataSourceType(record?.dbType) || "MYSQL";
    const original = parseOriginalJson(record);
    setValues({
      ...EMPTY_FORM,
      name: record?.name || "",
      dbType,
      jdbcUrl: original.jdbcUrl || "",
      host: original.host || DEFAULT_HOST,
      port: original.port || defaultPort(dbType),
      database: original.database || "",
      username: original.username || "",
      password: original.password || "",
      driverId: original.driverId || "AUTO",
      compatibleMode: original.compatibleMode || "mysql",
      privateKey: "",
      properties: original.properties || [],
      remark: record?.remark || "",
    });
    setErrors({});
    setCreateStep(record?.id ? "config" : "select");
    if (!record?.id) {
      setCreateSearch("");
      setCreateCategory("ALL");
      setFrequentTypePreferences([]);
    }
  }, [open, record]);

  useEffect(() => {
    if (!open || editing || createStep !== "select") return;

    let active = true;
    void listUserPreferences(DATASOURCE_CREATE_TYPE_SCENE)
      .then((preferences) => {
        if (active) setFrequentTypePreferences(preferences);
      })
      .catch(() => undefined);

    return () => {
      active = false;
    };
  }, [createStep, editing, open]);

  useEffect(() => {
    if (
      !open ||
      createStep !== "config" ||
      !values.dbType ||
      normalizeDataSourceType(values.dbType) === "ORACLE"
    ) {
      setPropertyKeyOptions([]);
      setDriverPropertyMetadata([]);
      setPropertyKeysLoading(false);
      return;
    }

    let active = true;
    setPropertyKeysLoading(true);
    void getDataSourceConnectionPropertyKeys(values.dbType)
      .then((result) => {
        if (active) {
          setPropertyKeyOptions(result?.acceptedPropertyKeys || []);
          setDriverPropertyMetadata(result?.properties || []);
        }
      })
      .catch(() => {
        if (active) {
          setPropertyKeyOptions([]);
          setDriverPropertyMetadata([]);
        }
      })
      .finally(() => {
        if (active) setPropertyKeysLoading(false);
      });

    return () => {
      active = false;
    };
  }, [createStep, open, values.dbType]);

  useEffect(() => {
    if (!open || createStep !== "config" || !values.dbType) {
      setOptionRules(null);
      return;
    }
    let active = true;
    setOptionRules(null);
    void getDataSourceOptionRules(values.dbType)
      .then((rules) => {
        if (!active) return;
        setOptionRules(rules);
        const declaredPort = rules.options.find((option) => option.key === "port")?.defaultValue;
        if (typeof declaredPort !== "number") return;
        setValues((current) => {
          if (normalizeDataSourceType(current.dbType) !== normalizeDataSourceType(rules.dbType)) {
            return current;
          }
          if (current.port !== defaultPort(current.dbType)) return current;
          return { ...current, port: String(declaredPort) };
        });
      })
      .catch(() => {
        if (active) setOptionRules(null);
      });
    return () => {
      active = false;
    };
  }, [createStep, open, values.dbType]);

  const applyValidationErrors = (error: unknown) => {
    const reported = datasourceValidationErrors(error);
    if (!reported.length) return;
    const next: FormErrors = {};
    const fields = new Set<FormErrorKey>([
      "jdbcUrl",
      "host",
      "port",
      "database",
      "username",
      "properties",
    ]);
    for (const issue of reported) {
      if (!issue || typeof issue.path !== "string" || typeof issue.rule !== "string") continue;
      const field = issue.path.split(",")[0] as FormErrorKey;
      if (fields.has(field) && !next[field]) {
        next[field] =
          issue.rule === "REQUIRED" || issue.rule === "CONDITIONAL"
            ? intl.formatMessage({ id: "pages.datasource.form.parameterRequired" })
            : intl.formatMessage({ id: "pages.datasource.form.parameterInvalid" });
      }
    }
    if (Object.keys(next).length) setErrors((current) => ({ ...current, ...next }));
  };

  const patch = <K extends keyof FormValues>(key: K, value: FormValues[K]) => {
    setValues((current) => ({ ...current, [key]: value }));
    if (key in errors) {
      setErrors((current) => ({ ...current, [key]: undefined }));
    }
  };

  const patchProperty = (index: number, key: keyof JdbcProperty, value: string) => {
    setValues((current) => ({
      ...current,
      properties: current.properties.map((property, propertyIndex) =>
        propertyIndex === index ? { ...property, [key]: value } : property,
      ),
    }));
    setErrors((current) => ({ ...current, properties: undefined }));
  };

  const propertyKeyMap = new Map(
    propertyKeyOptions.map((key) => [normalizePropertyKey(key), key] as const),
  );

  const selectedSuggestedPropertyKeys = values.properties.flatMap((property) => {
    const key = propertyKeyMap.get(normalizePropertyKey(property.key));
    return key ? [key] : [];
  });

  const syncSuggestedProperties = (selectedKeys: string[]) => {
    setValues((current) => {
      const currentByKey = new Map(
        current.properties.map(
          (property) => [normalizePropertyKey(property.key), property] as const,
        ),
      );
      const customProperties = current.properties.filter(
        (property) => !propertyKeyMap.has(normalizePropertyKey(property.key)),
      );
      const suggestedProperties = selectedKeys.map(
        (key) => currentByKey.get(normalizePropertyKey(key)) || { key, value: "" },
      );
      return {
        ...current,
        properties: [...suggestedProperties, ...customProperties],
      };
    });
    setErrors((current) => ({ ...current, properties: undefined }));
  };

  const addProperty = () => {
    setValues((current) => ({
      ...current,
      properties: [...current.properties, { key: "", value: "" }],
    }));
    setErrors((current) => ({ ...current, properties: undefined }));
  };

  const removeProperty = (index: number) => {
    setValues((current) => ({
      ...current,
      properties: current.properties.filter((_, propertyIndex) => propertyIndex !== index),
    }));
    setErrors((current) => ({ ...current, properties: undefined }));
  };

  const validate = () => {
    const next: FormErrors = {};
    const port = Number(values.port);
    if (!values.name.trim())
      next.name = intl.formatMessage({ id: "pages.datasource.form.dsNameRequired" });
    if (!values.dbType)
      next.dbType = intl.formatMessage({ id: "pages.datasource.form.dbTypeRequired" });

    if (isOracle) {
      if (!values.jdbcUrl.trim())
        next.jdbcUrl = intl.formatMessage({ id: "pages.datasource.form.jdbcUrlRequired" });
      else {
        try {
          parseOracleJdbcUrl(values.jdbcUrl);
        } catch {
          next.jdbcUrl = intl.formatMessage({ id: "pages.datasource.form.jdbcUrlInvalid" });
        }
      }
    } else {
      if (!values.host.trim())
        next.host = intl.formatMessage({ id: "pages.datasource.form.hostRequired" });
      if (!values.port.trim())
        next.port = intl.formatMessage({ id: "pages.datasource.form.portRequired" });
      else if (!Number.isInteger(port) || port < 1 || port > 65535)
        next.port = intl.formatMessage({ id: "pages.datasource.form.portInvalid" });
      if (!isSSH && !values.database.trim())
        next.database = intl.formatMessage({ id: "pages.datasource.form.databaseRequired" });

      const propertyKeys = values.properties.map((property) => property.key.trim());
      const normalizedPropertyKeys = propertyKeys.map(normalizePropertyKey);
      if (propertyKeys.some((key) => !key)) {
        next.properties = intl.formatMessage({ id: "pages.datasource.form.propertyKeyRequired" });
      } else if (new Set(normalizedPropertyKeys).size !== normalizedPropertyKeys.length) {
        next.properties = intl.formatMessage({ id: "pages.datasource.form.propertyKeyDuplicate" });
      }
    }

    if (!values.username.trim())
      next.username = intl.formatMessage({ id: "pages.datasource.form.usernameRequired" });
    if (values.name.length > 128)
      next.name = intl.formatMessage({ id: "pages.datasource.form.dsNameMax" });
    if (values.remark.length > 500)
      next.remark = intl.formatMessage({ id: "pages.datasource.form.descriptionMax" });

    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const connectionParams = (): DataSourceConnectionParams => {
    const properties = Object.fromEntries(
      values.properties.map((property) => [property.key.trim(), property.value]),
    );
    if (isOracle) {
      return {
        jdbcUrl: values.jdbcUrl.trim(),
        username: values.username.trim(),
        password: values.password,
        properties,
      };
    }
    return {
      host: values.host.trim(),
      port: Number(values.port),
      database: isSSH ? "" : values.database.trim(),
      username: values.username.trim(),
      password: values.password,
      ...(normalizeDataSourceType(values.dbType) === "MYSQL" ? { driverId: values.driverId } : {}),
      ...(isOceanBase ? { compatibleMode: values.compatibleMode } : {}),
      ...(isSSH ? { privateKey: values.privateKey } : {}),
      properties,
    };
  };

  const handleTest = async () => {
    if (busy || !validate()) return;
    setTesting(true);
    try {
      const connected =
        record?.id && !values.password.trim()
          ? await testSavedDataSourceConnection(record.id)
          : await testDataSourceConnectionWithParams({
              dataSourceId: record?.id,
              dbType: values.dbType,
              connectionParams: connectionParams(),
            });
      if (connected) toast.success(intl.formatMessage({ id: "pages.datasource.test.success" }));
    } catch (error) {
      applyValidationErrors(error);
      // Preserve the dialog and the user's input after a rejected connection test.
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async () => {
    if (busy || !validate()) return;
    setSubmitting(true);
    try {
      const payload: DataSourceSavePayload = {
        name: values.name.trim(),
        dbType: values.dbType,
        environment: record?.environment || "DEVELOP",
        remark: values.remark.trim() || undefined,
        connectionParams: connectionParams(),
      };

      if (record?.id) await updateDataSource(record.id, payload);
      else await createDataSource(payload);

      toast.success(
        intl.formatMessage({
          id: record?.id
            ? "pages.datasource.modal.message.updateSuccess"
            : "pages.datasource.modal.message.createSuccess",
        }),
      );
      onOpenChange(false);
      onSaved();
    } catch (error) {
      applyValidationErrors(error);
    } finally {
      setSubmitting(false);
    }
  };

  const handleSelectType = (dbType: string) => {
    const normalizedType = normalizeDataSourceType(dbType);
    void recordUserPreferenceUsage(DATASOURCE_CREATE_TYPE_SCENE, normalizedType)
      .then((preference) => {
        setFrequentTypePreferences((current) => upsertPreference(current, preference));
      })
      .catch(() => undefined);
    setValues((current) =>
      current.dbType === normalizedType
        ? current
        : {
            ...current,
            dbType: normalizedType,
            jdbcUrl: "",
            host: DEFAULT_HOST,
            port: defaultPort(normalizedType),
            database: "",
            username: "",
            password: "",
            driverId: "AUTO",
            compatibleMode: "mysql",
            privateKey: "",
            properties: [],
          },
    );
    setErrors({});
    setCreateStep("config");
  };
  return {
    intl,
    values,
    errors,
    editing,
    busy,
    isOracle,
    isSSH,
    isOceanBase,
    testing,
    submitting,
    createStep,
    setCreateStep,
    createSearch,
    setCreateSearch,
    createCategory,
    setCreateCategory,
    frequentTypePreferences,
    propertyKeyOptions,
    driverPropertyMetadata,
    propertyKeysLoading,
    optionRules,
    propertyKeyMap,
    selectedSuggestedPropertyKeys,
    syncSuggestedProperties,
    patchProperty,
    addProperty,
    removeProperty,
    patch,
    handleTest,
    handleSubmit,
    handleSelectType,
  };
}
