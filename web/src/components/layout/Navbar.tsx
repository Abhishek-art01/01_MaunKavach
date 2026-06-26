import { Link } from "wouter";
import { Shield } from "lucide-react";

export default function Navbar() {
  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-background/80 backdrop-blur-md border-b border-border">
      <div className="container mx-auto px-6 h-16 flex items-center justify-between">
        <Link href="/" className="flex items-center gap-2 group">
          <Shield className="w-6 h-6 text-primary group-hover:text-white transition-colors" />
          <span className="font-bold text-lg tracking-wide uppercase">MaunKavach</span>
        </Link>
        <nav className="hidden md:flex items-center gap-8 text-sm font-mono text-muted-foreground">
          <a href="#features" className="hover:text-foreground transition-colors">Features</a>
          <a href="#architecture" className="hover:text-foreground transition-colors">Architecture</a>
          <a href="#download" className="hover:text-foreground transition-colors">Download</a>
        </nav>
        <div>
          <a href="#download" className="bg-primary text-primary-foreground px-5 py-2 text-sm font-bold uppercase tracking-wider hover:bg-primary/90 transition-colors">
            Get Started
          </a>
        </div>
      </div>
    </header>
  );
}
