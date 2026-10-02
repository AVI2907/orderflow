import { catalogApi } from '../api/client';

export const IMAGE_ACCEPT = 'image/jpeg,image/png,image/webp';
const MAX_BYTES = 5 * 1024 * 1024;

export class UploadError extends Error {}

/** Uploads a photo straight to S3 and returns the address to save on the product. */
export async function uploadProductImage(file: File): Promise<string> {
  if (!IMAGE_ACCEPT.split(',').includes(file.type)) {
    throw new UploadError('Please choose a JPEG, PNG or WebP image.');
  }
  if (file.size > MAX_BYTES) {
    throw new UploadError('Images must be 5 MB or smaller.');
  }

  const { data } = await catalogApi.post<{ uploadUrl: string; imageUrl: string }>(
    '/products/images/upload-url',
    { contentType: file.type, size: file.size }
  );

  // Plain fetch, not catalogApi: the upload link is its own credential,
  // and adding our login token would make S3 reject the request.
  const res = await fetch(data.uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file,
  });
  if (!res.ok) {
    throw new UploadError('Photo upload failed. Please try again.');
  }
  return data.imageUrl;
}

export function errorMessage(err: unknown, fallback: string): string {
  if (err instanceof UploadError) return err.message;
  const serverMessage = (err as any)?.response?.data?.message;
  return typeof serverMessage === 'string' && serverMessage ? serverMessage : fallback;
}
