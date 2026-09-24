import loginHeroVideo from "@/assets/login/login-hero3.mp4";
import LoginPanel from "./LoginPanel";

interface LoginPageProps {
  onAuthenticated: () => Promise<void>;
}

export default function LoginPage({ onAuthenticated }: LoginPageProps) {
  return (
    <main className="yak-login-page h-screen overflow-y-auto bg-[#fbfbfa] text-[#171717]">
      <div className="mx-auto flex min-h-screen w-full max-w-[1540px] flex-col px-6 py-4 sm:px-10 lg:px-12 lg:pb-6 lg:pt-5 xl:px-16">
        <header className="flex h-11 shrink-0 items-center">
          <img
            src="/logo1.png"
            alt="Yak Ops 一体化"
            className="h-9 w-auto select-none object-contain sm:h-9"
            draggable={false}
          />
        </header>

        <div className="grid flex-1 grid-cols-1 gap-10 pt-6 lg:grid-cols-[minmax(0,1.05fr)_minmax(540px,0.95fr)] lg:items-center lg:gap-14 lg:pt-0 xl:gap-20">
          <section className="flex min-w-0 items-center justify-center py-8 lg:py-10">
            <div className="w-full max-w-[620px]">
              <div
                className="mb-10 text-center"
                style={{ fontFamily: "'YakOps', Inter, sans-serif" }}
              >
                <h1 className="m-0 text-[46px] font-normal leading-[1.01] tracking-[-0.045em] text-[#171717] sm:text-[60px] lg:text-[64px]">
                  Data ops, &nbsp; simplified.
                </h1>
                <p className="mt-5 text-[16px] leading-7 text-[#555] sm:text-[17px]">
                  One workspace for your data.
                </p>
              </div>

              <div className="mx-auto w-full max-w-[430px]">
                <LoginPanel onAuthenticated={onAuthenticated} />
              </div>
            </div>
          </section>

          <aside className="hidden min-w-0 justify-end lg:flex">
            <div className="relative h-[min(734px,calc(100vh-112px))] w-[min(587px,calc((100vh-112px)*0.8))] overflow-hidden rounded-[24px] bg-[#ecece9] shadow-[0_12px_36px_rgba(15,23,42,0.06)]">
              <video
                className="h-full w-full object-cover"
                autoPlay
                loop
                muted
                playsInline
                preload="metadata"
                aria-label="Yak Ops login visual"
              >
                <source src={loginHeroVideo} type="video/mp4" />
              </video>
              <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/10 via-transparent to-white/5" />
            </div>
          </aside>
        </div>

        <footer className="flex h-8 shrink-0 items-center">
          <span className="text-[12px] tracking-[0.01em] text-black/45">
            Built by 魏福万
          </span>
        </footer>
      </div>
    </main>
  );
}
