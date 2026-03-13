import {
  normalizeApiError,
  isResponseError,
  fromError,
  ResponseError,
} from '@/lib/util/error/error.util';
import { ResponseError as OpenApiResponseError } from '@/lib/types';

function createMockOpenApiError(status: number, body: object | string): OpenApiResponseError {
  const isString = typeof body === 'string';
  const response = {
    status,
    statusText: 'Error',
    json: isString
      ? () => Promise.reject(new SyntaxError('Unexpected token'))
      : () => Promise.resolve(body),
  } as Response;

  const error = new OpenApiResponseError(response, 'API Error');
  return error;
}

describe('normalizeApiError', () => {
  it('normalizes OpenAPI ResponseError with valid JSON body', async () => {
    const apiError = createMockOpenApiError(400, {
      statusCode: 400,
      error: 'VALIDATION_ERROR',
      message: 'Invalid input',
    });

    await expect(normalizeApiError(apiError)).rejects.toMatchObject({
      status: 400,
      error: 'VALIDATION_ERROR',
      message: 'Invalid input',
    });
  });

  it('handles malformed JSON body gracefully', async () => {
    const apiError = createMockOpenApiError(500, 'not json');

    await expect(normalizeApiError(apiError)).rejects.toMatchObject({
      status: 500,
      error: 'API_ERROR',
    });
  });

  it('does not double-normalize an existing ResponseError', async () => {
    const existing: ResponseError = Object.assign(new Error('Already normalized'), {
      name: 'ResponseError',
      status: 422,
      error: 'ALREADY_NORMALIZED',
      message: 'Already normalized',
    });

    await expect(normalizeApiError(existing)).rejects.toMatchObject({
      status: 422,
      error: 'ALREADY_NORMALIZED',
      message: 'Already normalized',
    });
  });

  it('preserves original error as cause when available', async () => {
    const apiError = createMockOpenApiError(400, {
      statusCode: 400,
      error: 'BAD_REQUEST',
      message: 'Bad request',
    });

    await expect(normalizeApiError(apiError)).rejects.toMatchObject({
      cause: apiError,
    });
  });
});

describe('fromError', () => {
  it('returns existing ResponseError as-is', () => {
    const existing: ResponseError = Object.assign(new Error('test'), {
      name: 'ResponseError',
      status: 400,
      error: 'TEST',
      message: 'test',
    });
    expect(fromError(existing)).toBe(existing);
  });

  it('converts standard Error', () => {
    const error = new Error('Something failed');
    const result = fromError(error);
    expect(result.status).toBe(500);
    expect(result.message).toBe('Something failed');
  });

  it('converts unknown object with message', () => {
    const result = fromError({ message: 'Custom error', status: 503 });
    expect(result.status).toBe(503);
    expect(result.message).toBe('Custom error');
  });

  it('converts primitive error', () => {
    const result = fromError('string error');
    expect(result.message).toBe('string error');
    expect(result.status).toBe(500);
  });
});

describe('isResponseError', () => {
  it('returns true for valid ResponseError shape', () => {
    expect(isResponseError({ status: 400, error: 'ERR', message: 'msg' })).toBe(true);
  });

  it('returns false for plain Error', () => {
    expect(isResponseError(new Error('test'))).toBe(false);
  });

  it('returns false for null', () => {
    expect(isResponseError(null)).toBe(false);
  });
});
