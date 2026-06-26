import { Shield } from "lucide-react";

export default function Footer() {
  return (
    <footer className="bg-background border-t border-border py-16">
      <div className="container mx-auto px-6">
        <div className="flex flex-col md:flex-row justify-between items-start gap-12">
          <div className="max-w-xs">
            <div className="flex items-center gap-2 mb-4">
              <Shield className="w-6 h-6 text-primary" />
              <span className="font-bold text-lg tracking-wide uppercase">MaunKavach</span>
            </div>
            <p className="text-muted-foreground text-sm leading-relaxed">
              The last refuge for privacy. Hardware-backed security, zero-trust architecture, and complete ownership of your communications.
            </p>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-3 gap-12 text-sm">
            <div className="flex flex-col gap-3">
              <h4 className="font-mono text-foreground uppercase tracking-widest mb-2">Product</h4>
              <a href="#features" className="text-muted-foreground hover:text-primary transition-colors">Features</a>
              <a href="#architecture" className="text-muted-foreground hover:text-primary transition-colors">Security</a>
              <a href="#download" className="text-muted-foreground hover:text-primary transition-colors">Download</a>
            </div>
            <div className="flex flex-col gap-3">
              <h4 className="font-mono text-foreground uppercase tracking-widest mb-2">Resources</h4>
              <a href="#" className="text-muted-foreground hover:text-primary transition-colors">Documentation</a>
              <a href="#" className="text-muted-foreground hover:text-primary transition-colors">Source Code</a>
              <a href="#" className="text-muted-foreground hover:text-primary transition-colors">Self-Hosting</a>
            </div>
            <div className="flex flex-col gap-3">
              <h4 className="font-mono text-foreground uppercase tracking-widest mb-2">Legal</h4>
              <a href="#" className="text-muted-foreground hover:text-primary transition-colors">Privacy Policy</a>
              <a href="#" className="text-muted-foreground hover:text-primary transition-colors">Terms of Service</a>
              <a href="#" className="text-muted-foreground hover:text-primary transition-colors">Transparency</a>
            </div>
          </div>
        </div>
        <div className="mt-16 pt-8 border-t border-border/50 text-center md:text-left text-xs text-muted-foreground font-mono">
          <p>&copy; {new Date().getFullYear()} MaunKavach. No trackers. No analytics. No compromise.</p>
        </div>
      </div>
    </footer>
  );
}
