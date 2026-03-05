import { Button } from '@riven/ui/button';
import { Input } from '@riven/ui/input';
import { Label } from '@riven/ui/label';
import { Upload } from 'lucide-react';
import Image from 'next/image';
import { FC, useId, useRef } from 'react';

interface InputValidation {
  maxSize: number; // Maximum file size in bytes
  allowedTypes: string[]; // Allowed MIME types
  errorMessage: string; // Error message to display if validation fails
}

interface AvatarUploaderProps {
  onUpload: (file: Blob) => void;
  onRemove?: () => void;
  imageURL?: string;
  title?: string;
  validation: InputValidation;
}

export const AvatarUploader: FC<AvatarUploaderProps> = ({
  onUpload,
  imageURL,
  onRemove,
  title,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const inputId = useId();

  /**
   * Allows File input to be accessed from a Button component
   */
  const uploadFile = () => {
    if (!inputRef.current) return;

    inputRef.current.click();
  };

  const handleFileChangeEvent = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    onUpload(file);
    event.target.value = ''; // Reset input value to allow re-uploading the same file
  };

  return (
    <section className="mt-2 flex items-center">
      <div className="group/picture relative mt-2">
        <div className="relative h-20 w-20 overflow-hidden rounded-xl">
          {imageURL ? (
            <Image
              alt={title || 'Uploaded Avatar Picture'}
              className=""
              src={imageURL}
              fill
              style={{
                objectFit: 'cover',
              }}
            />
          ) : (
            <div className="h-full w-full rounded-xl border-2"></div>
          )}
        </div>

        <Input
          ref={inputRef}
          onChange={handleFileChangeEvent}
          id={inputId}
          className="absolute mt-6 hidden w-full"
          accept="image/*"
          type="file"
        />
        <label
          htmlFor={inputId}
          className="absolute top-0 left-0 flex h-full w-full cursor-pointer items-center bg-neutral-900/50 text-center opacity-0 transition-opacity group-hover/picture:opacity-100 dark:bg-neutral-950/70"
        >
          Upload Picture
        </label>
      </div>

      <div className="ml-4 flex flex-col">
        <Label className="hidden font-semibold md:block">{title}</Label>
        <div className="mt-2 flex flex-col space-y-2 md:flex-row md:space-y-0 md:space-x-4">
          <Button type="button" onClick={uploadFile} variant={'outline'}>
            <Upload className="h-4 w-4" />
            <span>Upload Picture</span>
          </Button>
          {onRemove && (
            <Button type="button" onClick={onRemove} variant={'destructive'}>
              Remove
            </Button>
          )}
        </div>
      </div>
    </section>
  );
};
