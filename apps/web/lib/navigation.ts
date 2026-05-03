export interface NavLink {
  label: string;
  href: string;
  external?: boolean;
  shouldCloseOnClick?: boolean;
}

/** Flat list for mobile nav */
export const NAV_LINKS_FLAT: NavLink[] = [
  { label: 'Blog', href: '/resources/blog' },
  { label: 'Changelog', href: '/resources/blog/category/changelog' },
  { label: 'FAQ', href: '/resources/faq' },
];

/** Alias used by Footer and MobileNavbar */
export const NAV_LINKS = NAV_LINKS_FLAT;
