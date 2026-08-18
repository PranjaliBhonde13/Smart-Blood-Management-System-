import {
  LayoutDashboard, PlusCircle, Droplet, MapPin, Heart, ClipboardList,
  Package, UserRound, Users, ShieldCheck, ScrollText, BarChart3,
} from 'lucide-react';
import { ROLE_HOME } from '../../lib/constants';

/**
 * Sidebar navigation per role. Icons are Lucide components (rendered by the
 * Sidebar). Only routes that exist today are listed; later modules extend the
 * per-role arrays.
 */
export function navForRole(role) {
  const home = ROLE_HOME[role] || '/';
  const dashboard = { label: 'Dashboard', to: home, icon: LayoutDashboard };
  const profile = { label: 'My Profile', to: `${home}/profile`, icon: UserRound };

  const byRole = {
    PATIENT: [
      dashboard,
      { label: 'New Request', to: `${home}/requests/new`, icon: PlusCircle },
      { label: 'My Requests', to: `${home}/requests`, icon: Droplet },
      { label: 'Find Blood', to: `${home}/find`, icon: MapPin },
      profile,
    ],
    DONOR: [
      dashboard,
      { label: 'Open Requests', to: `${home}/open-requests`, icon: Droplet },
      { label: 'Donation History', to: `${home}/donations`, icon: Heart },
      profile,
    ],
    HOSPITAL: [
      dashboard,
      { label: 'Request Queue', to: `${home}/queue`, icon: ClipboardList },
      { label: 'Find Blood', to: `${home}/find`, icon: MapPin },
      profile,
    ],
    BLOOD_BANK: [
      dashboard,
      { label: 'Inventory', to: `${home}/inventory`, icon: Package },
      { label: 'Requests', to: `${home}/requests`, icon: ClipboardList },
      profile,
    ],
    ADMIN: [
      dashboard,
      { label: 'Users', to: `${home}/users`, icon: Users },
      { label: 'Verifications', to: `${home}/verifications`, icon: ShieldCheck },
      { label: 'Audit Log', to: `${home}/audit-logs`, icon: ScrollText },
      { label: 'Reports', to: `${home}/reports`, icon: BarChart3 },
    ],
  };

  return byRole[role] || [dashboard];
}
