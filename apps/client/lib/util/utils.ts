import { clsx, type ClassValue } from 'clsx';
import dayjs from 'dayjs';
import { twMerge } from 'tailwind-merge';
import { v4 } from 'uuid';

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function uuid() {
  return v4();
}

import type { ImageLoaderProps } from 'next/image';

const CDN_URL = (process.env.NEXT_PUBLIC_CDN_URL ?? '').replace(/\/+$/, '');

export const getCdnUrl = (path: string): string => `${CDN_URL}/${path}`;

export const cdnImageLoader = ({ src }: ImageLoaderProps): string => getCdnUrl(src);

export function exists<T>(value: T | undefined | null): value is T {
  return value !== undefined && value !== null;
}

export function undefinedIfNull<T>(value: T | null): T | undefined {
  return value === null ? undefined : value;
}

export const getInitials = (name: string): string => {
  if (!name.trim()) {
    return '';
  }

  // Split the name into parts, filtering out empty strings caused by extra spaces
  const nameParts = name.trim().split(/\s+/);

  // Extract the first letter of each part and limit to the first two
  const initials = nameParts.map((part) => part[0].toUpperCase()).slice(0, 2);

  // Join the initials into a single string
  return initials.join('');
};

export const toTitleCase = (value: string | null | undefined): string => {
  if (!value) return '';

  return value
    .split(' ')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ');
};

/**
 * Converts a string to key case (lowercase with underscores)
 * @param value - The input string
 * @returns
 */
export const toKeyCase = (value: string | null | undefined): string => {
  if (!value) return '';

  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_') // Replace non-alphanumeric characters with underscores
    .replace(/^_+|_+$/g, ''); // Remove leading/trailing underscores
};

export const fromKeyCase = (value: string | null | undefined): string => {
  if (!value) return '';

  return value
    .trim()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
};

export const allNotNull = <T>(values: (T | null)[]): values is NonNullable<T>[] => {
  return values.every((value) => value !== null);
};

export const api = () => {
  const url = process.env.NEXT_PUBLIC_API_URL;
  if (!url) {
    throw new Error('API URL not configured');
  }

  return url;
};

export const currency = () => {
  return new Set(
    Intl.supportedValuesOf
      ? Intl.supportedValuesOf('currency') // modern browsers
      : ['USD', 'EUR', 'JPY', 'GBP', 'AUD', 'CAD', 'CHF', 'CNY', 'SEK', 'NZD'], // fallback
  );
};

export const isValidCurrency = (value: string): boolean => {
  if (!value) return false;
  const validCurrencies = currency();
  return validCurrencies.has(value.toUpperCase());
};

export const now = (): Date => {
  return dayjs().toDate();
};

export const hexToRgb = (hex: string): string => {
  const cleaned = hex.trim().replace(/^#/, '');
  const is3 = /^[0-9a-fA-F]{3}$/.test(cleaned);
  const is6 = /^[0-9a-fA-F]{6}$/.test(cleaned);
  if (!is3 && !is6) return '0, 0, 0';
  const value = is3
    ? cleaned
        .split('')
        .map((c) => c + c)
        .join('')
    : cleaned;
  const bigint = parseInt(value, 16);
  const r = (bigint >> 16) & 255;
  const g = (bigint >> 8) & 255;
  const b = bigint & 255;
  return `${r}, ${g}, ${b}`;
};

/**
 * Safely retrieves a value from a nested object using a dot-separated path or array of keys.
 * Returns defaultValue if any intermediate value is null/undefined or if the final value is undefined.
 * @param obj - The object to traverse
 * @param path - Dot-separated string (e.g., "user.address.city") or array of keys
 * @param defaultValue - Value to return if path cannot be resolved
 * @returns The value at the path, or defaultValue
 */
export function get(obj: any, path: string | Array<string | number>, defaultValue?: any) {
  const segments = Array.isArray(path) ? path : path.split('.');
  let current = obj;

  for (const segment of segments) {
    if (current == null) return defaultValue;
    current = current[segment];
  }

  return current === undefined ? defaultValue : current;
}

/**
 * Sets a value at the specified path in an object, creating intermediate objects/arrays as needed.
 * WARNING: This function mutates the input object.
 * @param obj - The object to mutate
 * @param path - Dot-separated string or array of keys
 * @param value - Value to set at the path
 * @returns The mutated object (for chaining)
 */
export function set(obj: any, path: string | Array<string | number>, value: any) {
  const segments = Array.isArray(path) ? path : path.split('.');
  let current = obj;

  for (let i = 0; i < segments.length; i++) {
    const segment = segments[i];
    const isLast = i === segments.length - 1;

    if (isLast) {
      current[segment] = value;
    } else {
      if (
        !(segment in current) ||
        typeof current[segment] !== 'object' ||
        current[segment] == null
      ) {
        // Create array if next segment is a numeric index, otherwise create object
        const nextSegment = segments[i + 1];
        const next = typeof nextSegment === 'number' || /^\d+$/.test(String(nextSegment)) ? [] : {};
        current[segment] = next;
      }
      current = current[segment];
    }
  }

  return obj;
}

export function splitName(name: string): { firstName: string; lastName: string } {
  const parts = name.trim().split(/\s+/);
  return {
    firstName: parts[0] ?? '',
    lastName: parts.slice(1).join(' '),
  };
}

export function joinName(firstName: string, lastName?: string): string {
  return [firstName, lastName].filter(Boolean).join(' ');
}

/**
 * Deep equality check a given object payload
 * Returns true if the objects are deeply equal
 * Uses a proper deep comparison instead of JSON.stringify to avoid property ordering issues
 */
export function isPayloadEqual(a: any, b: any, visited = new WeakSet()): boolean {
  // Handle primitive types and null
  if (a === b) return true;
  if (a == null || b == null) return false;
  if (typeof a !== typeof b) return false;

  // Handle Date objects
  if (a instanceof Date && b instanceof Date) {
    return a.getTime() === b.getTime();
  }

  // Handle RegExp
  if (a instanceof RegExp && b instanceof RegExp) {
    return a.toString() === b.toString();
  }

  // Handle arrays
  if (Array.isArray(a) && Array.isArray(b)) {
    if (a.length !== b.length) return false;
    return a.every((item, index) => isPayloadEqual(item, b[index]));
  }

  // Handle objects
  if (typeof a === 'object' && typeof b === 'object') {
    // Check for circular references
    if (visited.has(a)) return true;
    visited.add(a);
    const keysA = Object.keys(a).sort();
    const keysB = Object.keys(b).sort();

    // Compare keys (order-independent)
    if (keysA.length !== keysB.length) return false;
    if (!keysA.every((key, index) => key === keysB[index])) return false;

    // Compare values recursively
    return keysA.every((key) => isPayloadEqual(a[key], b[key], visited));
  }

  // For other types (functions, symbols, etc.), use strict equality
  return a === b;
}
