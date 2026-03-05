import { BackgroundBeams } from '@/components/ui/background/beams';
import { Card } from '@riven/ui';
import { FCWC, Propless } from '@/lib/interfaces/interface';

export const AuthFormWrapper: FCWC<Propless> = ({ children }) => {
  return (
    <>
      <BackgroundBeams />
      <section className="h-screen-without-header flex items-center justify-center bg-background/20">
        <Card className="relative bg-background/70 p-6 backdrop-blur-sm transition-all">
          {children}
        </Card>
      </section>
    </>
  );
};
