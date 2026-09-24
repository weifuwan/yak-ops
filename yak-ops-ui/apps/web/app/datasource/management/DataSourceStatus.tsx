import {
  Badge,
  Spinner,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
  type BadgeProps,
} from "@yak-ops/yak-ui";
import { CircleCheck, CircleMinus, CircleX } from "lucide-react";
import type { ReactNode } from "react";

import { useIntl } from "../i18n";
import type { DataSourceConnectionStatus } from "../model/types";

interface DataSourceStatusProps {
  status?: DataSourceConnectionStatus;
}

interface StatusConfigItem {
  tone: NonNullable<BadgeProps["tone"]>;
  icon: ReactNode;
  text: string;
  tooltip?: string;
}

const DataSourceStatus = ({ status }: DataSourceStatusProps) => {
  const intl = useIntl();
  const connectedConfig: StatusConfigItem = {
    tone: "success",
    icon: <CircleCheck size={13} />,
    text: intl.formatMessage({ id: "pages.datasource.status.connected" }),
    tooltip: intl.formatMessage({
      id: "pages.datasource.status.connectedTooltip",
    }),
  };
  const disconnectedConfig: StatusConfigItem = {
    tone: "danger",
    icon: <CircleX size={13} />,
    text: intl.formatMessage({ id: "pages.datasource.status.disconnected" }),
    tooltip: intl.formatMessage({
      id: "pages.datasource.status.disconnectedTooltip",
    }),
  };
  const unknownConfig: StatusConfigItem = {
    tone: "neutral",
    icon: <CircleMinus size={13} />,
    text: intl.formatMessage({ id: "pages.datasource.status.unknown" }),
    tooltip: intl.formatMessage({ id: "pages.datasource.status.unknownTooltip" }),
  };
  const statusConfigMap: Record<string, StatusConfigItem> = {
    CONNECTED: connectedConfig,
    CONNECTED_SUCCESS: connectedConfig,
    DISCONNECTED: disconnectedConfig,
    CONNECTED_FAILED: disconnectedConfig,
    UNKNOWN: unknownConfig,
    CONNECTED_NONE: unknownConfig,
    CONNECTING: {
      tone: "info",
      icon: <Spinner size="small" label="Connecting" />,
      text: intl.formatMessage({ id: "pages.datasource.status.connecting" }),
      tooltip: intl.formatMessage({
        id: "pages.datasource.status.connectingTooltip",
      }),
    },
  };

  const normalized = String(status || "UNKNOWN").trim().toUpperCase();
  const currentConfig = statusConfigMap[normalized] || unknownConfig;

  return (
    <Tooltip>
      <TooltipTrigger
        className="inline-flex"
        aria-label={currentConfig.tooltip}
      >
        <Badge
          tone={currentConfig.tone}
          className="min-w-20 justify-center gap-1.5 whitespace-nowrap px-2.5 py-0.5"
        >
          {currentConfig.icon}
          {currentConfig.text}
        </Badge>
      </TooltipTrigger>
      <TooltipContent>{currentConfig.tooltip}</TooltipContent>
    </Tooltip>
  );
};

export default DataSourceStatus;
