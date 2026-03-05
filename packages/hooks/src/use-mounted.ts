import { useEffect, useState } from 'react';

export function useMounted() {
  const [mounted, setMounted] = useState(false);
  // eslint-disable-next-line react-hooks/set-state-in-effect -- intentional one-time hydration flag
  useEffect(() => { setMounted(true); }, []);
  return mounted;
}
