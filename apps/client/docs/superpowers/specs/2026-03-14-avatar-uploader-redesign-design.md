# Avatar Uploader Redesign with Image Cropping

## Overview

Redesign the `AvatarUploader` component with modern aesthetics and add image cropping functionality. When a user uploads a photo, they enter a dark cinematic crop dialog to select the desired portion of their image at a 1:1 aspect ratio. The cropped blob is what gets passed to consumers, not the raw file.

Also rename the file from PascalCase (`AvatarUploader.tsx`) to kebab-case (`avatar-uploader.tsx`) per project conventions.

## Components

### 1. `components/ui/avatar-uploader.tsx` — Inline Form Trigger

`"use client"` directive required.

Replaces the existing `AvatarUploader.tsx`. Same prop interface, new visuals and validation behavior.

**Props (unchanged):**

```ts
interface InputValidation {
  maxSize: number;
  allowedTypes: string[];
  errorMessage: string;
}

interface AvatarUploaderProps {
  onUpload: (file: Blob) => void;
  onRemove?: () => void;
  imageURL?: string;
  title?: string;
  validation: InputValidation;
}
```

**Empty state:** Horizontal layout — dashed circle placeholder (72px) with a user silhouette icon on the left, title label and an outline "Upload" button with upload icon on the right.

**Uploaded state:** Same horizontal layout — the dashed circle becomes a solid circle showing the cropped avatar image, the "Upload" button label changes to "Change", and a "Remove" destructive text button appears next to it.

**Behavior:**
- Clicking the "Upload"/"Change" button or the circle placeholder opens the hidden file input
- On file selection: validate type and size against `validation` prop. If invalid, show `toast.error(validation.errorMessage)` and stop. If valid, open the crop dialog.
- The dashed circle placeholder itself is also a click target for upload
- **New behavior vs old component:** The original `AvatarUploader` accepted a `validation` prop but never enforced it. This redesign now validates before opening the crop dialog.

**Preview URL ownership:** The inline component does NOT manage its own preview state. It relies on the consumer passing the image back via `imageURL` after receiving the blob from `onUpload`. This matches the existing consumer pattern in `profile-step-form.tsx` and `workspace-form.tsx`.

**Imports:** Use `@riven/ui/*` for shared primitives (Button, Label). Fall back to `@/components/ui/*` only for components not in the shared package.

### 2. `components/ui/avatar-crop-dialog.tsx` — Crop Modal

`"use client"` directive required.

Dark cinematic crop dialog using shadcn `Dialog` component.

**Props:**

```ts
interface AvatarCropDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  imageSrc: string; // Object URL of the selected file
  onCropComplete: (croppedBlob: Blob) => void;
}
```

**Visual design:**
- Always dark regardless of app theme — `bg-zinc-950` dialog overlay, `bg-zinc-900` dialog content
- Header: "Crop Image" label on the left, X close button on the right, separated by `border-zinc-800`
- Center: Image with `react-easy-crop` Cropper component, 1:1 aspect ratio locked, `cropShape="round"` so the user sees a circular crop area matching the circular avatar display. The area outside the crop is dimmed.
- Zoom control: Horizontal slider between zoom-out (minus magnifier) and zoom-in (plus magnifier) icons. Uses a styled range input with dark theme overrides.
- Footer: "Cancel" text button (zinc-400) and "Apply" solid button (white bg, black text), separated by `border-zinc-800`

**Behavior:**
- Image loads into `react-easy-crop` with `aspect={1}`, `cropShape="round"`
- User drags to reposition, uses slider to zoom (range: 1x to 3x)
- "Apply" calls `getCroppedImage()` utility, receives blob, calls `onCropComplete(blob)`, dialog closes
- "Cancel" or backdrop click closes dialog with no changes

**Object URL lifecycle:** The avatar-uploader component creates the object URL from the raw file before opening the dialog. It revokes this object URL after the dialog closes (whether via Apply or Cancel). The cropped blob's preview URL is managed by the consumer.

### 3. `components/ui/crop-utils.ts` — Crop Helper

No `"use client"` directive needed — pure utility.

Uses the `Area` type exported by `react-easy-crop` directly rather than redefining it.

```ts
import type { Area } from 'react-easy-crop';

function getCroppedImage(imageSrc: string, cropArea: Area): Promise<Blob>
```

- Creates an offscreen `<canvas>` element
- Loads the image from `imageSrc`
- Draws only the crop area onto the canvas at the crop area's pixel dimensions
- Exports as `image/png` to preserve quality (avatars are small, file size is not a concern)
- Returns the blob via `canvas.toBlob()` wrapped in a Promise
- Rejects the promise if the image fails to load or `toBlob()` returns null
- Cleans up the canvas and image after export

## New Dependency

`react-easy-crop` — lightweight image cropping library (~10KB gzipped). Provides a React component with pan/zoom on an image and returns crop area coordinates. No other dependencies needed.

Install: `npm install react-easy-crop`

## Interaction Flows

### Upload Flow
1. User clicks "Upload" button or dashed circle placeholder
2. Hidden file input opens native file picker
3. User selects a file
4. Validate file type against `validation.allowedTypes` and size against `validation.maxSize`
5. If invalid: `toast.error(validation.errorMessage)`, stop
6. If valid: create object URL from raw file, open crop dialog with the image
7. User positions crop area and adjusts zoom
8. User clicks "Apply"
9. `getCroppedImage()` produces a cropped blob
10. `onUpload(croppedBlob)` fires to consumer
11. Dialog closes, raw file object URL is revoked
12. Consumer receives blob, creates preview URL, passes it back as `imageURL`

### Change Flow
1. User clicks "Change" button (visible when image is already uploaded)
2. Same flow as Upload — new crop replaces old preview

### Remove Flow
1. User clicks "Remove" button
2. `onRemove()` fires to consumer
3. Inline component returns to empty state (consumer clears `imageURL`)

## Files Changed

**New files:**
- `components/ui/avatar-uploader.tsx` — inline trigger (replacement for AvatarUploader.tsx)
- `components/ui/avatar-crop-dialog.tsx` — crop modal
- `components/ui/crop-utils.ts` — canvas crop utility

**Deleted files:**
- `components/ui/AvatarUploader.tsx` — replaced by kebab-case version

**Updated imports (path change only, no prop changes):**
- `components/feature-modules/onboarding/components/forms/profile-step-form.tsx`
- `components/feature-modules/onboarding/components/forms/workspace-step-form.tsx`
- `components/feature-modules/onboarding/components/OnboardForm.tsx`
- `components/feature-modules/workspace/components/form/workspace-form.tsx`

## Dark Cinematic Styling Notes

The crop dialog uses hardcoded dark colors (`zinc-900`, `zinc-800`, `zinc-950`) independent of the app's theme toggle. This is intentional — dark backgrounds make images pop during editing, matching professional photo-editing conventions.

The inline trigger component follows the app's normal theme (light/dark via CSS variables).
