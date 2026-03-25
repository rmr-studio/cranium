export const StripeIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 16 16" fill="none" className="flex-shrink-0">
    <circle cx="8" cy="8" r="8" fill="#635BFF" />
    <path
      fillRule="evenodd"
      clipRule="evenodd"
      d="M7.28 6.27C7.28 5.81 7.66 5.63 8.28 5.63C9.17 5.63 10.29 5.9 11.17 6.38V3.64C10.2 3.26 9.24 3.11 8.28 3.11C5.92 3.11 4.35 4.34 4.35 6.4C4.35 9.61 9.07 9.1 9.07 10.49C9.07 11.02 8.6 11.2 7.95 11.2C6.98 11.2 5.75 10.81 4.77 10.27V13.06C5.85 13.53 6.94 13.73 7.95 13.73C10.37 13.73 12.04 12.53 12.04 10.44C12.02 6.97 7.28 7.59 7.28 6.27Z"
      fill="white"
    />
  </svg>
);

export const IntercomIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#42A5F5" />
    <rect x="6.25" y="3.25" width="1.5" height="7" rx="0.75" fill="white" />
    <rect x="3.75" y="3.75" width="1.5" height="6" rx="0.75" fill="white" />
    <rect x="8.75" y="3.75" width="1.5" height="6" rx="0.75" fill="white" />
    <rect x="1.5" y="4.75" width="1.5" height="4" rx="0.75" fill="white" />
    <rect x="11" y="4.75" width="1.5" height="4" rx="0.75" fill="white" />
    <path d="M3.5 11C5 12.5 9 12.5 10.5 11" stroke="white" strokeWidth="0.75" strokeLinecap="round" fill="none" />
  </svg>
);

export const CompanyIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#67207A" />
    <path
      d="M7 6H7.01M7 8H7.01M7 4H7.01M9 6H9.01M9 8H9.01M9 4H9.01M5 6H5.01M5 8H5.01M5 4H5.01M5.5 12V10.5C5.5 10.22 5.72 10 6 10H8C8.28 10 8.5 10.22 8.5 10.5V12M4 3H10C10.55 3 11 3.45 11 4V11C11 11.55 10.55 12 10 12H4C3.45 12 3 11.55 3 11V4C3 3.45 3.45 3 4 3Z"
      stroke="white"
      strokeWidth="0.5"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const GmailIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={Math.round(size * 0.85)} viewBox="0 0 16 14" fill="none" className="flex-shrink-0">
    <path d="M0.5 3.1C0.5 2.1 1.3 1 2.2 1C3.1 1 3.8 2.1 3.8 3.1V11.5H2.2C1.3 11.5 0.5 10.7 0.5 9.8V3.1Z" fill="#0094FF" />
    <path d="M12.2 3.1C12.2 2.1 13 1 13.8 1C14.7 1 15.5 2.1 15.5 3.1V9.8C15.5 10.7 14.7 11.5 13.8 11.5H12.2V3.1Z" fill="#03A400" />
    <path d="M13.8 1.3C14.4 0.8 15.2 0.9 15.7 1.5C16.2 2.1 16.1 3 15.5 3.5L12.5 6L12 3.3L13.8 1.3Z" fill="#FFE600" />
    <path
      fillRule="evenodd"
      clipRule="evenodd"
      d="M0.7 1.8C1.1 1.1 2 0.9 2.7 1.4L8 5.5L12 3.3L12.5 6L8.2 9.5L1 4.5C0.3 4 0.2 3 0.7 1.8Z"
      fill="#FF0909"
      fillOpacity="0.86"
    />
  </svg>
);

export const CalendarIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#4A642B" />
    <path
      d="M5.5 2.5V4.5M8.5 2.5V4.5M3.5 6.5H10.5M6 9H8M7 8V10M4.5 4H9.5C10.05 4 10.5 4.45 10.5 5V11C10.5 11.55 10.05 12 9.5 12H4.5C3.95 12 3.5 11.55 3.5 11V5C3.5 4.45 3.95 4 4.5 4Z"
      stroke="white"
      strokeWidth="0.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const FeatureUsageIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#0E7490" />
    <path
      d="M3.5 10.5V7M5.75 10.5V5M8.25 10.5V7.5M10.5 10.5V3.5"
      stroke="white"
      strokeWidth="1"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const GoogleAdsIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#E8710A" />
    <path
      d="M4 10.5L9 3.5M5 10.5C5 11.05 4.55 11.5 4 11.5C3.45 11.5 3 11.05 3 10.5C3 9.95 3.45 9.5 4 9.5C4.55 9.5 5 9.95 5 10.5ZM11 4.5L7 10.5"
      stroke="white"
      strokeWidth="0.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const TeamMemberIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#4338CA" />
    <circle cx="7" cy="5.5" r="2" stroke="white" strokeWidth="0.75" />
    <path
      d="M3.5 11.5C3.5 9.57 5.07 8 7 8C8.93 8 10.5 9.57 10.5 11.5"
      stroke="white"
      strokeWidth="0.75"
      strokeLinecap="round"
    />
  </svg>
);

