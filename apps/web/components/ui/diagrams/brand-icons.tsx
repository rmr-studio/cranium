export type Integration =
  | 'Intercom'
  | 'Stripe'
  | 'Gmail'
  | 'HubSpot'
  | 'LinkedIn'
  | 'Slack'
  | 'Shopify'
  | 'Facebook'
  | 'Instagram'
  | 'Gorgias'
  | 'Klaviyo'
  | 'GoogleSheets'
  | 'Claude'
  | 'Google'
  | 'Cin7'
  | 'GoogleMeet'
  | 'Claude';

export const BrandIcons: Record<Integration, React.FC<{ size?: number }>> = {
  Intercom: BrandIntercom,
  Stripe: BrandStripe,
  Gmail: BrandGmail,
  HubSpot: BrandHubSpot,
  LinkedIn: BrandLinkedIn,
  Slack: BrandSlack,
  Shopify: BrandShopify,
  Facebook: BrandFacebook,
  Instagram: BrandInstagram,
  Gorgias: BrandGorgias,
  Klaviyo: BrandKlaviyo,
  Google: BrandGoogle,
  GoogleSheets: BrandGoogleSheets,
  Claude: BrandClaude,
  Cin7: BrandCin7,
  GoogleMeet: BrandGoogleMeet,
};

export function BrandCompany({ size = 14 }: { size?: number }) {
  return <></>;
}

export function BrandZoom({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 14 14" fill="none" className="shrink-0">
      <rect width="14" height="14" rx="2.5" fill="#2D8CFF" />
      <rect x="3" y="4.25" width="6.25" height="5.5" rx="1.1" fill="white" />
      <path d="M9.5 6.25 12 4.5v5L9.5 7.75v-1.5Z" fill="white" />
    </svg>
  );
}

export function BrandGoogleSheets({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className="shrink-0">
      <path
        d="M11.318 12.545H7.91v-1.909h3.41v1.91zM14.728 0v6h6l-6-6zm1.363 10.636h-3.41v1.91h3.41v-1.91zm0 3.273h-3.41v1.91h3.41v-1.91zM20.727 6.5v15.864c0 .904-.732 1.636-1.636 1.636H4.909a1.636 1.636 0 0 1-1.636-1.636V1.636C3.273.732 4.005 0 4.909 0h9.318v6.5h6.5zm-3.273 2.773H6.545v7.909h10.91v-7.91zm-6.136 4.636H7.91v1.91h3.41v-1.91z"
        fill="#0F9D58"
      />
    </svg>
  );
}

