import type { ChildNodeProps } from '@cranium/utils';
import { FC } from 'react';

const layout: FC<ChildNodeProps> = ({ children }) => {
  return <>{children}</>;
};

export default layout;
