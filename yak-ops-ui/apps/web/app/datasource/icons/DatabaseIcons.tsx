import { Database } from "lucide-react";
import type { CSSProperties } from "react";

import { normalizeDataSourceType } from "../constants";
import MysqlIcon from "./MysqlIcon";
import OracleIcon from "./OracleIcon";
import PsSqlIcon from "./PsSqlIcon";
import { DATASOURCE_ICON_ASSETS } from "./icon-assets";

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

  const assetSrc = DATASOURCE_ICON_ASSETS[normalizedType.toUpperCase()];
  if (assetSrc) {
    return (
      <img
        src={assetSrc}
        alt=""
        aria-hidden="true"
        style={{ width, height, display: "inline-block", objectFit: "contain", flexShrink: 0 }}
      />
    );
  }

  switch (normalizedType) {
    case "mysql":
      return <MysqlIcon width={width} height={height} />;
    case "oracle":
      return <OracleIcon width={width} height={height} />;
    case "postgre_sql":
      return <PsSqlIcon width={width} height={height} />;
    default:
      return <Database style={fallbackStyle} strokeWidth={1.7} />;
  }
};

export default DatabaseIcons;
