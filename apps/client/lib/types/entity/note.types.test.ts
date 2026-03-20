import { extractNoteTitle, formatNoteTimestamp } from './note.types';

describe('extractNoteTitle', () => {
  it('extracts text from BlockNote paragraph block', () => {
    const content = [
      { type: 'paragraph', content: [{ type: 'text', text: 'Hello World', styles: {} }], props: {}, children: [] }
    ];
    expect(extractNoteTitle(content)).toBe('Hello World');
  });

  it('returns empty string for empty content', () => {
    expect(extractNoteTitle([])).toBe('');
  });

  it('returns empty string for null/undefined', () => {
    expect(extractNoteTitle(null as any)).toBe('');
    expect(extractNoteTitle(undefined as any)).toBe('');
  });

  it('handles blocks with nested inline content (links)', () => {
    const content = [
      { type: 'paragraph', content: [
        { type: 'text', text: 'Check ', styles: {} },
        { type: 'link', href: 'https://example.com', content: [{ type: 'text', text: 'this link', styles: {} }] },
      ], props: {}, children: [] }
    ];
    expect(extractNoteTitle(content)).toBe('Check this link');
  });

  it('truncates long titles to 100 characters', () => {
    const longText = 'a'.repeat(200);
    const content = [
      { type: 'paragraph', content: [{ type: 'text', text: longText, styles: {} }], props: {}, children: [] }
    ];
    expect(extractNoteTitle(content)).toHaveLength(100);
  });
});

describe('formatNoteTimestamp', () => {
  it('returns relative time for recent dates', () => {
    const now = new Date();
    const fiveMinAgo = new Date(now.getTime() - 5 * 60 * 1000);
    expect(formatNoteTimestamp(fiveMinAgo)).toBe('5m ago');
  });

  it('returns hours for dates within 24h', () => {
    const now = new Date();
    const threeHoursAgo = new Date(now.getTime() - 3 * 3600 * 1000);
    expect(formatNoteTimestamp(threeHoursAgo)).toBe('3h ago');
  });

  it('returns days for dates within a week', () => {
    const now = new Date();
    const twoDaysAgo = new Date(now.getTime() - 2 * 86400 * 1000);
    expect(formatNoteTimestamp(twoDaysAgo)).toBe('2d ago');
  });

  it('returns formatted date for older dates', () => {
    const oldDate = new Date('2024-03-10T12:00:00Z');
    const result = formatNoteTimestamp(oldDate);
    expect(result).toContain('Mar');
    expect(result).toContain('10');
  });

  it('returns "Just now" for very recent dates', () => {
    const now = new Date();
    expect(formatNoteTimestamp(now)).toBe('Just now');
  });

  it('handles string dates', () => {
    const fiveMinAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString();
    expect(formatNoteTimestamp(fiveMinAgo)).toBe('5m ago');
  });

  it('returns empty string for undefined', () => {
    expect(formatNoteTimestamp(undefined)).toBe('');
  });
});
