import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { store } from './app/store';
import './index.css';
import App from './App.jsx';

// The app is light-only: drop any dark class or saved preference left by the old theme toggle.
document.documentElement.classList.remove('dark');
try { localStorage.removeItem('sben_theme'); } catch { /* storage unavailable */ }

/** Bootstraps React with the Redux store (which includes the RTK Query cache). */
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <Provider store={store}>
      <App />
    </Provider>
  </StrictMode>
);
