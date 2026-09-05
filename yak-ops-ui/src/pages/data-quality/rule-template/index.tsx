import { BRAND_THEME } from '@/styles/brand';
import { useIntl } from '@umijs/max';
import { ConfigProvider } from 'antd';

import TemplateLibraryDialogs from './components/TemplateLibraryDialogs';
import TemplateLibraryMain from './components/TemplateLibraryMain';
import TemplateLibrarySidebar from './components/TemplateLibrarySidebar';
import { useQualityTemplateLibrary } from './hooks/useQualityTemplateLibrary';

const TemplateLibraryPage = () => {
  const intl = useIntl();
  const library = useQualityTemplateLibrary();

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <div className="flex h-[calc(100vh-64px)] min-h-[620px] flex-col overflow-hidden bg-white">
        <header className="flex h-12 shrink-0 items-center border-b border-[#e8e9ec] px-5">
          <h1 className="m-0 text-[20px] font-semibold text-[#161823]">
            {intl.formatMessage({ id: 'pages.dataQuality.template.title' })}
          </h1>
        </header>

        <div className="flex min-h-0 flex-1 overflow-hidden">
          <TemplateLibrarySidebar library={library} />
          <TemplateLibraryMain library={library} />
        </div>
      </div>

      <TemplateLibraryDialogs library={library} />
    </ConfigProvider>
  );
};

export default TemplateLibraryPage;
