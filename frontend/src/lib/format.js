/** "45m", "3h 10m", "2d 4h" from a number of minutes. */
export function formatMinutes(min) {
  if (min == null) return '—';
  if (min < 60) return `${min}m`;
  const h = Math.floor(min / 60);
  if (h < 24) return `${h}h ${min % 60}m`;
  const d = Math.floor(h / 24);
  return `${d}d ${h % 24}h`;
}

const pad = (n) => String(n).padStart(2, '0');

/** Date, timestamp or ISO string; date-only "yyyy-mm-dd" is read as a local day (avoids the UTC shift). */
function toDate(value) {
  if (value instanceof Date) return value;
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) return new Date(`${value}T00:00:00`);
  return new Date(value);
}

/** App-wide date format: DD-MM-YYYY, or a dash. */
export function formatDate(value) {
  if (!value) return '—';
  const d = toDate(value);
  if (Number.isNaN(d.getTime())) return '—';
  return `${pad(d.getDate())}-${pad(d.getMonth() + 1)}-${d.getFullYear()}`;
}

/** App-wide date+time format: DD-MM-YYYY, hh:mm AM/PM, or a dash. */
export function formatDateTime(value) {
  if (!value) return '—';
  const d = toDate(value);
  if (Number.isNaN(d.getTime())) return '—';
  const h = d.getHours();
  return `${formatDate(d)}, ${pad(h % 12 || 12)}:${pad(d.getMinutes())} ${h < 12 ? 'AM' : 'PM'}`;
}

/** Date-only ISO string (yyyy-mm-dd) as DD-MM-YYYY. */
export const formatDay = formatDate;
