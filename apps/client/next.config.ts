import type { NextConfig } from "next";

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8081";

const nextConfig: NextConfig = {
  output: "standalone",
  transpilePackages: ["@cranium/ui", "@cranium/hooks", "@cranium/utils"],
  async rewrites() {
    return [
      {
        source: "/api/v1/avatars/:path*",
        destination: `${apiUrl}/api/v1/avatars/:path*`,
      },
    ];
  },
};

export default nextConfig;
