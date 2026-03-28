import type { MetadataRoute } from "next";

const isCanonicalDomain =
  process.env.NEXT_PUBLIC_SITE_URL === "https://getriven.io" ||
  !process.env.NEXT_PUBLIC_SITE_URL;

export default function robots(): MetadataRoute.Robots {
  if (!isCanonicalDomain) {
    return {
      rules: [{ userAgent: "*", disallow: ["/"] }],
    };
  }

  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: ["/api/"],
      },
    ],
    sitemap: "https://getriven.io/sitemap.xml",
  };
}
