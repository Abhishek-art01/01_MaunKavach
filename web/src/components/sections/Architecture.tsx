export default function Architecture() {
  return (
    <section id="architecture" className="py-32 bg-noise relative overflow-hidden">
      <div className="container mx-auto px-6">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 items-center">
          <div>
            <h2 className="text-sm font-mono text-primary tracking-widest uppercase mb-4">Under the Hood</h2>
            <h3 className="text-4xl md:text-5xl font-bold uppercase tracking-tight mb-8">Pure Native Stack.</h3>
            
            <div className="space-y-8">
              <div className="border-l-2 border-primary pl-6">
                <h4 className="text-xl font-bold mb-2 font-mono">Android: Kotlin + Jetpack</h4>
                <p className="text-muted-foreground">Built purely in Kotlin using Jetpack Compose for the UI. No cross-platform wrappers diluting the security model.</p>
              </div>
              <div className="border-l-2 border-border pl-6 hover:border-primary transition-colors">
                <h4 className="text-xl font-bold mb-2 font-mono">Native Crypto APIs</h4>
                <p className="text-muted-foreground">Encryption handles exclusively by Android's native Crypto APIs. Certificate pinning, root detection, and anti-debug measures are baked in.</p>
              </div>
              <div className="border-l-2 border-border pl-6 hover:border-primary transition-colors">
                <h4 className="text-xl font-bold mb-2 font-mono">Self-Hosted Node.js Backend</h4>
                <p className="text-muted-foreground">The server is written in pure Node.js (HTTP/HTTPS). It stores nothing but encrypted blobs. Host it yourself on a Raspberry Pi or a VPS.</p>
              </div>
            </div>
          </div>
          <div className="relative">
            <div className="aspect-[4/5] md:aspect-square relative border border-border bg-card p-4">
              <img 
                src="/encryption-abstract.png" 
                alt="Abstract data encryption" 
                className="w-full h-full object-cover filter grayscale contrast-125"
              />
              <div className="absolute inset-0 border border-primary/20 m-4 pointer-events-none" />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