export function BrandClaude({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className="shrink-0">
      <path
        d="m4.7144 15.9555 4.7174-2.6471.079-.2307-.079-.1275h-.2307l-.7893-.0486-2.6956-.0729-2.3375-.0971-2.2646-.1214-.5707-.1215-.5343-.7042.0546-.3522.4797-.3218.686.0608 1.5179.1032 2.2767.1578 1.6514.0972 2.4468.255h.3886l.0546-.1579-.1336-.0971-.1032-.0972L6.973 9.8356l-2.55-1.6879-1.3356-.9714-.7225-.4918-.3643-.4614-.1578-1.0078.6557-.7225.8803.0607.2246.0607.8925.686 1.9064 1.4754 2.4893 1.8336.3643.3035.1457-.1032.0182-.0728-.164-.2733-1.3539-2.4467-1.445-2.4893-.6435-1.032-.17-.6194c-.0607-.255-.1032-.4674-.1032-.7285L6.287.1335 6.6997 0l.9957.1336.419.3642.6192 1.4147 1.0018 2.2282 1.5543 3.0296.4553.8985.2429.8318.091.255h.1579v-.1457l.1275-1.706.2368-2.0947.2307-2.6957.0789-.7589.3764-.9107.7468-.4918.5828.2793.4797.686-.0668.4433-.2853 1.8517-.5586 2.9021-.3643 1.9429h.2125l.2429-.2429.9835-1.3053 1.6514-2.0643.7286-.8196.85-.9046.5464-.4311h1.0321l.759 1.1293-.34 1.1657-1.0625 1.3478-.8804 1.1414-1.2628 1.7-.7893 1.36.0729.1093.1882-.0183 2.8535-.607 1.5421-.2794 1.8396-.3157.8318.3886.091.3946-.3278.8075-1.967.4857-2.3072.4614-3.4364.8136-.0425.0304.0486.0607 1.5482.1457.6618.0364h1.621l3.0175.2247.7892.522.4736.6376-.079.4857-1.2142.6193-1.6393-.3886-3.825-.9107-1.3113-.3279h-.1822v.1093l1.0929 1.0686 2.0035 1.8092 2.5075 2.3314.1275.5768-.3218.4554-.34-.0486-2.2039-1.6575-.85-.7468-1.9246-1.621h-.1275v.17l.4432.6496 2.3436 3.5214.1214 1.0807-.17.3521-.6071.2125-.6679-.1214-1.3721-1.9246L14.38 17.959l-1.1414-1.9428-.1397.079-.674 7.2552-.3156.3703-.7286.2793-.6071-.4614-.3218-.7468.3218-1.4753.3886-1.9246.3157-1.53.2853-1.9004.17-.6314-.0121-.0425-.1397.0182-1.4328 1.9672-2.1796 2.9446-1.7243 1.8456-.4128.164-.7164-.3704.0667-.6618.4008-.5889 2.386-3.0357 1.4389-1.882.929-1.0868-.0062-.1579h-.0546l-6.3385 4.1164-1.1293.1457-.4857-.4554.0608-.7467.2307-.2429 1.9064-1.3114Z"
        fill="#D97757"
      />
    </svg>
  );
}

export function BrandGoogleMeet({ size = 14 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={Math.round(size * 0.85)}
      viewBox="-13.1265 -18 113.763 108"
      fill="none"
      className="shrink-0"
    >
      <path d="M49.5 36l8.53 9.75 11.47 7.33 2-17.02-2-16.64-11.69 6.44z" fill="#00832d" />
      <path d="M0 51.5V66c0 3.315 2.685 6 6 6h14.5l3-10.96-3-9.54-9.95-3z" fill="#0066da" />
      <path d="M20.5 0L0 20.5l10.55 3 9.95-3 2.95-9.41z" fill="#e94235" />
      <path d="M20.5 20.5H0v31h20.5z" fill="#2684fc" />
      <path
        d="M82.6 8.68L69.5 19.42v33.66l13.16 10.79c1.97 1.54 4.85.135 4.85-2.37V11c0-2.535-2.945-3.925-4.91-2.32zM49.5 36v15.5h-29V72h43c3.315 0 6-2.685 6-6V53.08z"
        fill="#00ac47"
      />
      <path d="M63.5 0h-43v20.5h29V36l20-16.57V6c0-3.315-2.685-6-6-6z" fill="#ffba00" />
    </svg>
  );
}

export function BrandCin7({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 55.8 54.4" fill="none" className="shrink-0">
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M18.4,49.9l5,2.4l1.5,0.7l-7.7-33.6h-5.7L18.4,49.9z"
        fill="#7F96FF"
      />
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M5.5,43.7l6.5,3.1L5.7,19.4H0L5.5,43.7z"
        fill="#7F96FF"
      />
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M32.3,35.2l-3.6-15.8H23l6.5,28.3L32.3,35.2z"
        fill="#7F96FF"
      />
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M39.1,49.1l-5.6,2.7L44.2,4.9l3.9,4.9L39.1,49.1L39.1,49.1z"
        fill="#002DFF"
      />
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M50.3,43.7l-5.6,2.7L52,14.6l3.9,4.9L50.3,43.7L50.3,43.7z"
        fill="#002DFF"
      />
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M15.5,0l-3.9,4.9h22.6l-1.1,4.9H7.7l-3.9,4.9H32l-1.1,4.9H0l1.1,4.9h28.7l-6.4,28l4.5,2.2L40.3,0H15.5z"
        fill="#002DFF"
      />
    </svg>
  );
}

