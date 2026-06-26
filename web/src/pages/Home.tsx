import { Link } from "wouter";
import Navbar from "@/components/layout/Navbar";
import Footer from "@/components/layout/Footer";
import Hero from "@/components/sections/Hero";
import Features from "@/components/sections/Features";
import AppPreview from "@/components/sections/AppPreview";
import ZeroTrust from "@/components/sections/ZeroTrust";
import Architecture from "@/components/sections/Architecture";
import Download from "@/components/sections/Download";

export default function Home() {
  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col overflow-x-hidden selection:bg-primary selection:text-white">
      <Navbar />
      <main className="flex-grow">
        <Hero />
        <AppPreview />
        <Features />
        <ZeroTrust />
        <Architecture />
        <Download />
      </main>
      <Footer />
    </div>
  );
}
