import { Lock, Cpu, Fingerprint, ServerOff, Code, FileLock2 } from "lucide-react";

const features = [
  {
    icon: <Lock className="w-8 h-8 text-primary" />,
    title: "AES-256-GCM",
    description: "End-to-end encryption. No exceptions, no backdoors, no compromises."
  },
  {
    icon: <Cpu className="w-8 h-8 text-primary" />,
    title: "Hardware Keystore",
    description: "Keys are generated and stored in the Android Keystore, backed by secure hardware."
  },
  {
    icon: <Fingerprint className="w-8 h-8 text-primary" />,
    title: "Biometric Unlock",
    description: "Access requires local biometric authentication. Your device, your rules."
  },
  {
    icon: <ServerOff className="w-8 h-8 text-primary" />,
    title: "Zero-Knowledge",
    description: "The server is a dumb courier. It delivers encrypted blobs and reads nothing."
  },
  {
    icon: <Code className="w-8 h-8 text-primary" />,
    title: "No 3rd-Party SDKs",
    description: "Zero analytics. Zero tracking. No Firebase, no AWS, no Supabase."
  },
  {
    icon: <FileLock2 className="w-8 h-8 text-primary" />,
    title: "Encrypted Files",
    description: "Files are encrypted locally before upload and decrypted only on the receiver's device."
  }
];

export default function Features() {
  return (
    <section id="features" className="py-24 bg-background border-y border-border relative">
      <div className="container mx-auto px-6 relative z-10">
        <div className="max-w-3xl mb-16">
          <h2 className="text-sm font-mono text-primary tracking-widest uppercase mb-4">Core Principles</h2>
          <h3 className="text-4xl md:text-5xl font-bold uppercase tracking-tight mb-6">Surgical Precision.</h3>
          <p className="text-muted-foreground text-lg leading-relaxed">
            We built MaunKavach from the metal up. Relying on native crypto APIs ensures that your keys never leave the secure hardware enclave of your device.
          </p>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {features.map((feature, idx) => (
            <div key={idx} className="p-8 border border-border bg-card hover:border-primary/50 transition-colors group">
              <div className="mb-6 opacity-80 group-hover:opacity-100 transition-opacity">
                {feature.icon}
              </div>
              <h4 className="text-xl font-bold mb-3 tracking-wide">{feature.title}</h4>
              <p className="text-muted-foreground leading-relaxed text-sm">
                {feature.description}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