export function BrandNotion({ size = 14 }: { size?: number }) {
  return <></>;
}

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
      height={Math.round(size * 0.75)}
      viewBox="-13.2 -16.50405 114.4 99.0243"
      fill="none"
      className="shrink-0"
    >
      <path d="M6 66.0162h14v-34l-20-15v43c0 3.315 2.685 6 6 6z" fill="#4285f4" />
      <path d="M68 66.0162h14c3.315 0 6-2.685 6-6v-43l-20 15z" fill="#34a853" />
      <path d="M68 6.0162v26l20-15v-8c0-7.415-8.465-11.65-14.4-7.2z" fill="#fbbc04" />
      <path d="M20 32.0162v-26l24 18 24-18v26l-24 18z" fill="#ea4335" />
      <path d="M0 9.0162v8l20 15v-26l-5.6-4.2c-5.935-4.45-14.4-.215-14.4 7.2z" fill="#c5221f" />
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
    <svg width={size} height={size} viewBox="0 0 127 127" fill="none" className="shrink-0">
      <path
        d="M27.2 80c0 7.3-5.9 13.2-13.2 13.2C6.7 93.2.8 87.3.8 80c0-7.3 5.9-13.2 13.2-13.2h13.2V80zm6.6 0c0-7.3 5.9-13.2 13.2-13.2 7.3 0 13.2 5.9 13.2 13.2v33c0 7.3-5.9 13.2-13.2 13.2-7.3 0-13.2-5.9-13.2-13.2V80z"
        fill="#E01E5A"
      />
      <path
        d="M47 27c-7.3 0-13.2-5.9-13.2-13.2C33.8 6.5 39.7.6 47 .6c7.3 0 13.2 5.9 13.2 13.2V27H47zm0 6.7c7.3 0 13.2 5.9 13.2 13.2 0 7.3-5.9 13.2-13.2 13.2H13.9C6.6 60.1.7 54.2.7 46.9c0-7.3 5.9-13.2 13.2-13.2H47z"
        fill="#36C5F0"
      />
      <path
        d="M99.9 46.9c0-7.3 5.9-13.2 13.2-13.2 7.3 0 13.2 5.9 13.2 13.2 0 7.3-5.9 13.2-13.2 13.2H99.9V46.9zm-6.6 0c0 7.3-5.9 13.2-13.2 13.2-7.3 0-13.2-5.9-13.2-13.2V13.8C66.9 6.5 72.8.6 80.1.6c7.3 0 13.2 5.9 13.2 13.2v33.1z"
        fill="#2EB67D"
      />
      <path
        d="M80.1 99.8c7.3 0 13.2 5.9 13.2 13.2 0 7.3-5.9 13.2-13.2 13.2-7.3 0-13.2-5.9-13.2-13.2V99.8h13.2zm0-6.6c-7.3 0-13.2-5.9-13.2-13.2 0-7.3 5.9-13.2 13.2-13.2h33.1c7.3 0 13.2 5.9 13.2 13.2 0 7.3-5.9 13.2-13.2 13.2H80.1z"
        fill="#ECB22E"
      />
    </svg>
  );
}

