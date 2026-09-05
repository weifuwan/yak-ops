import {
  getDataSourcePluginConfig,
  installDataSourcePlugin,
} from '@/services/data-source';
import type { FormInstance } from 'antd';
import {
  useCallback,
  useEffect,
  useMemo,
  useReducer,
  useRef,
} from 'react';

import type { DynamicFormField } from '../../../types';
import {
  flattenFormSectionFields,
  getConfigInitialValues,
  normalizeConfigValuesForForm,
  normalizeFormSections,
  patchEmptyWithDefaults,
} from '../utils/formUtils';
import {
  INITIAL_PLUGIN_CONFIG_STATE,
  PLUGIN_CONFIG_STATUS,
  pluginConfigStateReducer,
} from './pluginConfigState';

interface IntlFormatter {
  formatMessage: (descriptor: { id: string }) => string;
}

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback;

/** 数据源插件配置统一生命周期。 */
export function usePluginFormConfig(params: {
  dbType: string;
  configForm: FormInstance;
  initialConfig?: Record<string, unknown>;
  /** 主编辑器切换数据源类型时需要清空旧配置。 */
  resetOnLoad?: boolean;
  intl: IntlFormatter;
}) {
  const {
    dbType,
    configForm,
    initialConfig,
    resetOnLoad = false,
    intl,
  } = params;
  const [state, dispatch] = useReducer(
    pluginConfigStateReducer,
    INITIAL_PLUGIN_CONFIG_STATE,
  );
  const requestSequenceRef = useRef(0);

  const loadFormConfig = useCallback(async () => {
    if (!dbType) {
      requestSequenceRef.current += 1;
      dispatch({ type: 'RESET' });
      if (resetOnLoad) configForm.resetFields();
      return false;
    }

    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    dispatch({ type: 'LOAD_START' });
    if (resetOnLoad) configForm.resetFields();

    try {
      const data = await getDataSourcePluginConfig(dbType);
      if (requestSequence !== requestSequenceRef.current) return false;

      if (data.installRequired) {
        dispatch({
          type: 'INSTALL_REQUIRED',
          message:
            data.installHint ||
            intl.formatMessage({
              id: 'pages.datasource.plugin.installRequiredTitle',
            }),
        });
        return false;
      }

      const sections = normalizeFormSections(data || { formFields: [] }).map(
        (section) => ({
          ...section,
          title:
            section.key === 'connection' || section.title === '连接参数'
              ? intl.formatMessage({
                  id: 'pages.datasource.form.connectionParams',
                })
              : section.title,
          fields: section.fields.map((field) => ({
            ...field,
            placeholder:
              field.key === 'properties' && field.type === 'CUSTOM_SELECT'
                ? intl.formatMessage({
                    id: 'pages.datasource.form.propertiesPlaceholder',
                  })
                : field.placeholder,
          })),
        }),
      );
      const fields = flattenFormSectionFields(sections);
      const defaults = getConfigInitialValues(fields);

      if (resetOnLoad) {
        configForm.setFieldsValue({
          ...defaults,
          ...normalizeConfigValuesForForm(fields, initialConfig),
        });
      } else {
        const current = normalizeConfigValuesForForm(
          fields,
          configForm.getFieldsValue(true),
        );
        const patch = patchEmptyWithDefaults(current, defaults);
        configForm.setFieldsValue({ ...current, ...patch });
      }

      dispatch({ type: 'LOAD_SUCCESS', sections });
      return true;
    } catch (error) {
      if (requestSequence !== requestSequenceRef.current) return false;
      dispatch({
        type: 'LOAD_FAILED',
        message: errorMessage(
          error,
          intl.formatMessage({
            id: 'pages.datasource.plugin.loadFailedFallback',
          }),
        ),
      });
      return false;
    }
  }, [configForm, dbType, initialConfig, intl, resetOnLoad]);

  const installPlugin = useCallback(async () => {
    if (!dbType || state.status === PLUGIN_CONFIG_STATUS.INSTALLING) {
      return false;
    }

    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    dispatch({ type: 'INSTALL_START' });

    try {
      await installDataSourcePlugin(dbType);
      if (requestSequence !== requestSequenceRef.current) return false;

      await loadFormConfig();
      return true;
    } catch (error) {
      if (requestSequence !== requestSequenceRef.current) return false;
      dispatch({
        type: 'INSTALL_FAILED',
        message: errorMessage(
          error,
          intl.formatMessage({
            id: 'pages.datasource.plugin.installFailedFallback',
          }),
        ),
      });
      return false;
    }
  }, [dbType, intl, loadFormConfig, state.status]);

  useEffect(() => {
    void loadFormConfig();
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadFormConfig]);

  const formConfig = useMemo<DynamicFormField[]>(
    () => flattenFormSectionFields(state.sections),
    [state.sections],
  );

  return {
    formConfig,
    loading: state.status === PLUGIN_CONFIG_STATUS.LOADING,
    formSections: state.sections,
    status: state.status,
    message: state.message,
    installing: state.status === PLUGIN_CONFIG_STATUS.INSTALLING,
    reload: loadFormConfig,
    installPlugin,
  };
}
