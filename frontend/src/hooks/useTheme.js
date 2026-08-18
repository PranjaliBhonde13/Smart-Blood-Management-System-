import { useEffect, useState } from 'react';

/**
 * Light/dark theme toggle. Persists the choice and reflects it as a `dark` class
 * on <html> so Tailwind's `dark:` variants apply. Defaults to the OS preference.
 */
export function useTheme() {
  const [theme, setTheme] = useState(
    () => localStorage.getItem('sben_theme') ||
      (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
  );

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
    localStorage.setItem('sben_theme', theme);
  }, [theme]);

  return { theme, toggle: () => setTheme((t) => (t === 'dark' ? 'light' : 'dark')) };
}
