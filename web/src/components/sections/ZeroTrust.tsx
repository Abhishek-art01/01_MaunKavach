import { ShieldAlert, Server, Network } from "lucide-react";

export default function ZeroTrust() {
  return (
    <section className="py-32 bg-background border-t border-border">
      <div className="container mx-auto px-6">
        <div className="text-center max-w-3xl mx-auto mb-20">
          <h2 className="text-sm font-mono text-primary tracking-widest uppercase mb-4">The Architecture of Silence</h2>
          <h3 className="text-4xl md:text-6xl font-black uppercase tracking-tight mb-6">Zero Trust is Not a Buzzword.</h3>
          <p className="text-xl text-muted-foreground leading-relaxed">
            Every component of MaunKavach assumes the network is compromised, the server is hostile, and the operating system is under attack. We don't ask for your trust; we build a system where trust is irrelevant.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div className="flex flex-col items-center text-center p-8 border border-border bg-card">
            <ShieldAlert className="w-12 h-12 text-primary mb-6" />
            <h4 className="text-xl font-bold uppercase tracking-wider mb-4">Hostile Networks</h4>
            <p className="text-muted-foreground text-sm leading-relaxed">
              Certificate pinning prevents MITM attacks. If the TLS certificate isn't our exact hardware-backed pin, the app drops the connection immediately. No warnings, no "proceed anyway" buttons.
            </p>
          </div>

          <div className="flex flex-col items-center text-center p-8 border border-border bg-card">
            <Server className="w-12 h-12 text-primary mb-6" />
            <h4 className="text-xl font-bold uppercase tracking-wider mb-4">Hostile Servers</h4>
            <p className="text-muted-foreground text-sm leading-relaxed">
              The backend never sees plaintext. It receives opaque encrypted blobs and routes them. Even if the database is dumped, it contains nothing but cryptographic noise.
            </p>
          </div>

          <div className="flex flex-col items-center text-center p-8 border border-border bg-card">
            <Network className="w-12 h-12 text-primary mb-6" />
            <h4 className="text-xl font-bold uppercase tracking-wider mb-4">Hostile Devices</h4>
            <p className="text-muted-foreground text-sm leading-relaxed">
              Root detection, anti-debug measures, and secure window flags prevent screen recording or memory scraping. Keys reside strictly within the Android Keystore.
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
