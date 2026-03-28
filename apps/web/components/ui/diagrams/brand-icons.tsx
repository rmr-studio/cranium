export function BrandIntercom({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="#42A5F5" />
      <rect x="6.25" y="3.25" width="1.5" height="7" rx="0.75" fill="white" />
      <rect x="3.75" y="3.75" width="1.5" height="6" rx="0.75" fill="white" />
      <rect x="8.75" y="3.75" width="1.5" height="6" rx="0.75" fill="white" />
      <rect x="1.5" y="4.75" width="1.5" height="4" rx="0.75" fill="white" />
      <rect x="11" y="4.75" width="1.5" height="4" rx="0.75" fill="white" />
      <path
        d="M3.5 11C5 12.5 9 12.5 10.5 11"
        stroke="white"
        strokeWidth="0.75"
        strokeLinecap="round"
        fill="none"
      />
    </svg>
  );
}

export function BrandStripe({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className="shrink-0">
      <circle cx="8" cy="8" r="8" fill="#635BFF" />
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M7.28 6.27C7.28 5.81 7.66 5.63 8.28 5.63C9.17 5.63 10.29 5.9 11.17 6.38V3.64C10.2 3.26 9.24 3.11 8.28 3.11C5.92 3.11 4.35 4.34 4.35 6.4C4.35 9.61 9.07 9.1 9.07 10.49C9.07 11.02 8.6 11.2 7.95 11.2C6.98 11.2 5.75 10.81 4.77 10.27V13.06C5.85 13.53 6.94 13.73 7.95 13.73C10.37 13.73 12.04 12.53 12.04 10.44C12.02 6.97 7.28 7.59 7.28 6.27Z"
        fill="white"
      />
    </svg>
  );
}

export function BrandGmail({ size = 14 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={Math.round(size * 0.85)}
      viewBox="0 0 16 14"
      fill="none"
      className="shrink-0"
    >
      <path
        d="M0.5 3.1C0.5 2.1 1.3 1 2.2 1C3.1 1 3.8 2.1 3.8 3.1V11.5H2.2C1.3 11.5 0.5 10.7 0.5 9.8V3.1Z"
        fill="#0094FF"
      />
      <path
        d="M12.2 3.1C12.2 2.1 13 1 13.8 1C14.7 1 15.5 2.1 15.5 3.1V9.8C15.5 10.7 14.7 11.5 13.8 11.5H12.2V3.1Z"
        fill="#03A400"
      />
      <path
        d="M13.8 1.3C14.4 0.8 15.2 0.9 15.7 1.5C16.2 2.1 16.1 3 15.5 3.5L12.5 6L12 3.3L13.8 1.3Z"
        fill="#FFE600"
      />
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M0.7 1.8C1.1 1.1 2 0.9 2.7 1.4L8 5.5L12 3.3L12.5 6L8.2 9.5L1 4.5C0.3 4 0.2 3 0.7 1.8Z"
        fill="#FF0909"
        fillOpacity="0.86"
      />
    </svg>
  );
}

export function BrandHubSpot({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="#FF7A59" />
      <path
        d="m191.385 85.694v-29.506a22.722 22.722 0 0 0 13.101-20.48v-.677c0-12.549-10.173-22.722-22.721-22.722h-.678c-12.549 0-22.722 10.173-22.722 22.722v.677a22.722 22.722 0 0 0 13.101 20.48v29.506a64.342 64.342 0 0 0-30.594 13.47l-80.922-63.03c.577-2.083.878-4.225.912-6.375a25.6 25.6 0 1 0-25.633 25.55 25.323 25.323 0 0 0 12.607-3.43l79.685 62.007c-14.65 22.131-14.258 50.974.987 72.7l-24.236 24.243c-1.96-.626-4-.959-6.057-.987-11.607.01-21.01 9.423-21.007 21.03.003 11.606 9.412 21.014 21.018 21.017 11.607.003 21.02-9.4 21.03-21.007a20.747 20.747 0 0 0-.988-6.056l23.976-23.985c21.423 16.492 50.846 17.913 73.759 3.562 22.912-14.352 34.475-41.446 28.985-67.918-5.49-26.473-26.873-46.734-53.603-50.792m-9.938 97.044a33.17 33.17 0 1 1 0-66.316c17.85.625 32 15.272 32.01 33.134.008 17.86-14.127 32.522-31.977 33.165"
        fill="white"
        transform="translate(2.9, 3) scale(0.032)"
      />
    </svg>
  );
}

