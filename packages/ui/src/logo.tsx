"use client";

import { useId } from "react";
import {cn} from '@riven/utils'

interface LogoProps extends React.SVGProps<SVGSVGElement> {
  /** Width of the logo in pixels (height auto-scales to maintain aspect ratio) */
  size?: number | string;
}

export function Logo({ size = 32, className, ...props }: LogoProps) {
  const id = useId().replace(/:/g, "");

  return (
    <svg
      viewBox="624 590 748 698"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      className={cn(className)}
      {...props}
    >
      <mask
        id={`${id}-mask0`}
        style={{ maskType: "luminance" }}
        maskUnits="userSpaceOnUse"
        x="1113"
        y="901"
        width="239"
        height="368"
      >
        <path
          d="M1113.33 901.333H1352V1268.29H1113.33V901.333Z"
          fill="white"
        />
      </mask>
      <g mask={`url(#${id}-mask0)`}>
        <path
          d="M1343.46 1177.18L1184.63 902.141L1114.6 1023.57L1238.27 1237.82C1249.44 1257.19 1269.85 1268.22 1290.86 1268.22C1301.14 1268.22 1311.57 1265.54 1321.11 1260.03C1350.31 1243.19 1360.15 1206.24 1343.46 1177.18Z"
          className="fill-logo-primary"
        />
      </g>
      <mask
        id={`${id}-mask1`}
        style={{ maskType: "luminance" }}
        maskUnits="userSpaceOnUse"
        x="644"
        y="610"
        width="598"
        height="659"
      >
        <path
          d="M644 610.667H1241.33V1268.29H644V610.667Z"
          fill="white"
        />
      </mask>
      <g mask={`url(#${id}-mask1)`}>
        <path
          d="M1232.01 751.063L1164.66 867.724L1094.49 989.156L986.021 1177.04C953.536 1233.35 892.896 1268.37 827.938 1268.37C761.932 1268.37 702.781 1234.25 669.703 1177.04C636.63 1119.97 636.63 1051.58 669.703 994.37L859.526 665.839C879.193 631.719 914.5 611.302 953.839 611.302C993.172 611.302 1028.48 631.719 1048.3 665.839L1055.15 677.755L1074.82 711.729L1004.65 833.156L953.839 745.25L775.042 1055.01C759.099 1082.57 769.682 1107.01 775.042 1116.24C780.406 1125.48 796.203 1146.94 828.083 1146.94C849.839 1146.94 870.25 1135.17 880.979 1116.24L1024.46 867.724L1094.64 746.292L1126.82 690.422C1143.66 661.365 1180.76 651.385 1209.81 668.219C1238.86 684.906 1248.84 722.005 1232.01 751.063Z"
          className="fill-logo-primary"
        />
      </g>
    </svg>
  );
}
