import type { Area } from 'react-easy-crop';

const MAX_OUTPUT_SIZE = 256;

export function getCroppedImage(imageSrc: string, cropArea: Area): Promise<Blob> {
  if (cropArea.width <= 0 || cropArea.height <= 0) {
    return Promise.reject(new Error('Crop area dimensions must be greater than zero'));
  }

  return new Promise((resolve, reject) => {
    const image = new Image();
    image.crossOrigin = 'anonymous';

    image.onload = () => {
      const outputSize = Math.min(cropArea.width, cropArea.height, MAX_OUTPUT_SIZE);

      const canvas = document.createElement('canvas');
      canvas.width = outputSize;
      canvas.height = outputSize;

      const ctx = canvas.getContext('2d');
      if (!ctx) {
        reject(new Error('Failed to get canvas context'));
        return;
      }

      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';

      ctx.drawImage(
        image,
        cropArea.x,
        cropArea.y,
        cropArea.width,
        cropArea.height,
        0,
        0,
        outputSize,
        outputSize,
      );

      canvas.toBlob(
        (blob) => {
          if (!blob) {
            reject(new Error('Failed to create image blob'));
            return;
          }
          resolve(blob);
        },
        'image/png',
      );
    };

    image.onerror = () => {
      reject(new Error('Failed to load image for cropping'));
    };

    image.src = imageSrc;
  });
}
