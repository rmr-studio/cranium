'use server';

type JoinPayload = {
  type: 'join';
  name: string;
  email: string;
};

type SurveyPayload = {
  type: 'survey';
  name: string;
  email: string;
  businessOverview?: string;
  painPoints: string[];
  painPointsOther?: string;
  integrations: string[];
  involvement: string;
};

type SlackPayload = JoinPayload | SurveyPayload;

function buildJoinBlocks(payload: JoinPayload) {
  return [
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
}

function buildSurveyBlocks(payload: SurveyPayload) {
  const fields: { type: string; text: string }[] = [
    { type: 'mrkdwn', text: `*Name:*\n${payload.name}` },
    { type: 'mrkdwn', text: `*Email:*\n${payload.email}` },
  ];

  if (payload.businessOverview) {
    fields.push({ type: 'mrkdwn', text: `*Business Overview:*\n${payload.businessOverview}` });
  }

  if (payload.painPoints.length > 0) {
    const painText = payload.painPointsOther
      ? [...payload.painPoints, `Other: ${payload.painPointsOther}`].join('\n• ')
      : payload.painPoints.join('\n• ');
    fields.push({ type: 'mrkdwn', text: `*Pain Points:*\n• ${painText}` });
  }

  if (payload.integrations.length > 0) {
    fields.push({ type: 'mrkdwn', text: `*Integrations:*\n${payload.integrations.join(', ')}` });
  }

  fields.push({ type: 'mrkdwn', text: `*Involvement:*\n${payload.involvement}` });

  return [
    {
      type: 'header',
      text: { type: 'plain_text', text: 'Survey Completed', emoji: true },
    },
    {
      type: 'section',
      fields: fields.slice(0, 2),
    },
    ...(fields.length > 2
      ? fields.slice(2).map((field) => ({
          type: 'section' as const,
          text: field,
        }))
      : []),
  ];
}

export async function sendSlackNotification(
  payload: SlackPayload,
): Promise<{ success: boolean; error?: string }> {
  const webhookUrl = process.env.SLACK_WEBHOOK_URL;

  if (!webhookUrl) {
    return { success: false, error: 'SLACK_WEBHOOK_URL is not configured' };
  }

  try {
    const blocks =
      payload.type === 'join' ? buildJoinBlocks(payload) : buildSurveyBlocks(payload);

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
