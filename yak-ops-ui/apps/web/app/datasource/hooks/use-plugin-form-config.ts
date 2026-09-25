import { getDataSourcePluginConfig } from '@/service/datasource';
import type { DataSourceFormInstance } from '../editor/form-runtime';
import { useCallback, useEffect, useReducer, useRef } from 'react';

import type { DynamicFormSection } from '../types';
import {
  flattenFormSectionFields,
  getConfigInitialValues,
  normalizeConfigValuesForForm,
  normalizeFormSections,
  patchEmptyWithDefaults,
} from '../editor/form-utils';

export const PLUGIN_CONFIG_STATUS = {
  IDLE: 'IDLE',
  LOADING: 'LOADING',
  READY: 'READY',
  LOAD_FAILED: 'LOAD_FAILED',
} as const;

type PluginConfigStatus =
  (typeof PLUGIN_CONFIG_STATUS)[keyof typeof PLUGIN_CONFIG_STATUS];

interface PluginConfigState {
  status: PluginConfigStatus;
  sections: DynamicFormSection[];
  message?: string;
}

type PluginConfigAction =
  | { type: 'RESET' }
  | { type: 'LOAD_START' }
  | { type: 'LOAD_SUCCESS'; sections: DynamicFormSection[] }
  | { type: 'LOAD_FAILED'; message?: string };

const INITIAL_PLUGIN_CONFIG_STATE: PluginConfigState = {
  status: PLUGIN_CONFIG_STATUS.IDLE,
  sections: [],
};

const pluginConfigStateReducer = (
  _state: PluginConfigState,
  action: PluginConfigAction,
): PluginConfigState => {
  switch (action.type) {
    case 'LOAD_START':
      return { status: PLUGIN_CONFIG_STATUS.LOADING, sections: [] };
    case 'LOAD_SUCCESS':
      return { status: PLUGIN_CONFIG_STATUS.READY, sections: action.sections };
    case 'LOAD_FAILED':
      return {
        status: PLUGIN_CONFIG_STATUS.LOAD_FAILED,
        sections: [],
        message: action.message,
      };
    case 'RESET':
    default:
      return INITIAL_PLUGIN_CONFIG_STATE;
  }
};

interface IntlFormatter {
  formatMessage: (descriptor: { id: string }) => string;
}

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback;

/** 数据源插件配置统一生命周期。 */
export function usePluginFormConfig(params: {
  dbType: string;
  configForm: DataSourceFormInstance;
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

  useEffect(() => {
    void loadFormConfig();
    return () => {
      requestSequenceRef.current += 1;
    };
  }, [loadFormConfig]);

  return {
    formSections: state.sections,
    status: state.status,
    message: state.message,
    reload: loadFormConfig,
  };
}
