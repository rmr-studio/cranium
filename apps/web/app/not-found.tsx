import Link from 'next/link';

export default function NotFound() {
  return (
    <main className="flex min-h-[80dvh] flex-col items-center justify-center px-4 pt-20 text-center">
      <p className="font-mono text-sm font-bold uppercase tracking-wide text-muted-foreground">
        404
      </p>
      <h1 className="mt-3 font-serif text-5xl italic tracking-tight text-foreground">
        Page not found
      </h1>
      <p className="mt-4 max-w-md text-sm tracking-tight text-muted-foreground">
        The page you&apos;re looking for doesn&apos;t exist or has been moved.
      </p>
      <Link
        href="/"
        className="mt-8 text-sm font-medium tracking-tight text-foreground underline underline-offset-4 transition-colors hover:text-muted-foreground"
      >
        Back to home
      </Link>
    </main>
  );
}
