import { Monitor } from "lucide-react";
import { SiApple, SiLinux, SiAndroid, SiGoogleplay, SiAppstore } from "react-icons/si";

const btnBase =
  "flex-1 px-4 py-2 text-xs font-bold uppercase tracking-widest transition-colors text-center border";
const btnPrimary =
  `${btnBase} bg-primary/10 text-primary border-primary/30 hover:bg-primary hover:text-white`;
const btnMuted =
  `${btnBase} bg-muted/10 text-muted-foreground border-border cursor-default`;

export default function Download() {
  const androidApkUrl =
    "https://github.com/Abhishek-art01/01_MaunKavach/releases/download/v1.0.0/app-release.apk";

  return (
    <section id="download" className="py-32 bg-background border-t border-border">
      <div className="container mx-auto px-6">
        <div className="text-center max-w-3xl mx-auto mb-20">
          <h2 className="text-4xl md:text-6xl font-black uppercase tracking-tight mb-6">
            Take Back Control.
          </h2>
          <p className="text-xl text-muted-foreground">
            Download the client. Generate your keys. Seal the vault.
          </p>
        </div>

        <div className="flex flex-wrap justify-center gap-6 max-w-5xl mx-auto">

          {/* Android */}
          <div className="flex flex-col items-center p-10 border border-border bg-card w-full sm:w-72">
            <SiAndroid className="w-12 h-12 mb-6 text-primary" />
            <h3 className="text-xl font-bold mb-2 uppercase tracking-wider">Android</h3>
            <p className="text-sm text-muted-foreground font-mono mb-6">v1.0.0</p>
            <div className="flex gap-2 w-full mt-auto">
              <a href={androidApkUrl} className={btnPrimary}>
                Download APK
              </a>
              <a href="#download-android-play" className={btnPrimary}>
                <SiGoogleplay className="inline w-3 h-3 mr-1" />
                Play Store
              </a>
            </div>
          </div>

          {/* iOS */}
          <div className="flex flex-col items-center p-10 border border-border bg-card w-full sm:w-72">
            <SiApple className="w-12 h-12 mb-6 text-primary" />
            <h3 className="text-xl font-bold mb-2 uppercase tracking-wider">iOS</h3>
            <p className="text-sm text-muted-foreground font-mono mb-6">v1.4.2</p>
            <div className="flex gap-2 w-full mt-auto">
              <a href="#download-ios-store" className={btnPrimary + " w-full"}>
                <SiAppstore className="inline w-3 h-3 mr-1" />
                App Store
              </a>
            </div>
          </div>

          {/* Windows */}
          <div className="flex flex-col items-center p-10 border border-border bg-card w-full sm:w-72">
            <Monitor className="w-12 h-12 mb-6 text-primary" />
            <h3 className="text-xl font-bold mb-2 uppercase tracking-wider">Windows</h3>
            <p className="text-sm text-muted-foreground font-mono mb-6">v2.0.1</p>
            <div className="flex gap-2 w-full mt-auto">
              <span className={btnMuted + " flex-1"}>
                Coming Soon
              </span>
              <a href="#download-windows-store" className={btnPrimary}>
                Store
              </a>
            </div>
          </div>

          {/* macOS */}
          <div className="flex flex-col items-center p-10 border border-border bg-card w-full sm:w-72">
            <SiApple className="w-12 h-12 mb-6 text-primary" />
            <h3 className="text-xl font-bold mb-2 uppercase tracking-wider">macOS</h3>
            <p className="text-sm text-muted-foreground font-mono mb-6">v2.0.1 &bull; Apple Silicon / Intel</p>
            <div className="flex gap-2 w-full mt-auto">
              <a href="#download-mac-store" className={btnPrimary + " w-full"}>
                <SiAppstore className="inline w-3 h-3 mr-1" />
                App Store
              </a>
            </div>
          </div>

          {/* Linux */}
          <div className="flex flex-col items-center p-10 border border-border bg-card w-full sm:w-72">
            <SiLinux className="w-12 h-12 mb-6 text-primary" />
            <h3 className="text-xl font-bold mb-2 uppercase tracking-wider">Linux</h3>
            <p className="text-sm text-muted-foreground font-mono mb-6">v2.0.1</p>
            <div className="flex gap-2 w-full mt-auto">
              <a href="#download-linux-deb" className={btnPrimary}>
                .deb
              </a>
              <a href="#download-linux-store" className={btnPrimary}>
                Store
              </a>
            </div>
          </div>

        </div>
      </div>
    </section>
  );
}
