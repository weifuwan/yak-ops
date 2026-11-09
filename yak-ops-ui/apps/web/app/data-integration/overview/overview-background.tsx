import "./overview-background.css";

const BACKGROUND_IMAGE_URL =
  "https://lf3-static.bytednsdoc.com/obj/eden-cn/lm-yvahlyj-upfbvk/ljhwZthlaukjlkulzlp/creator-pc-ui-20261013/home-background.jpg";
const BACKGROUND_VIDEO_URL =
  "https://lf3-static.bytednsdoc.com/obj/eden-cn/lm-yvahlyj-upfbvk/ljhwZthlaukjlkulzlp/creator-pc-ui-20261013/home-background.mp4";

export function OverviewBackground() {
  return (
    <div className="data-integration-overview-background" aria-hidden="true">
      <img
        className="data-integration-overview-background__media"
        src={BACKGROUND_IMAGE_URL}
        alt=""
        draggable={false}
      />
      <video
        className="data-integration-overview-background__media data-integration-overview-background__video"
        src={BACKGROUND_VIDEO_URL}
        poster={BACKGROUND_IMAGE_URL}
        autoPlay
        loop
        muted
        playsInline
        preload="metadata"
      />
      <div className="data-integration-overview-background__fade" />
    </div>
  );
}
