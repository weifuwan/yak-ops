import batchLinkUp from './zh-CN/batch-link-up';
import component from './zh-CN/component';
import dashboard from './zh-CN/dashboard';
import dashboardEditor from './zh-CN/dashboard-editor';
import dashboardEditorAdvanced from './zh-CN/dashboard-editor-advanced';
import dashboardEditorChart from './zh-CN/dashboard-editor-chart';
import dashboardEditorFields from './zh-CN/dashboard-editor-fields';
import dashboardEditorStyle from './zh-CN/dashboard-editor-style';
import dataDevelopment from './zh-CN/data-development';
import dataDevelopmentEditor from './zh-CN/data-development-editor';
import dataQuality from './zh-CN/data-quality';
import dataService from './zh-CN/data-service';
import dataSource from './zh-CN/data-source';
import globalHeader from './zh-CN/globalHeader';
import home from './zh-CN/home';
import menu from './zh-CN/menu';
import pages from './zh-CN/pages';
import pwa from './zh-CN/pwa';
import realtimeSync from './zh-CN/realtime-sync';
import settingDrawer from './zh-CN/settingDrawer';
import settings from './zh-CN/settings';
import workflow from './zh-CN/workflow';
import workflowEditor from './zh-CN/workflow-editor';
import workflowEditorRuntime from './zh-CN/workflow-editor-runtime';
import workflowInstance from './zh-CN/workflow-instance';
import workflowScheduleHistory from './zh-CN/workflow-schedule-history';

export default {
  'navBar.lang': '语言',
  'layout.user.link.help': '帮助',
  'layout.user.link.privacy': '隐私',
  'layout.user.link.terms': '条款',
  'app.preview.down.block': '下载此页面到本地项目',
  'app.welcome.link.fetch-blocks': '获取全部区块',
  'app.welcome.link.block-list': '基于 block 开发，快速构建标准页面',
  ...pages,
  ...dataSource,
  ...batchLinkUp,
  ...dataService,
  ...realtimeSync,
  ...dataQuality,
  ...home,
  ...dataDevelopment,
  ...dataDevelopmentEditor,
  ...workflow,
  ...workflowEditor,
  ...workflowEditorRuntime,
  ...workflowInstance,
  ...workflowScheduleHistory,
  ...dashboard,
  ...dashboardEditor,
  ...dashboardEditorChart,
  ...dashboardEditorAdvanced,
  ...dashboardEditorFields,
  ...dashboardEditorStyle,
  ...globalHeader,
  ...menu,
  ...settingDrawer,
  ...settings,
  ...pwa,
  ...component,
};
