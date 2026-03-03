import { Body, Head, Html, Img, Link, Preview, Text } from '@react-email/components';

// Amber theme palette — matches app globals.css
const colors = {
  background: '#e6dac8', // oklch(0.93 0.032 80)
  card: '#F5EDE0', // oklch(0.91 0.035 78)
  foreground: '#3D2F1F', // oklch(0.25 0.02 60)
  primary: '#5C3D1F', // oklch(0.35 0.04 65)
  content: '#7A5433', // oklch(0.45 0.04 65)
  accent: '#C8985F', // oklch(0.75 0.1 70)
  muted: '#EDE2D3', // oklch(0.88 0.04 75)
  mutedForeground: '#7A5433', // oklch(0.45 0.03 65)
  logoPrimary: '#57493D', // oklch(0.35 0 0)
  // Nav-inverse (amber) — used for contrasting footer
  navInvBackground: '#3D2F1F', // oklch(0.25 0.02 60)
  navInvForeground: '#F5EDE0', // oklch(0.93 0.032 80)
  navInvMuted: '#C8985F', // oklch(0.75 0.06 70)
  navInvLogoPrimary: '#D9CFC5', // oklch(0.85 0 0)
};

const font = '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif';
const serifFont = 'Georgia, "Times New Roman", serif';

interface WaitlistConfirmationProps {
  name: string;
}

export function WaitlistConfirmation({ name }: WaitlistConfirmationProps) {
  // Hardcoded fallback ensures template renders in all environments
  // NEXT_PUBLIC_CDN_URL is optional in env.ts — do NOT use validated env() here
  const cdnBase = process.env.NEXT_PUBLIC_CDN_URL ?? 'https://cdn.riven.software';

  return (
    <Html lang="en" dir="ltr" style={{ height: '100%', margin: 0, padding: 0 }}>
      <Head />
      <Preview>We cannot wait to show you what we are building!</Preview>
      <Body
        style={{
          margin: 0,
          padding: 0,
          backgroundColor: colors.background,
          height: '100%',
          width: '100%',
        }}
      >
        {/* Full-height outer wrapper — background visible on sides */}
        <table
          width="100%"
          cellPadding={0}
          cellSpacing={0}
          role="presentation"
          style={{ height: '100%', minHeight: '100%', backgroundColor: colors.background }}
        >
          <tr>
            <td align="center" valign="top">
              {/* Centered 600px column — stretches top to bottom */}
              <table
                width={600}
                cellPadding={0}
                cellSpacing={0}
                role="presentation"
                style={{ height: '100%', minHeight: '100%' }}
              >
                {/* ── Content card row ── */}
                <tr>
                  <td>
                    <table width="100%" cellPadding={0} cellSpacing={0} role="presentation">
                      <tr>
                        {/* Left accent strip */}
                        <td
                          width={4}
                          style={{ backgroundColor: colors.primary, fontSize: 0, lineHeight: 0 }}
                        >
                          &nbsp;
                        </td>
                        {/* Content */}
                        <td style={{ backgroundColor: colors.card, padding: '0 36px' }}>
                          {/* Logo + wordmark */}
                          <table
                            cellPadding={0}
                            cellSpacing={0}
                            role="presentation"
                            style={{ marginTop: '32px' }}
                          >
                            <tr>
                              <td style={{ verticalAlign: 'middle' }}>
                                <Img
                                  src={`${cdnBase}/images/email/email-riven-logo.png`}
                                  width={28}
                                  height={28}
                                  alt=""
                                />
                              </td>
                              <td style={{ verticalAlign: 'middle', paddingLeft: '4px' }}>
                                <Text
                                  style={{
                                    margin: 0,
                                    fontSize: '22px',
                                    fontWeight: 700,
                                    fontFamily: serifFont,
                                    color: colors.logoPrimary,
                                  }}
                                >
                                  Riven
                                </Text>
                              </td>
                            </tr>
                          </table>

                          {/* Greeting */}
                          <Text
                            style={{
                              margin: '28px 0 16px',
                              fontSize: '24px',
                              fontWeight: 600,
                              fontFamily: serifFont,
                              color: colors.foreground,
                            }}
                          >
                            Hey {name},
                          </Text>
                          <Text
                            style={{
                              margin: '0 0 16px',
                              fontSize: '15px',
                              lineHeight: '24px',
                              color: colors.content,
                              fontFamily: font,
                            }}
                          >
                            Thank you for joining the waitlist. We are hard at work building the
                            best platform Imaginable for operational intelligence, and it is our
                            goal to ensure you have an incredible experience once you get your hands
                            on it.
                          </Text>

                          <Text
                            style={{
                              margin: '0 0 16px',
                              fontSize: '15px',
                              lineHeight: '24px',
                              color: colors.content,
                              fontFamily: font,
                            }}
                          >
                            This is an open thread, so if you have any questions, suggestions, or
                            just want to say hi, feel free to reply to this email (please it gets so
                            lonely here).
                          </Text>
                          <Text
                            style={{
                              margin: '0 0 16px',
                              fontSize: '15px',
                              lineHeight: '24px',
                              color: colors.content,
                              fontFamily: font,
                            }}
                          >
                            We read and respond to every email we receive, and we would love to hear
                            from you.
                          </Text>
                          <Text
                            style={{
                              margin: '0 0 24px',
                              fontSize: '15px',
                              lineHeight: '24px',
                              color: colors.content,
                              fontFamily: font,
                            }}
                          >
                            If you want to follow along with the build, you can find me online
                            posting updates and sneak peeks as we go!
                          </Text>

                          {/* CTA button */}
                          <table
                            cellPadding={0}
                            cellSpacing={0}
                            role="presentation"
                            style={{ marginBottom: '36px' }}
                          >
                            <tr>
                              <td
                                style={{
                                  backgroundColor: colors.primary,
                                  borderRadius: '8px',
                                }}
                              >
                                <a
                                  href="https://x.com/dawadt"
                                  style={{
                                    display: 'inline-block',
                                    padding: '12px 24px',
                                    fontSize: '14px',
                                    fontWeight: 500,
                                    fontFamily: font,
                                    color: colors.background,
                                    textDecoration: 'none',
                                  }}
                                >
                                  Follow our journey
                                </a>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>

                {/* ── Footer — dark inverse, fills remaining height ── */}
                <tr>
                  <td
                    valign="top"
                    style={{
                      height: '100%',
                      backgroundColor: colors.navInvBackground,
                      padding: '28px 36px 32px',
                    }}
                  >
                    {/* Brand name + website */}
                    <Text
                      style={{
                        margin: '20px 0 0',
                        fontSize: '14px',
                        fontWeight: 600,
                        fontFamily: serifFont,
                        color: colors.navInvForeground,
                      }}
                    >
                      Riven
                    </Text>
                    <Link
                      href="https://getriven.io"
                      style={{
                        display: 'block',
                        margin: '2px 0 0',
                        fontSize: '13px',
                        fontFamily: font,
                        color: colors.navInvMuted,
                        textDecoration: 'none',
                      }}
                    >
                      getriven.io
                    </Link>

                    {/* Contact email */}
                    <Link
                      href="mailto:jared@riven.software"
                      style={{
                        display: 'block',
                        margin: '4px 0 0',
                        fontSize: '13px',
                        fontFamily: font,
                        color: colors.navInvMuted,
                        textDecoration: 'none',
                      }}
                    >
                      jared@riven.software
                    </Link>

                    {/* Copyright */}
                    <Text
                      style={{
                        margin: '20px 0 0',
                        fontSize: '11px',
                        fontFamily: font,
                        color: colors.navInvMuted,
                        opacity: 0.6,
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
