export function scrollToSection(sectionId: string, href?: string) {
  const el = document.getElementById(sectionId);
  if (el) {
    el.scrollIntoView({ behavior: 'smooth' });
    window.history.replaceState(null, '', href ?? `/#${sectionId}`);
  } else {
    // On a different route — navigate to the home page with the hash
    window.location.href = href ?? `/#${sectionId}`;
  }
}

/**
 * Scrolls to the element matching the current URL hash after hydration.
 * Call once from a root client component's useEffect.
 * Retries briefly to handle elements that render after dynamic imports.
 */
export function scrollToHashOnLoad() {
  const hash = window.location.hash.slice(1);
  if (!hash) return;

  let attempts = 0;
  const maxAttempts = 20; // ~2 seconds total
  const interval = 100;

  const tryScroll = () => {
    const el = document.getElementById(hash);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth' });
      return;
    }
    attempts++;
    if (attempts < maxAttempts) {
      setTimeout(tryScroll, interval);
    }
  };

  // Small initial delay to let the first paint settle
  setTimeout(tryScroll, 50);
}
