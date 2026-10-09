import { Children, Fragment, isValidElement, useCallback, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown } from 'lucide-react';

/** Flattens `<option>` children (incl. fragments/arrays) into {value,label,disabled}. */
function optionsFromChildren(children) {
  const out = [];
  const walk = (nodes) =>
    Children.forEach(nodes, (child) => {
      if (!isValidElement(child)) return;
      if (child.type === Fragment) return walk(child.props.children);
      if (child.type === 'option') {
        const text = Children.toArray(child.props.children).join('');
        out.push({ value: String(child.props.value ?? text), label: text, disabled: !!child.props.disabled });
      }
    });
  walk(children);
  return out;
}

const normalize = (o) =>
  typeof o === 'string' ? { value: o, label: o, disabled: false } : { value: String(o.value), label: o.label, disabled: !!o.disabled };

/**
 * Themed dropdown matching TextField. `options` may be strings or {value,label},
 * or pass `<option>` children. The popup is a custom listbox (portal, so it is
 * never clipped by cards/modals) styled to the app theme; a hidden native
 * <select> keeps `name`, `required` and browser form validation working.
 * `onChange` / `onBlur` receive an event-like object with `target.value`.
 */
export default function Select({
  label, error, hint, icon: Icon = null, options = [], className = '', children, id,
  value, defaultValue, onChange, onBlur, name, required, disabled, placeholder, ...props
}) {
  const autoId = useId();
  const fieldId = id || (typeof label === 'string' ? `s-${label.replace(/\s+/g, '-').toLowerCase()}` : `s${autoId.replace(/:/g, '')}`);
  const describedBy = props['aria-describedby'] ?? ((error || hint) ? `${fieldId}-desc` : undefined);
  const listId = `${fieldId}-listbox`;

  const items = useMemo(() => (children ? optionsFromChildren(children) : options.map(normalize)), [children, options]);

  const controlled = value !== undefined;
  const [inner, setInner] = useState(defaultValue ?? items[0]?.value ?? '');
  const current = String(controlled ? value ?? '' : inner);
  // Like a native select, an unmatched value shows the first option.
  const selectedIndex = Math.max(0, items.findIndex((o) => o.value === current));
  const selected = items[selectedIndex];

  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(selectedIndex);
  const [pos, setPos] = useState(null);
  const buttonRef = useRef(null);
  const listRef = useRef(null);
  const typeahead = useRef({ text: '', timer: 0 });

  const emit = (handler, val) => {
    if (!handler) return;
    const target = { value: val, name, id: fieldId, type: 'select-one' };
    handler({ target, currentTarget: target, preventDefault() {}, stopPropagation() {} });
  };

  const choose = (i) => {
    const o = items[i];
    if (!o || o.disabled) return;
    if (!controlled) setInner(o.value);
    if (o.value !== current) emit(onChange, o.value);
    setOpen(false);
    buttonRef.current?.focus();
  };

  const openList = () => {
    if (disabled || !items.length) return;
    setActive(selectedIndex);
    setOpen(true);
  };

  // Position the popup under the trigger, flipping above when space runs out.
  const place = useCallback(() => {
    const r = buttonRef.current?.getBoundingClientRect();
    if (!r) return;
    const maxH = 264;
    const below = window.innerHeight - r.bottom - 8;
    const up = below < Math.min(maxH, items.length * 40 + 12) && r.top > below;
    setPos({
      left: r.left,
      width: r.width,
      maxHeight: Math.max(120, Math.min(maxH, (up ? r.top : below) - 8)),
      ...(up ? { bottom: window.innerHeight - r.top + 6 } : { top: r.bottom + 6 }),
    });
  }, [items.length]);

  useLayoutEffect(() => {
    if (!open) return undefined;
    place();
    window.addEventListener('resize', place);
    window.addEventListener('scroll', place, true);
    return () => {
      window.removeEventListener('resize', place);
      window.removeEventListener('scroll', place, true);
    };
  }, [open, place]);

  // Close on outside pointer down.
  useEffect(() => {
    if (!open) return undefined;
    const onDown = (e) => {
      if (!buttonRef.current?.contains(e.target) && !listRef.current?.contains(e.target)) setOpen(false);
    };
    document.addEventListener('pointerdown', onDown);
    return () => document.removeEventListener('pointerdown', onDown);
  }, [open]);

  // Keep the highlighted option in view.
  useEffect(() => {
    if (open) listRef.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [open, active, pos]);

  const step = (from, dir) => {
    for (let i = from + dir; i >= 0 && i < items.length; i += dir) if (!items[i].disabled) return i;
    return from;
  };

  const onKeyDown = (e) => {
    if (disabled) return;
    switch (e.key) {
      case 'ArrowDown':
      case 'ArrowUp': {
        e.preventDefault();
        const dir = e.key === 'ArrowDown' ? 1 : -1;
        if (!open) {
          if (e.altKey) return openList();
          return choose(step(selectedIndex, dir));
        }
        return setActive((a) => step(a, dir));
      }
      case 'Home':
      case 'End':
        if (!open) return undefined;
        e.preventDefault();
        return setActive(e.key === 'Home' ? step(-1, 1) : step(items.length, -1));
      case 'Enter':
      case ' ':
        if (e.key === ' ' && typeahead.current.text) break;
        e.preventDefault();
        return open ? choose(active) : openList();
      case 'Escape':
        if (open) { e.preventDefault(); setOpen(false); }
        return undefined;
      case 'Tab':
        if (open) setOpen(false);
        return undefined;
      default:
        break;
    }
    // Type-to-select, like a native select.
    if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
      const t = typeahead.current;
      clearTimeout(t.timer);
      t.text += e.key.toLowerCase();
      t.timer = setTimeout(() => { t.text = ''; }, 600);
      const start = open ? active : selectedIndex;
      const order = [...items.keys()].map((k) => (start + 1 + k) % items.length);
      const hit = order.find((i) => !items[i].disabled && items[i].label.toLowerCase().startsWith(t.text));
      if (hit !== undefined) {
        if (open) setActive(hit);
        else choose(hit);
      }
    }
    return undefined;
  };

  const isPlaceholder = !selected || selected.value === '';
  const shownText = selected?.label || placeholder || '';

  return (
    <div className={className}>
      {label && (
        <label htmlFor={fieldId} className="mb-1.5 block text-sm font-medium text-slate-700 dark:text-slate-200">
          {label}
        </label>
      )}
      <div className="relative">
        {Icon && <Icon className="pointer-events-none absolute left-3.5 top-1/2 z-10 h-5 w-5 -translate-y-1/2 text-slate-500 dark:text-slate-400" />}
        <button
          ref={buttonRef}
          type="button"
          id={fieldId}
          role="combobox"
          aria-haspopup="listbox"
          aria-expanded={open}
          aria-controls={open ? listId : undefined}
          aria-activedescendant={open ? `${listId}-${active}` : undefined}
          aria-invalid={error ? true : undefined}
          aria-required={required || undefined}
          aria-describedby={describedBy}
          disabled={disabled}
          onClick={() => (open ? setOpen(false) : openList())}
          onKeyDown={onKeyDown}
          onBlur={() => emit(onBlur, current)}
          {...props}
          className={
            'relative flex w-full items-center rounded-xl border bg-white py-2.5 pr-10 text-left outline-none transition ' +
            'focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:bg-slate-900 ' +
            'disabled:cursor-not-allowed disabled:bg-slate-50 disabled:opacity-60 dark:disabled:bg-slate-900/60 ' +
            (Icon ? 'pl-11 ' : 'pl-3.5 ') +
            (isPlaceholder ? 'text-slate-400 dark:text-slate-500 ' : 'text-slate-800 dark:text-slate-100 ') +
            (open ? 'border-brand-500 ring-4 ring-brand-500/15 ' : '') +
            (error ? 'border-brand-400 ' : open ? '' : 'border-slate-200 hover:border-slate-300 dark:border-slate-700 dark:hover:border-slate-600 ')
          }
        >
          <span className="block truncate">{shownText || ' '}</span>
        </button>
        <ChevronDown
          className={
            'pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 transition-transform duration-200 ' +
            (open ? 'rotate-180 text-brand-500' : 'text-slate-400')
          }
        />
        {/* Hidden native control: carries name/value for forms and triggers browser `required` validation. */}
        <select
          aria-hidden="true"
          tabIndex={-1}
          name={name}
          required={required}
          disabled={disabled}
          value={current}
          onChange={() => {}}
          onFocus={() => buttonRef.current?.focus()}
          className="pointer-events-none absolute bottom-0 left-1/2 h-px w-px -translate-x-1/2 opacity-0"
        >
          {items.map((o) => <option key={o.value} value={o.value} disabled={o.disabled}>{o.label}</option>)}
        </select>
      </div>

      {open && pos && createPortal(
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          aria-labelledby={typeof label === 'string' ? undefined : fieldId}
          aria-label={typeof label === 'string' ? label : undefined}
          onMouseDown={(e) => e.preventDefault()}
          style={{ position: 'fixed', left: pos.left, width: pos.width, top: pos.top, bottom: pos.bottom, maxHeight: pos.maxHeight }}
          className={
            'scroll-slim z-[60] animate-scale-in overflow-y-auto rounded-2xl border border-slate-200 bg-white/95 p-1.5 shadow-card backdrop-blur-xl ' +
            'dark:border-slate-800 dark:bg-slate-900/95 ' + (pos.bottom != null ? 'origin-bottom' : 'origin-top')
          }
        >
          {items.map((o, i) => {
            const isSel = i === selectedIndex;
            const isActive = i === active;
            return (
              <li
                key={o.value}
                id={`${listId}-${i}`}
                data-index={i}
                role="option"
                aria-selected={isSel}
                aria-disabled={o.disabled || undefined}
                onMouseEnter={() => !o.disabled && setActive(i)}
                onClick={() => choose(i)}
                className={
                  'flex cursor-pointer select-none items-center justify-between gap-2 rounded-xl px-3 py-2 text-sm transition-colors ' +
                  (o.disabled
                    ? 'cursor-not-allowed text-slate-300 dark:text-slate-600 '
                    : isActive
                      ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300 '
                      : 'text-slate-700 dark:text-slate-200 ') +
                  (isSel ? 'font-semibold ' : '') +
                  (o.value === '' && !isActive ? 'text-slate-400 dark:text-slate-500 ' : '')
                }
              >
                <span className="truncate">{o.label}</span>
                {isSel && <Check className="h-4 w-4 shrink-0 text-brand-600 dark:text-brand-400" />}
              </li>
            );
          })}
        </ul>,
        document.body,
      )}

      {error ? (
        <span id={describedBy} className="mt-1.5 block animate-fade-up text-xs font-medium text-brand-600">{error}</span>
      ) : (
        hint && <span id={describedBy} className="mt-1.5 block text-xs text-slate-500 dark:text-slate-400">{hint}</span>
      )}
    </div>
  );
}
