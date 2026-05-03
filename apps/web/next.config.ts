import type { NextConfig } from "next";
import { validateEnv } from "./lib/env";

validateEnv();

const nextConfig: NextConfig = {
  output: "standalone",
  turbopack: {
    root: "../../",
  },
  transpilePackages: ["@riven/ui", "@riven/hooks", "@riven/utils"],
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "cdn.riven.software",
        pathname: "/images/**",
      },
    ],
  },
  async headers() {
    const linkHeader = [
      '</sitemap.xml>; rel="sitemap"; type="application/xml"',
      '</resources/faq>; rel="service-doc"; type="text/html"',
      '</resources/blog>; rel="describedby"; type="text/html"',
      '</>; rel="describedby"; type="text/markdown"',
    ].join(", ");

    return [
      {
        source: "/",
        headers: [{ key: "Link", value: linkHeader }],
      },
    ];
  },
};

export default nextConfig;
