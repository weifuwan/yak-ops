import type { DynamicFormSection } from '../model/types';

export const PLUGIN_CONFIG_STATUS = {
  IDLE: 'IDLE',
  LOADING: 'LOADING',
  READY: 'READY',
  INSTALL_REQUIRED: 'INSTALL_REQUIRED',
  INSTALLING: 'INSTALLING',
  LOAD_FAILED: 'LOAD_FAILED',
} as const;

export type PluginConfigStatus =
  (typeof PLUGIN_CONFIG_STATUS)[keyof typeof PLUGIN_CONFIG_STATUS];

export interface PluginConfigState {
  status: PluginConfigStatus;
  sections: DynamicFormSection[];
  message?: string;
}

export type PluginConfigAction =
  | { type: 'RESET' }
  | { type: 'LOAD_START' }
  | { type: 'LOAD_SUCCESS'; sections: DynamicFormSection[] }
  | { type: 'INSTALL_REQUIRED'; message?: string }
  | { type: 'INSTALL_START' }
  | { type: 'INSTALL_FAILED'; message?: string }
  | { type: 'LOAD_FAILED'; message?: string };

export const INITIAL_PLUGIN_CONFIG_STATE: PluginConfigState = {
  status: PLUGIN_CONFIG_STATUS.IDLE,
  sections: [],
};

export const pluginConfigStateReducer = (
  _state: PluginConfigState,
  action: PluginConfigAction,
): PluginConfigState => {
  switch (action.type) {
    case 'LOAD_START':
      return {
        status: PLUGIN_CONFIG_STATUS.LOADING,
        sections: [],
      };
    case 'LOAD_SUCCESS':
      return {
        status: PLUGIN_CONFIG_STATUS.READY,
        sections: action.sections,
      };
    case 'INSTALL_REQUIRED':
      return {
        status: PLUGIN_CONFIG_STATUS.INSTALL_REQUIRED,
        sections: [],
        message: action.message,
      };
    case 'INSTALL_START':
      return {
        status: PLUGIN_CONFIG_STATUS.INSTALLING,
        sections: [],
      };
    case 'INSTALL_FAILED':
      return {
        status: PLUGIN_CONFIG_STATUS.INSTALL_REQUIRED,
        sections: [],
        message: action.message,
      };
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
