import Button from './Button';

/**
 * Prev/next pager for Spring `Page` responses. Renders nothing when everything
 * fits on one page.
 *
 * @param {{page:number, totalPages:number, onChange:(p:number)=>void}} props
 */
export default function Pager({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-center gap-3">
      <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>Prev</Button>
      <span className="text-sm text-slate-500">Page {page + 1} of {totalPages}</span>
      <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</Button>
    </div>
  );
}
