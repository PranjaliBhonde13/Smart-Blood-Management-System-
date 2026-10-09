import { useState } from 'react';
import { ListChecks } from 'lucide-react';
import {
  useHospitalDocumentRequirementsQuery,
  useUpdateHospitalDocumentRequirementMutation,
} from '../hospitalDocumentsAdminApi';
import { parseApiError } from '../../../lib/apiError';
import { toast } from '../../../lib/toast';
import Card from '../../../components/ui/Card';
import Skeleton from '../../../components/ui/Skeleton';
import { Table, THead, TH, TBody, TR, TD } from '../../../components/ui/Table';

const WHEN = { ALWAYS: 'All hospitals', OPERATES_BLOOD_BANK: 'Only hospitals running their own blood bank' };

/**
 * Hospital document rules (stored in hospital_document_requirements). Admins can
 * switch a rule on/off, make it mandatory or optional, and require an expiry
 * date. Category-specific overrides can be added through the API
 * (POST /admin/hospital-document-requirements).
 */
export default function DocumentRequirementsCard() {
  const { data, isLoading } = useHospitalDocumentRequirementsQuery();
  const [update] = useUpdateHospitalDocumentRequirementMutation();
  const [busy, setBusy] = useState(null);
  const rules = data?.data || [];

  const toggle = async (rule, field) => {
    setBusy(`${rule.id}-${field}`);
    try {
      await update({ id: rule.id, [field]: !rule[field] }).unwrap();
      toast.success('Requirement updated');
    } catch (err) {
      toast.error(parseApiError(err).message);
    } finally {
      setBusy(null);
    }
  };

  const Toggle = ({ rule, field, label }) => (
    <input type="checkbox" className="h-4 w-4 accent-brand-600" checked={!!rule[field]}
           disabled={busy !== null} aria-label={`${label}: ${rule.documentTypeLabel}`}
           onChange={() => toggle(rule, field)} />
  );

  return (
    <Card>
      <h2 className="mb-1 flex items-center gap-2 text-lg font-semibold"><ListChecks className="h-5 w-5 text-brand-600" /> Hospital document requirements</h2>
      <p className="mb-4 text-sm text-slate-500">
        Which documents hospitals must provide. Changes apply immediately to every hospital's verification status.
      </p>
      {isLoading ? <Skeleton className="h-32" /> : (
        <Table>
          <THead><TH>Document</TH><TH>Category</TH><TH>Applies to</TH><TH>Mandatory</TH><TH>Expiry date required</TH><TH>Active</TH></THead>
          <TBody>
            {rules.map((r) => (
              <TR key={r.id}>
                <TD><span className="font-medium">{r.documentTypeLabel}</span>{r.description && <p className="text-xs text-slate-500">{r.description}</p>}</TD>
                <TD className="text-xs">{r.registrationCategory ? r.registrationCategory.replaceAll('_', ' ').toLowerCase() : 'All categories'}</TD>
                <TD className="text-xs">{WHEN[r.appliesWhen] || r.appliesWhen}</TD>
                <TD><Toggle rule={r} field="mandatory" label="Mandatory" /></TD>
                <TD><Toggle rule={r} field="expiryDateRequired" label="Expiry date required" /></TD>
                <TD><Toggle rule={r} field="active" label="Active" /></TD>
              </TR>
            ))}
          </TBody>
        </Table>
      )}
    </Card>
  );
}
