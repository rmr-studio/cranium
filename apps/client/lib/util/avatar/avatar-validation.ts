import type { InputValidation } from '@/components/ui/avatar-uploader';

export const AVATAR_VALIDATION: InputValidation = {
  maxSize: 10 * 1024 * 1024,
  allowedTypes: ['image/jpeg', 'image/png', 'image/gif'],
  errorMessage: 'Please upload a JPEG, PNG, or GIF under 10MB.',
};