export const ChecklistIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#059669" />
    <path
      d="M4 5L5.5 6.5L8 4M4 9L5.5 10.5L8 8M9.5 5H11M9.5 9H11"
      stroke="white"
      strokeWidth="0.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const IntegrationIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#C2410C" />
    <path
      d="M5.5 5.5L3.5 7.5L5.5 9.5M8.5 5.5L10.5 7.5L8.5 9.5M7.5 4L6.5 11"
      stroke="white"
      strokeWidth="0.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const InvoiceIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#475569" />
    <path
      d="M4 3H10C10.28 3 10.5 3.22 10.5 3.5V11L9 10L7.5 11L6.5 10L5 11L3.5 10V3.5C3.5 3.22 3.72 3 4 3ZM5.5 5.5H8.5M5.5 7.5H7.5"
      stroke="white"
      strokeWidth="0.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const InfrastructureIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#991B1B" />
    <path
      d="M4 4H10C10.28 4 10.5 4.22 10.5 4.5V5.5C10.5 5.78 10.28 6 10 6H4C3.72 6 3.5 5.78 3.5 5.5V4.5C3.5 4.22 3.72 4 4 4ZM4 8H10C10.28 8 10.5 8.22 10.5 8.5V9.5C10.5 9.78 10.28 10 10 10H4C3.72 10 3.5 9.78 3.5 9.5V8.5C3.5 8.22 3.72 8 4 8ZM5 5H5.01M5 9H5.01"
      stroke="white"
      strokeWidth="0.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const ChangelogIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#7C3AED" />
    <path
      d="M5 3.5H9.5C9.78 3.5 10 3.72 10 4V10.5C10 10.78 9.78 11 9.5 11H4.5C4.22 11 4 10.78 4 10.5V4.5M5 3.5V4.5C5 4.78 4.78 5 4.5 5H4M5 3.5L4 4.5M5.5 7H8.5M5.5 9H7.5"
      stroke="white"
      strokeWidth="0.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const SalesPipelineIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#1D4ED8" />
    <path
      d="M3 4H11L9 7L11 10H3L5 7L3 4Z"
      stroke="white"
      strokeWidth="0.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const ApiUsageIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#0F766E" />
    <path
      d="M3.5 5L5.5 7L3.5 9M6.5 9H10.5"
      stroke="white"
      strokeWidth="0.85"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const HubspotIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#FF7A59" />
    <path
      d="m191.385 85.694v-29.506a22.722 22.722 0 0 0 13.101-20.48v-.677c0-12.549-10.173-22.722-22.721-22.722h-.678c-12.549 0-22.722 10.173-22.722 22.722v.677a22.722 22.722 0 0 0 13.101 20.48v29.506a64.342 64.342 0 0 0-30.594 13.47l-80.922-63.03c.577-2.083.878-4.225.912-6.375a25.6 25.6 0 1 0-25.633 25.55 25.323 25.323 0 0 0 12.607-3.43l79.685 62.007c-14.65 22.131-14.258 50.974.987 72.7l-24.236 24.243c-1.96-.626-4-.959-6.057-.987-11.607.01-21.01 9.423-21.007 21.03.003 11.606 9.412 21.014 21.018 21.017 11.607.003 21.02-9.4 21.03-21.007a20.747 20.747 0 0 0-.988-6.056l23.976-23.985c21.423 16.492 50.846 17.913 73.759 3.562 22.912-14.352 34.475-41.446 28.985-67.918-5.49-26.473-26.873-46.734-53.603-50.792m-9.938 97.044a33.17 33.17 0 1 1 0-66.316c17.85.625 32 15.272 32.01 33.134.008 17.86-14.127 32.522-31.977 33.165"
      fill="white"
      transform="translate(2.9, 3) scale(0.032)"
    />
  </svg>
);

export const ClockIcon = ({ size = 11 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="flex-shrink-0">
    <rect width="14" height="14" rx="2.5" fill="#D09D50" />
    <circle cx="7" cy="7" r="3.5" stroke="white" strokeWidth="0.75" />
    <path d="M7 5V7L8.5 8" stroke="white" strokeWidth="0.75" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);
