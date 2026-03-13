import { isSaveEntityResponse } from './error.util';

describe('isSaveEntityResponse', () => {
  it('returns true for response with entity', () => {
    expect(isSaveEntityResponse({ entity: { id: '123' } })).toBe(true);
  });

  it('returns true for response with errors array', () => {
    expect(isSaveEntityResponse({ errors: ['Field is required'] })).toBe(true);
  });

  it('returns true for response with both entity and errors', () => {
    expect(isSaveEntityResponse({ entity: { id: '123' }, errors: ['warning'] })).toBe(true);
  });

  it('returns false for ErrorResponse shape (error string + message)', () => {
    expect(
      isSaveEntityResponse({
        error: 'INVALID_RELATIONSHIP',
        message: 'Target entity is already linked',
      }),
    ).toBe(false);
  });

  it('returns false for null', () => {
    expect(isSaveEntityResponse(null)).toBe(false);
  });

  it('returns false for undefined', () => {
    expect(isSaveEntityResponse(undefined)).toBe(false);
  });

  it('returns false for string', () => {
    expect(isSaveEntityResponse('error')).toBe(false);
  });

  it('returns false for number', () => {
    expect(isSaveEntityResponse(42)).toBe(false);
  });

  it('returns false for empty object', () => {
    expect(isSaveEntityResponse({})).toBe(false);
  });

  it('returns false when errors is a string (not an array)', () => {
    expect(isSaveEntityResponse({ errors: 'not an array' })).toBe(false);
  });
});
