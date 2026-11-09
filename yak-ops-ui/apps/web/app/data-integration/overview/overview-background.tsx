import homeBackgroundImage from "./assets/home-background.jpg.png";
import homeBackgroundVideo from "./assets/home-background.mp4";

const MEDIA_CLASS_NAME =
  "absolute -top-[150px] right-0 h-[599px] w-[1240px] max-w-none origin-top scale-110 object-cover object-top opacity-80";

export function OverviewBackground() {
  return (
    <div
      className="pointer-events-none absolute right-0 top-[60px] z-0 h-[449px] w-[1240px] max-w-full overflow-hidden max-md:hidden"
      aria-hidden="true"
    >
      <img
        className={MEDIA_CLASS_NAME}
        src={homeBackgroundImage}
        alt=""
        draggable={false}
      />
      <video
        className={`${MEDIA_CLASS_NAME} motion-reduce:hidden`}
        src={homeBackgroundVideo}
        poster={homeBackgroundImage}
        autoPlay
        loop
        muted
        playsInline
        preload="metadata"
      />
      <div className="absolute inset-0 bg-[linear-gradient(180deg,color-mix(in_srgb,var(--yak-color-hover)_80%,transparent)_0,transparent_50%)]" />
      <div className="absolute inset-0 bg-[linear-gradient(270deg,transparent_52%,var(--yak-color-hover)_100%)]" />
      <div className="absolute inset-0 bg-[linear-gradient(180deg,transparent_71%,var(--yak-color-hover)_100%)]" />
    </div>
  );
}
