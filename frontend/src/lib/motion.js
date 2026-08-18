/**
 * Shared Framer Motion variants so page/section animations stay consistent
 * across the app. Import these instead of re-declaring transitions per file.
 */

/** Soft, springy ease used for entrances (Apple-style). */
export const EASE = [0.16, 1, 0.3, 1];

export const fadeUp = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: EASE } },
};

export const fadeIn = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { duration: 0.4, ease: EASE } },
};

export const scaleIn = {
  hidden: { opacity: 0, scale: 0.96 },
  show: { opacity: 1, scale: 1, transition: { duration: 0.35, ease: EASE } },
};

/** Parent that reveals children one after another. */
export const stagger = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08, delayChildren: 0.05 } },
};

/** Standard page transition for route-level wrappers. */
export const pageTransition = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.35, ease: EASE } },
  exit: { opacity: 0, y: -8, transition: { duration: 0.2 } },
};

/** Reusable hover-lift props for interactive cards. */
export const hoverLift = {
  whileHover: { y: -4, transition: { duration: 0.2, ease: EASE } },
  whileTap: { scale: 0.99 },
};