export function BrandLinkedIn({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="#0A66C2" />
      <path
        d="M4.5 6v4M4.5 4v.01M6.75 6v4M6.75 8.25c0-1.25 2.5-1.25 2.5 0V10"
        stroke="white"
        strokeWidth="1.1"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function BrandSlack({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="#4A154B" />
      <path
        d="M5.5 3.5a1 1 0 1 1 0 2H4M5.5 5.5V7M8.5 3.5a1 1 0 1 0 0 2H10M8.5 5.5V4M5.5 10.5a1 1 0 1 0 0-2H4M5.5 8.5V7M8.5 10.5a1 1 0 1 1 0-2H10M8.5 8.5v1.5"
        stroke="white"
        strokeWidth="0.8"
        strokeLinecap="round"
      />
    </svg>
  );
}

export function BrandShopify({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="#96BF48" />
      <path
        d="M9.5 3.5s-.3-.1-.7-.1c-.1-.4-.4-.7-.7-.8-.2-.1-.4 0-.5 0C7.4 2.4 7.2 2.2 7 2.2c-.9 0-1.4 1.1-1.6 1.7h-.8L4.5 4l-.1.3 1 .3L4.5 11l3.5.8L10 4c0-.1-.3-.4-.5-.5z"
        fill="white"
        fillOpacity="0.9"
      />
    </svg>
  );
}

export function BrandFacebook({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="#1877F2" />
      <path
        d="M9 2.5H8c-1.1 0-2 .9-2 2V6H4.5v2H6v4h2V8h1.5L10 6H8V4.75c0-.28.22-.5.5-.5H9.5V2.5H9z"
        fill="white"
      />
    </svg>
  );
}

export function BrandInstagram({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="url(#ig-grad)" />
      <defs>
        <linearGradient id="ig-grad" x1="0" y1="14" x2="14" y2="0">
          <stop offset="0%" stopColor="#FD5" />
          <stop offset="50%" stopColor="#FF543E" />
          <stop offset="100%" stopColor="#C837AB" />
        </linearGradient>
      </defs>
      <rect x="3" y="3" width="8" height="8" rx="2" stroke="white" strokeWidth="1" />
      <circle cx="7" cy="7" r="2" stroke="white" strokeWidth="0.9" />
      <circle cx="9.75" cy="4.25" r="0.6" fill="white" />
    </svg>
  );
}

export function BrandGoogle({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="white" />
      <rect x="0.5" y="0.5" width="13" height="13" rx="2" stroke="#ddd" strokeWidth="0.5" />
      <path
        d="M10.5 7.16c0-.37-.03-.73-.1-1.07H7.13v2.02h1.9a1.63 1.63 0 0 1-.7 1.07v.88h1.14c.66-.61 1.03-1.52 1.03-2.9z"
        fill="#4285F4"
      />
      <path
        d="M7.13 10.93c.95 0 1.74-.31 2.33-.85l-1.14-.88c-.31.21-.72.34-1.19.34-.91 0-1.69-.62-1.96-1.45H3.99v.91a3.52 3.52 0 0 0 3.14 1.93z"
        fill="#34A853"
      />
      <path
        d="M5.17 8.09a2.13 2.13 0 0 1 0-2.17v-.91H3.99a3.52 3.52 0 0 0 0 3.99l1.18-.91z"
        fill="#FBBC05"
      />
      <path
        d="M7.13 4.47c.51 0 .97.18 1.34.52l1-.99A3.43 3.43 0 0 0 7.13 3a3.52 3.52 0 0 0-3.14 1.92l1.18.91c.27-.83 1.05-1.36 1.96-1.36z"
        fill="#EA4335"
      />
    </svg>
  );
}

export function BrandGorgias({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="#1F1F1F" />
      <path
        d="M4.5 5.5h5M4.5 7h3.5M4.5 8.5h4.5"
        stroke="#FFD060"
        strokeWidth="1"
        strokeLinecap="round"
      />
    </svg>
  );
}

export function BrandKlaviyo({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="#24BE74" />
      <path d="M3.5 10L7 4l3.5 6H8.75L7 7.5 5.25 10H3.5z" fill="white" />
    </svg>
  );
}