export function BrandShopify({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" className="shrink-0">
      <path
        d="M15.337 23.979l7.216-1.561s-2.604-17.613-2.625-17.73c-.018-.116-.114-.192-.211-.192s-1.929-.136-1.929-.136-1.275-1.274-1.439-1.411c-.045-.037-.075-.057-.121-.074l-.914 21.104h.023zM11.71 11.305s-.81-.424-1.774-.424c-1.447 0-1.504.906-1.504 1.141 0 1.232 3.24 1.715 3.24 4.629 0 2.295-1.44 3.76-3.406 3.76-2.354 0-3.54-1.465-3.54-1.465l.646-2.086s1.245 1.066 2.28 1.066c.675 0 .975-.545.975-.932 0-1.619-2.654-1.694-2.654-4.359-.034-2.237 1.571-4.416 4.827-4.416 1.257 0 1.875.361 1.875.361l-.945 2.715-.02.01zM11.17.83c.136 0 .271.038.405.135-.984.465-2.064 1.639-2.508 3.992-.656.213-1.293.405-1.889.578C7.697 3.75 8.951.84 11.17.84V.83zm1.235 2.949v.135c-.754.232-1.583.484-2.394.736.466-1.777 1.333-2.645 2.085-2.971.193.501.309 1.176.309 2.1zm.539-2.234c.694.074 1.141.867 1.429 1.755-.349.114-.735.231-1.158.366v-.252c0-.752-.096-1.371-.271-1.871v.002zm2.992 1.289c-.02 0-.06.021-.078.021s-.289.075-.714.21c-.423-1.233-1.176-2.37-2.508-2.37h-.115C12.135.209 11.669 0 11.265 0 8.159 0 6.675 3.877 6.21 5.846c-1.194.365-2.063.636-2.16.674-.675.213-.694.232-.772.87-.075.462-1.83 14.063-1.83 14.063L15.009 24l.927-21.166z"
        fill="#95BF47"
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
    <svg width={size} height={size} viewBox="0 0 69.9 73.5" fill="none" className="shrink-0">
      <path
        fillRule="evenodd"
        clipRule="evenodd"
        d="M63.5,73.5H68c1,0,1.9-0.9,1.9-1.9V23.4c0-4.7-3.8-8.6-8.6-8.6h-5.7c-1,0-1.9-0.9-1.9-1.9V8.6C53.8,3.8,50,0,45.2,0H8.6C3.8,0,0,3.8,0,8.6V57c0,4.7,3.8,8.5,8.6,8.6H51c0.4,0,0.8,0.1,1.1,0.3l10.4,7.3C62.8,73.4,63.2,73.5,63.5,73.5z M53.2,58.7H8.7c-1.1,0-1.9-0.8-1.9-1.9V8.7c0-1,0.9-1.9,1.9-1.9h36.4c1,0,1.9,0.9,1.9,1.9v4.3c0,1-0.9,1.9-1.9,1.9H18c-1,0-1.9,0.9-1.9,1.9v32.1c0,1,0.9,1.9,1.9,1.9h33.9c1,0,1.9-0.9,1.9-1.9V23.5c0-1,0.9-1.9,1.9-1.9h5.6c1,0,1.9,0.9,1.9,1.9v39.4c0,1-1.1,1.5-1.8,1L54.3,59C54,58.8,53.6,58.7,53.2,58.7z M46,43.9H23.8c-0.5,0-0.9-0.4-0.9-0.9V22.5c0-0.5,0.4-0.9,0.9-0.9H46c0.5,0,0.9,0.4,0.9,0.9v20.4C46.9,43.4,46.6,43.9,46,43.9z"
        fill="#161616"
      />
    </svg>
  );
}

export function BrandKlaviyo({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 67.8 45.4" fill="none" className="shrink-0">
      <path d="M67.8,45.4H0V0h67.8L53.6,22.7L67.8,45.4Z" fill="#232426" />
    </svg>
  );
}

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
    <path
      d="M3.5 11C5 12.5 9 12.5 10.5 11"
      stroke="white"
      strokeWidth="0.75"
      strokeLinecap="round"
      fill="none"
    />
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
  <svg
    width={size}
    height={Math.round(size * 0.85)}
    viewBox="0 0 16 14"
    fill="none"
    className="flex-shrink-0"
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
    <path
      d="M7 5V7L8.5 8"
      stroke="white"
      strokeWidth="0.75"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);
