import AppRouter from './routes/AppRouter';
import { AppToaster } from './lib/toast';

/** Root component — delegates to the application route map. */
export default function App() {
  return (
    <>
      <AppRouter />
      <AppToaster />
    </>
  );
}
