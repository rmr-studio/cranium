'use client';

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog';
import { getCroppedImage } from '@/components/ui/crop-utils';
import { Button } from '@riven/ui/button';
import { ImageUp, SearchIcon, SearchCheckIcon } from 'lucide-react';
import { FC, useCallback, useRef, useState } from 'react';
import Cropper from 'react-easy-crop';
import type { Area } from 'react-easy-crop';

interface FileValidation {
  maxSize: number;
  allowedTypes: string[];
  errorMessage: string;
}

interface AvatarCropDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  imageSrc: string;
  onCropComplete: (croppedBlob: Blob) => void;
  onReplaceImage?: (objectUrl: string) => void;
  validation?: FileValidation;
}

export const AvatarCropDialog: FC<AvatarCropDialogProps> = ({
  open,
  onOpenChange,
  imageSrc,
  onCropComplete,
  onReplaceImage,
  validation,
}) => {
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [croppedAreaPixels, setCroppedAreaPixels] = useState<Area | null>(null);
  const [isApplying, setIsApplying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const replaceInputRef = useRef<HTMLInputElement>(null);

  const handleCropComplete = useCallback((_croppedArea: Area, croppedPixels: Area) => {
    setCroppedAreaPixels(croppedPixels);
  }, []);

  const handleApply = async () => {
    if (!croppedAreaPixels) return;

    setIsApplying(true);
    setError(null);
    try {
      const blob = await getCroppedImage(imageSrc, croppedAreaPixels);
      onCropComplete(blob);
      onOpenChange(false);
    } catch {
      setError('Failed to crop image. Please try again.');
    } finally {
      setIsApplying(false);
    }
  };

  const handleCancel = () => {
    setError(null);
    onOpenChange(false);
  };

  const handleReplaceClick = () => {
    replaceInputRef.current?.click();
  };

  const handleReplaceFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;

    if (validation) {
      if (!validation.allowedTypes.includes(file.type) || file.size > validation.maxSize) {
        setError(validation.errorMessage);
        return;
      }
    }

    setError(null);
    setCrop({ x: 0, y: 0 });
    setZoom(1);

    const objectUrl = URL.createObjectURL(file);
    onReplaceImage?.(objectUrl);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="gap-0 overflow-hidden border-zinc-800 bg-zinc-900 p-0 sm:max-w-md"
      >
        <DialogTitle className="sr-only">Crop Image</DialogTitle>
        <DialogDescription className="sr-only">
          Adjust the crop area and zoom to select the portion of your image to use as your avatar.
        </DialogDescription>

        {/* Header */}
        <div className="flex items-center justify-between border-b border-zinc-800 px-5 py-3.5">
          <span className="text-sm font-medium text-zinc-300">Crop Image</span>
          <button
            type="button"
            onClick={handleCancel}
            aria-label="Close crop dialog"
            className="text-lg leading-none text-zinc-500 transition-colors hover:text-zinc-300"
          >
            &times;
          </button>
        </div>

        {/* Crop area */}
        <div className="relative h-72 w-full bg-zinc-950 sm:h-80">
          <Cropper
            image={imageSrc}
            crop={crop}
            zoom={zoom}
            aspect={1}
            cropShape="round"
            showGrid={true}
            onCropChange={setCrop}
            onZoomChange={setZoom}
            onCropComplete={handleCropComplete}
            style={{
              containerStyle: { background: '#09090b' },
              cropAreaStyle: {
                border: '2px solid rgba(255, 255, 255, 0.6)',
              },
            }}
          />
        </div>

        {/* Zoom slider */}
        <div className="flex items-center gap-3 border-t border-zinc-800 px-5 py-3">
          <SearchIcon className="h-4 w-4 shrink-0 text-zinc-500" />
          <input
            type="range"
            min={1}
            max={3}
            step={0.01}
            value={zoom}
            aria-label="Zoom level"
            onChange={(e) => setZoom(Number(e.target.value))}
            className="h-1 w-full cursor-pointer appearance-none rounded-full bg-zinc-700 accent-white [&::-webkit-slider-thumb]:h-3.5 [&::-webkit-slider-thumb]:w-3.5 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-white"
          />
          <SearchCheckIcon className="h-4 w-4 shrink-0 text-zinc-500" />
        </div>

        {/* Inline error */}
        {error && (
          <div className="px-5 py-2">
            <p className="text-xs text-red-400">{error}</p>
          </div>
        )}

        {/* Footer */}
        <div className="flex items-center justify-between border-t border-zinc-800 px-5 py-3">
          <div>
            {onReplaceImage && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={handleReplaceClick}
                className="text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200"
              >
                <ImageUp className="h-3.5 w-3.5" />
                Replace
              </Button>
            )}
          </div>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="ghost"
              onClick={handleCancel}
              className="text-zinc-400 hover:bg-zinc-800 hover:text-zinc-200"
            >
              Cancel
            </Button>
            <Button
              type="button"
              onClick={handleApply}
              disabled={isApplying}
              className="bg-white text-zinc-900 hover:bg-zinc-200"
            >
              {isApplying ? 'Applying...' : 'Apply'}
            </Button>
          </div>
        </div>

        {/* Hidden file input for replace — lives inside the dialog */}
        <input
          ref={replaceInputRef}
          type="file"
          accept="image/*"
          onChange={handleReplaceFileChange}
          className="hidden"
        />
      </DialogContent>
    </Dialog>
  );
};
