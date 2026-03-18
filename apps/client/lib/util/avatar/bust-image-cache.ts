/**
 * Appends a cache-busting query parameter to an image URL, forcing the
 * browser to fetch a fresh copy instead of serving from its HTTP cache.
 *
 * @param url    The image URL to bust.
 * @param version Optional stable version identifier (e.g. entity updatedAt
 *                timestamp). Falls back to Date.now() when omitted.
 */
export function bustImageCache(
  url: string | undefined | null,
  version?: number,
): string | undefined {
  if (!url) return undefined;
  const v = version ?? Date.now();
  const separator = url.includes('?') ? '&' : '?';
  return `${url}${separator}t=${v}`;
}
