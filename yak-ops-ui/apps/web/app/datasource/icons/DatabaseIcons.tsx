import { Database } from "lucide-react";
import type { CSSProperties } from "react";

import { normalizeDataSourceType } from "../constants";
import MysqlIcon from "./MysqlIcon";
import OracleIcon from "./OracleIcon";
import PsSqlIcon from "./PsSqlIcon";
import DamengIcon from "./DamengIcon";
import StarRocksIcon from "./StarRocksIcon";
import ClickHouseIcon from "./ClickHouseIcon";
import Db2Icon from "./Db2Icon";
import OceanBaseIcon from "./OceanBaseIcon";
import SqlServerIcon from "./SqlServerIcon";
import DorisIcon from "./DorisIcon";
import SshIcon from "./SshIcon";

interface DatabaseIconsProps {
  dbType?: string;
  width?: string;
  height?: string;
}

const DatabaseIcons = ({ dbType, width = "20px", height = "20px" }: DatabaseIconsProps) => {
  const normalizedType = normalizeDataSourceType(dbType).toLowerCase();
  const fallbackStyle: CSSProperties = {
    fontSize: width,
    width,
    height,
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
  };

  switch (normalizedType) {
    case "mysql":
      return <MysqlIcon width={width} height={height} />;
    case "oracle":
      return <OracleIcon width={width} height={height} />;
    case "postgre_sql":
      return <PsSqlIcon width={width} height={height} />;
    case "dameng":
      return <DamengIcon width={width} height={height} />;
    case "starrocks":
      return <StarRocksIcon width={width} height={height} />;
    case "clickhouse":
      return <ClickHouseIcon width={width} height={height} />;
    case "db2":
      return <Db2Icon width={width} height={height} />;
    case "oceanbase":
      return <OceanBaseIcon width={width} height={height} />;
    case "sqlserver":
      return <SqlServerIcon width={width} height={height} />;
    case "doris":
      return <DorisIcon width={width} height={height} />;
    case "ssh":
      return <SshIcon width={width} height={height} />;
    default:
      return <Database style={fallbackStyle} strokeWidth={1.7} />;
  }
};

export default DatabaseIcons;
