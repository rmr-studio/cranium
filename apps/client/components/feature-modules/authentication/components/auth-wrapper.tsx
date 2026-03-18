import { AuthBackground } from '@/components/feature-modules/authentication/components/auth-background';
import { Card } from '@riven/ui/card';
import { FCWC, Propless } from '@/lib/interfaces/interface';

export const AuthFormWrapper: FCWC<Propless> = ({ children }) => {
  return (
    <>
      <AuthBackground />
      <section className="relative z-10 grid min-h-dvh place-items-center overflow-y-auto px-4 py-12">
        <Card className="w-full max-w-lg border-foreground/8 bg-card/95 p-6 shadow-2xl backdrop-blur-sm transition-all dark:bg-card/90">
          {children}
        </Card>
      </section>
    </>
  );
};
