'use client';

import { AvatarCropDialog } from '@/components/ui/avatar-crop-dialog';
import type { InputValidation } from '@/lib/interfaces/interface';
import { Button } from '@cranium/ui/button';
import { Label } from '@cranium/ui/label';
import { Pencil, Upload, UserRound } from 'lucide-react';
import Image from 'next/image';
import { FC, useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';

export type { InputValidation };

interface AvatarUploaderProps {
  onUpload: (file: Blob) => void;
  onRemove?: () => void;
  imageURL?: string;
  title?: string;
  validation: InputValidation;
}

function formatMaxSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${Math.round(bytes / (1024 * 1024))}MB`;
  return `${Math.round(bytes / 1024)}KB`;
}

function formatAllowedTypes(types: string[]): string {
  return types
    .map((t) => t.replace('image/', '').toUpperCase())
    .join(', ');
}

export const AvatarUploader: FC<AvatarUploaderProps> = ({
  onUpload,
  onRemove,
  imageURL,
  title,
  validation,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [cropDialogOpen, setCropDialogOpen] = useState(false);
  const [rawImageSrc, setRawImageSrc] = useState<string | null>(null);

  // Treat empty or whitespace-only URLs as no image
  const resolvedImageURL = imageURL?.trim() ? imageURL : undefined;

  // When imageURL changes to a server URL (not a blob), clear stale internal state
  useEffect(() => {
    if (resolvedImageURL && !resolvedImageURL.startsWith('blob:')) {
      if (rawImageSrc) {
        URL.revokeObjectURL(rawImageSrc);
        setRawImageSrc(null);
      }
    }
  }, [resolvedImageURL]); // eslint-disable-line react-hooks/exhaustive-deps

  // Revoke the stored original URL on unmount to prevent memory leaks
  useEffect(() => {
    return () => {
      if (rawImageSrc) URL.revokeObjectURL(rawImageSrc);
    };
  }, [rawImageSrc]);

  const openFilePicker = () => {
    inputRef.current?.click();
  };

  const openCropEditor = () => {
    if (rawImageSrc) setCropDialogOpen(true);
  };

  const validateAndLoadFile = useCallback(
    (file: File) => {
      if (!validation.allowedTypes.includes(file.type)) {
        toast.error(validation.errorMessage);
        return;
      }

      if (file.size > validation.maxSize) {
        toast.error(validation.errorMessage);
        return;
      }

      // Revoke previous original if replacing with a new file
      if (rawImageSrc) URL.revokeObjectURL(rawImageSrc);

      const objectUrl = URL.createObjectURL(file);
      setRawImageSrc(objectUrl);
      setCropDialogOpen(true);
    },
    [validation, rawImageSrc],
  );

  const handleFileChange = useCallback(
    (event: React.ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0];
      event.target.value = '';
      if (!file) return;
      validateAndLoadFile(file);
    },
    [validateAndLoadFile],
  );

  const handleCropComplete = useCallback(
    (croppedBlob: Blob) => {
      onUpload(croppedBlob);
    },
    [onUpload],
  );

  const handleReplaceImage = useCallback(
    (objectUrl: string) => {
      // Revoke old original, adopt the new one from the crop dialog
      if (rawImageSrc) URL.revokeObjectURL(rawImageSrc);
      setRawImageSrc(objectUrl);
    },
    [rawImageSrc],
  );

  const handleDialogChange = useCallback((open: boolean) => {
    setCropDialogOpen(open);
  }, []);

  const handleRemove = useCallback(() => {
    if (rawImageSrc) {
      URL.revokeObjectURL(rawImageSrc);
      setRawImageSrc(null);
    }
    onRemove?.();
  }, [rawImageSrc, onRemove]);

  return (
    <>
      <section className="flex items-center gap-4">
        {/* Avatar circle + edit badge wrapper */}
        <div className="group relative shrink-0">
          <button
            type="button"
            onClick={rawImageSrc ? openCropEditor : openFilePicker}
            aria-label={resolvedImageURL ? 'Edit avatar' : 'Upload avatar'}
            className="relative h-18 w-18 cursor-pointer overflow-hidden rounded-full border-2 border-dashed border-border transition-colors hover:border-muted-foreground"
          >
            {resolvedImageURL ? (
              <Image
                alt={title || 'Avatar'}
                src={resolvedImageURL}
                fill
                className="object-cover"
                unoptimized
              />
            ) : (
              <div className="flex h-full w-full items-center justify-center">
                <UserRound className="h-6 w-6 text-muted-foreground/50 transition-colors group-hover:text-muted-foreground" />
              </div>
            )}
          </button>
          {/* Edit badge — always visible when uploaded, positioned outside overflow */}
          {resolvedImageURL && (
            <div className="absolute right-0 bottom-0 flex h-6 w-6 items-center justify-center rounded-full border-2 border-background bg-foreground">
              <Pencil className="h-3 w-3 text-background" />
            </div>
          )}
        </div>

        {/* Label + actions */}
        <div className="flex flex-col gap-1.5">
          {title && <Label className="font-semibold">{title}</Label>}

          {resolvedImageURL ? (
            // Uploaded state — just Remove link
            <div className="flex items-center gap-2">
              {onRemove && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={handleRemove}
                  className="h-auto px-0 py-0 text-xs text-destructive hover:text-destructive hover:bg-transparent"
                >
                  Remove
                </Button>
              )}
            </div>
          ) : (
            // Empty state — Upload button + file hint
            <div className="flex flex-col gap-1">
              <Button type="button" variant="outline" size="sm" onClick={openFilePicker}>
                <Upload className="h-3.5 w-3.5" />
                Upload
              </Button>
              <span className="text-xs text-muted-foreground">
                {formatAllowedTypes(validation.allowedTypes)} up to {formatMaxSize(validation.maxSize)}
              </span>
            </div>
          )}
        </div>
      </section>

      {/* Hidden file input */}
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        onChange={handleFileChange}
        className="hidden"
      />

      {/* Crop dialog */}
      {rawImageSrc && (
        <AvatarCropDialog
          open={cropDialogOpen}
          onOpenChange={handleDialogChange}
          imageSrc={rawImageSrc}
          onCropComplete={handleCropComplete}
          onReplaceImage={handleReplaceImage}
          validation={validation}
        />
      )}
    </>
  );
};
