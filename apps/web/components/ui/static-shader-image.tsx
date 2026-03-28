'use client';

import { StaticMeshGradient } from '@paper-design/shaders-react';
import { useEffect, useRef, useState, type ComponentProps } from 'react';

type StaticMeshGradientProps = ComponentProps<typeof StaticMeshGradient>;

interface StaticShaderImageProps extends StaticMeshGradientProps {
  alt?: string;
}

const MAX_POLL_FRAMES = 300; // ~5 seconds at 60fps

/**
 * Renders a StaticMeshGradient, captures the WebGL canvas to a blob URL,
 * then tears down the WebGL context. The result is a plain <img> that
 * scales safely on iOS without GPU memory pressure during pinch-to-zoom.
 *
 * Uses canvas.toBlob() (async) instead of toDataURL() to avoid blocking
 * the main thread during capture.
 */
export function StaticShaderImage({
  className,
  alt = '',
  ...shaderProps
}: StaticShaderImageProps) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);

  // Revoke the object URL on unmount to avoid memory leaks
  useEffect(() => {
    return () => {
      if (blobUrl) URL.revokeObjectURL(blobUrl);
    };
  }, [blobUrl]);

  // Poll for canvas readiness and capture to blob
  useEffect(() => {
    const div = wrapperRef.current;
    if (!div || blobUrl) return;

    let cancelled = false;
    let frames = 0;

    const check = () => {
      if (cancelled || frames++ > MAX_POLL_FRAMES) return;
      const canvas = div.querySelector('canvas');
      if (canvas && canvas.width > 0 && canvas.height > 0) {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            if (cancelled) return;
            canvas.toBlob(
              (blob) => {
                if (cancelled || !blob) return;
                setBlobUrl(URL.createObjectURL(blob));
              },
              'image/webp',
              0.9,
            );
          });
        });
      } else {
        requestAnimationFrame(check);
      }
    };

    requestAnimationFrame(check);
    return () => {
      cancelled = true;
    };
  }, [blobUrl]);

  if (blobUrl) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img src={blobUrl} alt={alt} className={className} aria-hidden="true" />
    );
  }

  return (
    <div ref={wrapperRef}>
      <StaticMeshGradient
        {...shaderProps}
        className={className}
        webGlContextAttributes={{ preserveDrawingBuffer: true }}
      />
    </div>
  );
}
