export default function AppPreview() {
  return (
    <section className="py-24 bg-card border-y border-border overflow-hidden">
      <div className="container mx-auto px-6">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 items-center">
          <div className="order-2 lg:order-1 relative flex justify-center">
             <div className="relative max-w-[320px] w-full aspect-[9/16] border-[8px] border-border rounded-[2rem] overflow-hidden shadow-2xl">
                <img 
                  src="/app-mockup.png" 
                  alt="MaunKavach encrypted messaging interface" 
                  className="w-full h-full object-cover"
                />
             </div>
             {/* Decorative elements */}
             <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[120%] h-[120%] bg-primary/5 blur-[100px] rounded-full pointer-events-none z-[-1]" />
          </div>
          
          <div className="order-1 lg:order-2">
            <h2 className="text-sm font-mono text-primary tracking-widest uppercase mb-4">Uncompromising Interface</h2>
            <h3 className="text-4xl md:text-5xl font-bold uppercase tracking-tight mb-8">Cold, Clean,<br/>Unbreakable.</h3>
            <p className="text-lg text-muted-foreground leading-relaxed mb-8">
              The MaunKavach interface is designed to disappear. No stickers, no stories, no social features. Just a precise, high-contrast environment for secure communication. Every pixel serves a purpose.
            </p>
            <ul className="space-y-4 font-mono text-sm text-muted-foreground">
               <li className="flex items-center gap-3">
                 <span className="w-1.5 h-1.5 bg-primary block" />
                 Biometric challenge on every app launch
               </li>
               <li className="flex items-center gap-3">
                 <span className="w-1.5 h-1.5 bg-primary block" />
                 Automatic message shredding (configurable)
               </li>
               <li className="flex items-center gap-3">
                 <span className="w-1.5 h-1.5 bg-primary block" />
                 Screenshot and screen recording blocked natively
               </li>
               <li className="flex items-center gap-3">
                 <span className="w-1.5 h-1.5 bg-primary block" />
                 Opaque push notifications (no message content shown)
               </li>
            </ul>
          </div>
        </div>
      </div>
    </section>
  );
}
