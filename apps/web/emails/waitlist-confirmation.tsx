import { Body, Head, Hr, Html, Link, Preview, Text } from '@react-email/components';

// Monochrome palette — matches DESIGN.md typography-forward direction
const colors = {
  background: '#f7f7f7', // page background, light neutral
  card: '#ffffff', // content surface
  foreground: '#1a1a1a', // primary text, headings
  content: '#525252', // body text
  muted: '#a3a3a3', // secondary text, footer links
  border: '#e5e5e5', // dividers
  accent: '#3D2F1F', // Riven brand — subtle, earned
};

const font = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif';
const serifFont = 'Georgia, "Times New Roman", serif';

interface WaitlistConfirmationProps {
  name: string;
}

export function WaitlistConfirmation({ name }: WaitlistConfirmationProps) {
  return (
    <Html lang="en" dir="ltr">
      <Head />
      <Preview>I cannot wait to show you what I am building!</Preview>
      <Body
        style={{
          margin: 0,
          padding: 0,
          backgroundColor: colors.background,
          fontFamily: font,
          WebkitFontSmoothing: 'antialiased',
        }}
      >
        {/* Outer wrapper — subtle background visible on sides */}
        <table
          width="100%"
          cellPadding={0}
          cellSpacing={0}
          role="presentation"
          style={{ backgroundColor: colors.background }}
        >
          <tr>
            <td align="center" style={{ padding: '48px 24px' }}>
              {/* Content card — white, generous padding */}
              <table
                width={560}
                cellPadding={0}
                cellSpacing={0}
                role="presentation"
                style={{
                  backgroundColor: colors.card,
                  borderRadius: '8px',
                  border: `1px solid ${colors.border}`,
                }}
              >
                {/* ── Header — Riven wordmark ── */}
                <tr>
                  <td style={{ padding: '48px 48px 0' }}>
                    <Text
                      style={{
                        margin: 0,
                        fontSize: '18px',
                        fontWeight: 600,
                        fontFamily: serifFont,
                        color: colors.accent,
                        letterSpacing: '0.02em',
                      }}
                    >
                      Riven
                    </Text>
                  </td>
                </tr>

                {/* ── Divider ── */}
                <tr>
                  <td style={{ padding: '24px 48px 0' }}>
                    <Hr
                      style={{
                        margin: 0,
                        borderTop: `1px solid ${colors.border}`,
                        borderBottom: 'none',
                        borderLeft: 'none',
                        borderRight: 'none',
                      }}
                    />
                  </td>
                </tr>

                {/* ── Body content ── */}
                <tr>
                  <td style={{ padding: '40px 48px 0' }}>
                    {/* Greeting */}
                    <Text
                      style={{
                        margin: '0 0 32px',
                        fontSize: '26px',
                        fontWeight: 600,
                        fontFamily: serifFont,
                        color: colors.foreground,
                        lineHeight: '1.2',
                      }}
                    >
                      Hey {name},
                    </Text>

                    <Text
                      style={{
                        margin: '0 0 24px',
                        fontSize: '15px',
                        lineHeight: '26px',
                        color: colors.content,
                      }}
                    >
                      Thank you for joining the waitlist. We are working hard to build the best
                      customer lifecycle intelligence platform imaginable, and it is my goal to
                      ensure you both have an incredible experience once you get your hands on it
                      and that you feel like it was worth the wait.
                    </Text>

                    <Text
                      style={{
                        margin: '0 0 24px',
                        fontSize: '15px',
                        lineHeight: '26px',
                        color: colors.content,
                      }}
                    >
                      This is an open thread, so if you have any questions, suggestions, or just
                      want to say hi, feel free to reply to this email (please it gets so lonely
                      here).
                    </Text>

                    <Text
                      style={{
                        margin: '0 0 24px',
                        fontSize: '15px',
                        lineHeight: '26px',
                        color: colors.content,
                      }}
                    >
                      I read and respond to every email received, and I would love to hear from you.
                    </Text>

                    <Text
                      style={{
                        margin: '0 0 32px',
                        fontSize: '15px',
                        lineHeight: '26px',
                        color: colors.content,
                      }}
                    >
                      If you want to follow along with the build, you can find me online posting
                      updates and sneak peeks as we go!
                    </Text>
                  </td>
                </tr>

                {/* ── Sign-off ── */}
                <tr>
                  <td style={{ padding: '40px 48px 0' }}>
                    <Text
                      style={{
                        margin: 0,
                        fontSize: '15px',
                        lineHeight: '26px',
                        color: colors.content,
                      }}
                    >
                      Thanks :D,
                    </Text>
                    <Text
                      style={{
                        margin: '4px 0 0',
                        fontSize: '15px',
                        fontWeight: 600,
                        lineHeight: '26px',
                        color: colors.foreground,
                      }}
                    >
                      Jared
                    </Text>
                  </td>
                </tr>

                {/* ── Bottom padding ── */}
                <tr>
                  <td style={{ padding: '48px 0 0' }} />
                </tr>
              </table>

              {/* ── Footer — outside the card, minimal ── */}
              <table width={560} cellPadding={0} cellSpacing={0} role="presentation">
                <tr>
                  <td align="center" style={{ padding: '32px 48px' }}>
                    <Text
                      style={{
                        margin: 0,
                        fontSize: '12px',
                        lineHeight: '20px',
                        color: colors.muted,
                        fontFamily: font,
                      }}
                    >
                      <Link
                        href="https://getriven.io"
                        style={{ color: colors.muted, textDecoration: 'none' }}
                      >
                        getriven.io
                      </Link>
                      {' \u00B7 '}
                      <Link
                        href="mailto:jared@riven.software"
                        style={{ color: colors.muted, textDecoration: 'none' }}
                      >
                        jared@riven.software
                      </Link>
                    </Text>
                    <Text
                      style={{
                        margin: '8px 0 0',
                        fontSize: '12px',
                        lineHeight: '20px',
                        color: colors.muted,
                        fontFamily: font,
                        opacity: 0.7,
                      }}
                    >
                      &copy; {new Date().getFullYear()} Riven. All rights reserved.
                    </Text>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
        </table>
      </Body>
    </Html>
  );
}

// PreviewProps feeds sample data to the react-email preview server
WaitlistConfirmation.PreviewProps = {
  name: 'Alex',
} satisfies WaitlistConfirmationProps;

export default WaitlistConfirmation;
