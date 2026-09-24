import React from "react";
import { Button, Space, Tag, notification } from "antd";
import {
  AlertCircle,
  CheckCircle2,
  Info,
  TriangleAlert,
  ArrowRight,
  X,
} from "lucide-react";
import "./index.less";

type NotifyType = "error" | "success" | "warning" | "info";

interface PrettyNotificationOptions {
  type?: NotifyType;
  title: string;
  description?: React.ReactNode;
  meta?: string;
  btnText?: string;
  onClick?: () => void;
  duration?: number;
  placement?: "topRight" | "topLeft" | "bottomRight" | "bottomLeft";
  key?: string;
}

const toneMap = {
  error: {
    icon: <AlertCircle size={18} />,
    color: "#ef4444",
    softColor: "rgba(239, 68, 68, 0.10)",
    borderColor: "rgba(239, 68, 68, 0.16)",
    tag: "Error",
    bg: "linear-gradient(180deg, rgba(255,255,255,0.98), rgba(255,250,250,0.98))",
  },
  success: {
    icon: <CheckCircle2 size={18} />,
    color: "#22c55e",
    softColor: "rgba(34, 197, 94, 0.10)",
    borderColor: "rgba(34, 197, 94, 0.16)",
    tag: "Success",
    bg: "linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,255,251,0.98))",
  },
  warning: {
    icon: <TriangleAlert size={18} />,
    color: "#f59e0b",
    softColor: "rgba(245, 158, 11, 0.12)",
    borderColor: "rgba(245, 158, 11, 0.18)",
    tag: "Warning",
    bg: "linear-gradient(180deg, rgba(255,255,255,0.98), rgba(255,253,247,0.98))",
  },
  info: {
    icon: <Info size={18} />,
    color: "hsl(231 48% 48%)",
    softColor: "rgba(76, 91, 173, 0.10)",
    borderColor: "rgba(76, 91, 173, 0.16)",
    tag: "Info",
    bg: "linear-gradient(180deg, rgba(255,255,255,0.98), rgba(248,250,255,0.98))",
  },
};

export function openPrettyNotification({
  type = "info",
  title,
  description,
  meta,
  btnText,
  onClick,
  duration = 4,
  placement = "topRight",
  key,
}: PrettyNotificationOptions) {
  const tone = toneMap[type];

  notification.open({
    key,
    placement,
    duration,
    message: null,
    closeIcon: (
      <div className="pretty-notification-close">
        <X size={13} />
      </div>
    ),
    className: "pretty-notification-wrapper",
    style: {
      width: 400,
      background: "transparent",
      boxShadow: "none",
      padding: 0,
    },
    description: (
      <div
        className="pretty-notification-card"
        style={{
          background: tone.bg,
          borderColor: "rgba(15, 23, 42, 0.08)",
        }}
      >
        <div
          className="pretty-notification-top-glow"
          style={{
            background: `linear-gradient(90deg, transparent, ${tone.color}, transparent)`,
          }}
        />

        <Space align="start" size={12} style={{ width: "100%" }}>
          <div
            className="pretty-notification-icon"
            style={{
              color: tone.color,
              background: tone.softColor,
              borderColor: tone.borderColor,
            }}
          >
            {tone.icon}
          </div>

          <div className="pretty-notification-body">
            <div className="pretty-notification-header">
              <div className="pretty-notification-title">{title}</div>

              <Tag
                bordered={false}
                className="pretty-notification-tag"
                style={{
                  color: tone.color,
                  background: tone.softColor,
                }}
              >
                {tone.tag}
              </Tag>
            </div>

            {description ? (
              <div className="pretty-notification-description">
                {description}
              </div>
            ) : null}

            {(meta || btnText) && (
              <div className="pretty-notification-footer">
                {meta ? (
                  <span className="pretty-notification-meta">{meta}</span>
                ) : (
                  <span />
                )}

                {btnText ? (
                  <Button
                    type="text"
                    size="small"
                    onClick={onClick}
                    className="pretty-notification-action"
                    style={{ color: tone.color }}
                  >
                    <Space size={4}>
                      {btnText}
                      <ArrowRight size={14} />
                    </Space>
                  </Button>
                ) : null}
              </div>
            )}
          </div>
        </Space>
      </div>
    ),
  });
}