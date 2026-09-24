import { DatabaseOutlined } from '@ant-design/icons';
import type { CSSProperties } from 'react';

import CacheIcon from './CacheIcon';
import ClickhouseIcon from './ClickhouseIcon';
import DaMengIcon from './DamengIcon';
import DB2Icon from './DB2Icon';
import DorisIcon from './DorisIcon';
import DuckIcon from './DuckIcon';
import ElasticSearchIcon from './ElasticSearchIcon';
import GbaseIcon from './GbaseIcon';
import GoldenIcon from './GoldenIcon';
import HanaIcon from './HanaIcon';
import HighGoIcon from './HighGoIcon';
import HiveIcon from './HiveIcon';
import IrisIcon from './IrisIcon';
import KingBaseIcon from './KingBaseIcon';
import MongoDBIcon from './MongoDBIcon';
import MysqlIcon from './MysqlIcon';
import OceanBaseIcon from './OceanBaseIcon';
import OpenGaussIcon from './OpenGaussIcon';
import OracleIcon from './OracleIcon';
import PsSqlIcon from './PsSqlIcon';
import SQLite from './SQLite';
import SQLServer from './SQLServer';
import StarRocksIcon from './StarRocksIcon';
import TiDBIcon from './TiDBIcon';
import XuguIcon from './XuguIcon';
import YaShanIcon from './YaShanIcon';

interface DatabaseIconsProps {
  dbType?: string;
  width?: string;
  height?: string;
}

const DatabaseIcons = ({
  dbType,
  width = '20px',
  height = '20px',
}: DatabaseIconsProps) => {
  const normalizedType = String(dbType || '').trim().toLowerCase();
  const fallbackStyle: CSSProperties = {
    fontSize: width,
    width,
    height,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
  };

  switch (normalizedType) {
    case 'mysql':
      return <MysqlIcon width={width} height={height} />;
    case 'tidb':
    case 'ti_db':
      return <TiDBIcon width={width} height={height} />;
    case 'goldendb':
    case 'golden_db':
    case 'zte_goldendb':
      return <GoldenIcon width={width} height={height} />;
    case 'gbase8c':
    case 'gbase_8c':
    case 'gbase8a':
    case 'gbase_8a':
    case 'gbase8s':
    case 'gbase_8s':
      return <GbaseIcon width={width} height={height} />;
    case 'hana':
    case 'sap_hana':
    case 'saphana':
      return <HanaIcon width={width} height={height} />;
    case 'oracle':
      return <OracleIcon width={width} height={height} />;
    case 'postgre_sql':
    case 'postgresql':
    case 'postgres':
      return <PsSqlIcon width={width} height={height} />;
    case 'db2':
      return <DB2Icon width={width} height={height} />;
    case 'open_gauss':
    case 'opengauss':
      return <OpenGaussIcon width={width} height={height} />;
    case 'sql_server':
    case 'sqlserver':
    case 'mssql':
      return <SQLServer width={width} height={height} />;
    case 'oceanbase':
      return <OceanBaseIcon width={width} height={height} />;
    case 'yashan_db':
    case 'yashandb':
    case 'yasdb':
      return <YaShanIcon width={width} height={height} />;
    case 'highgo':
    case 'high_go':
    case 'hgdb':
      return <HighGoIcon width={width} height={height} />;
    case 'iris':
    case 'intersystems_iris':
      return <IrisIcon width={width} height={height} />;
    case 'xugu':
    case 'xugudb':
      return <XuguIcon width={width} height={height} />;
    case 'duckdb':
    case 'duck_db':
      return <DuckIcon width={width} height={height} />;
    case 'doris':
      return <DorisIcon width={width} height={height} />;
    case 'starrocks':
      return <StarRocksIcon width={width} height={height} />;
    case 'clickhouse':
      return <ClickhouseIcon width={width} height={height} />;
    case 'elasticsearch':
    case 'elasticsearch7':
    case 'elasticsearch_7':
    case 'es7':
    case 'elasticsearch8':
    case 'elasticsearch_8':
    case 'es8':
      return <ElasticSearchIcon width={width} height={height} />;
    case 'mongodb':
    case 'mongo':
    case 'mongo_db':
      return <MongoDBIcon width={width} height={height} />;
    case 'kingbase':
      return <KingBaseIcon width={width} height={height} />;
    case 'dameng':
      return <DaMengIcon width={width} height={height} />;
    case 'sqlite':
      return <SQLite width={width} height={height} />;
    case 'cache':
      return <CacheIcon width={width} height={height} />;
    case 'hive3':
      return <HiveIcon width={width} height={height} />;
    default:
      return <DatabaseOutlined style={fallbackStyle} />;
  }
};

export default DatabaseIcons;
