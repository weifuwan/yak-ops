import { Database } from "lucide-react";
import type { CSSProperties } from "react";

import MysqlIcon from "./MysqlIcon";
import OracleIcon from "./OracleIcon";
import PsSqlIcon from "./PsSqlIcon";

interface DatabaseIconsProps {
  dbType?: string;
  width?: string;
  height?: string;
}

const DatabaseIcons = ({ dbType, width = "20px", height = "20px" }: DatabaseIconsProps) => {
  const normalizedType = String(dbType || "")
    .trim()
    .toLowerCase();
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
    case "postgresql":
    case "postgres":
      return <PsSqlIcon width={width} height={height} />;
    default:
      return <Database style={fallbackStyle} strokeWidth={1.7} />;
  }
};

export default DatabaseIcons;
