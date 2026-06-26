export default function Hero() {
  return (
    <section className="relative min-h-[90vh] flex items-center justify-center pt-16 overflow-hidden bg-noise">
      <div className="absolute inset-0 z-0">
        <img 
          src="/vault-door.png" 
          alt="Massive dark steel vault door" 
          className="w-full h-full object-cover opacity-30 object-center"
        />
        <div className="absolute inset-0 bg-gradient-to-b from-background via-background/80 to-background" />
      </div>
      
      <div className="container relative z-10 mx-auto px-6 py-24 text-center">
        <div className="inline-block mb-6 px-4 py-1.5 border border-primary/30 bg-primary/10 text-primary text-xs font-mono tracking-widest uppercase animate-in">
          Hardware-Backed Security
        </div>
        <h1 className="text-5xl md:text-7xl lg:text-8xl font-black tracking-tight mb-8 animate-in delay-100 uppercase max-w-5xl mx-auto leading-[0.9]">
          The Silence of <br />
          <span className="steel-gradient">Certainty</span>
        </h1>
        <p className="text-lg md:text-xl text-muted-foreground max-w-2xl mx-auto mb-12 animate-in delay-200 leading-relaxed">
          Native-only, zero-trust encrypted messaging. No third-party SDKs. No cloud services. A sealed vault for your communications.
        </p>
        <div className="flex flex-col sm:flex-row items-center justify-center gap-6 animate-in delay-300">
          <a 
            href="#download" 
            className="px-8 py-4 bg-primary text-primary-foreground font-bold uppercase tracking-widest hover:bg-primary/90 transition-colors w-full sm:w-auto"
          >
            Download MaunKavach
          </a>
          <a 
            href="#architecture" 
            className="px-8 py-4 border border-border bg-background/50 hover:bg-muted text-foreground font-mono uppercase tracking-widest transition-colors w-full sm:w-auto"
          >
            View Architecture
          </a>
        </div>
      </div>
    </section>
  );
}
