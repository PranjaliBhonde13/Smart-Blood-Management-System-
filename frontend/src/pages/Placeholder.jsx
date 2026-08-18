import { Link } from 'react-router-dom';
import { ArrowLeft, Compass } from 'lucide-react';
import Logo from '../components/ui/Logo';
import Button from '../components/ui/Button';

/**
 * Friendly stand-in for error routes (403/404) and pages delivered in later
 * modules. Keeps the premium look consistent even on the edges.
 */
export default function Placeholder({ title }) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-slate-50 px-6 text-center dark:bg-slate-950">
      <div className="pointer-events-none absolute inset-0 bg-grad-mesh" />
      <div className="relative">
        <Link to="/" className="mb-10 flex justify-center"><Logo size="md" /></Link>
        <div className="mx-auto grid h-20 w-20 place-items-center rounded-3xl bg-grad-brand text-white shadow-emergency">
          <Compass className="h-10 w-10" />
        </div>
        <h1 className="mt-6 text-3xl font-extrabold tracking-tight text-slate-900 dark:text-white">{title}</h1>
        <p className="mx-auto mt-2 max-w-sm text-slate-500">This screen isn't available right now. Let's get you back on track.</p>
        <Link to="/" className="mt-8 inline-block">
          <Button leftIcon={<ArrowLeft className="h-4 w-4" />}>Back home</Button>
        </Link>
      </div>
    </div>
  );
}
