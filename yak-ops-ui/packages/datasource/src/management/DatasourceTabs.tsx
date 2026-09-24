import { Tabs, type TabsProps } from 'antd';
import './DatasourceTabs.less';

export type DatasourceTabsProps = TabsProps;

/**
 * Yak Ops unified tabs.
 *
 * The visual language is extracted from the home DataCenter overview tabs so
 * business pages keep one compact, neutral tab treatment while retaining the
 * complete Ant Design Tabs API.
 */
export default function DatasourceTabs({ className, ...props }: DatasourceTabsProps) {
  return (
    <Tabs
      {...props}
      className={['yak-tabs', className].filter(Boolean).join(' ')}
    />
  );
}
