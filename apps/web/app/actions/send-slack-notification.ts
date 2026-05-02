'use server';

type SlackPayload = {
  name: string;
  email: string;
};

export async function sendSlackNotification(
  payload: SlackPayload,
): Promise<{ success: boolean; error?: string }> {
  const webhookUrl = process.env.SLACK_WEBHOOK_URL;

  if (!webhookUrl) {
    return { success: false, error: 'SLACK_WEBHOOK_URL is not configured' };
  }

  try {
    const blocks = [
      {
        type: 'header',
        text: { type: 'plain_text', text: 'New Waitlist Signup', emoji: true },
      },
      {
        type: 'section',
        fields: [
          { type: 'mrkdwn', text: `*Name:*\n${payload.name}` },
          { type: 'mrkdwn', text: `*Email:*\n${payload.email}` },
        ],
      },
    ];

    const response = await fetch(webhookUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ blocks }),
    });

    if (!response.ok) {
      const text = await response.text();
      return { success: false, error: `Slack returned ${response.status}: ${text}` };
    }

    return { success: true };
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    return { success: false, error: message };
  }
}
