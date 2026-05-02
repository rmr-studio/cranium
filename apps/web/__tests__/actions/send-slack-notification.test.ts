import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { sendSlackNotification } from '@/app/actions/send-slack-notification';

const WEBHOOK_URL = 'https://hooks.slack.com/services/T00/B00/xxxx';

describe('sendSlackNotification', () => {
  const originalEnv = process.env.SLACK_WEBHOOK_URL;

  beforeEach(() => {
    process.env.SLACK_WEBHOOK_URL = WEBHOOK_URL;
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    process.env.SLACK_WEBHOOK_URL = originalEnv;
    vi.restoreAllMocks();
  });

  it('returns error when SLACK_WEBHOOK_URL is not configured', async () => {
    process.env.SLACK_WEBHOOK_URL = '';

    const result = await sendSlackNotification({ name: 'Alex', email: 'alex@test.com' });

    expect(result).toEqual({ success: false, error: 'SLACK_WEBHOOK_URL is not configured' });
    expect(fetch).not.toHaveBeenCalled();
  });

  it('sends signup notification with name and email', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('ok', { status: 200 }));

    const result = await sendSlackNotification({ name: 'Alex', email: 'alex@test.com' });

    expect(result).toEqual({ success: true });
    expect(fetch).toHaveBeenCalledWith(WEBHOOK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: expect.any(String),
    });

    const body = JSON.parse(vi.mocked(fetch).mock.calls[0][1]!.body as string);
    expect(body.blocks[0].text.text).toBe('New Waitlist Signup');
    expect(body.blocks[1].fields).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ text: expect.stringContaining('Alex') }),
        expect.objectContaining({ text: expect.stringContaining('alex@test.com') }),
      ]),
    );
  });

  it('returns error on non-200 response', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('invalid_payload', { status: 400 }));

    const result = await sendSlackNotification({ name: 'Alex', email: 'alex@test.com' });

    expect(result).toEqual({
      success: false,
      error: 'Slack returned 400: invalid_payload',
    });
  });

  it('returns error on network failure', async () => {
    vi.mocked(fetch).mockRejectedValue(new Error('fetch failed'));

    const result = await sendSlackNotification({ name: 'Alex', email: 'alex@test.com' });

    expect(result).toEqual({ success: false, error: 'fetch failed' });
  });

  it('handles non-Error thrown values', async () => {
    vi.mocked(fetch).mockRejectedValue('string error');

    const result = await sendSlackNotification({ name: 'Alex', email: 'alex@test.com' });

    expect(result).toEqual({ success: false, error: 'Unknown error' });
  });

  it('POSTs to the correct URL with correct Content-Type', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response('ok', { status: 200 }));

    await sendSlackNotification({ name: 'Alex', email: 'alex@test.com' });

    expect(fetch).toHaveBeenCalledWith(
      WEBHOOK_URL,
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      }),
    );
  });
});
