import { MenuItem, TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { api, SYSTEM_TYPES, type CmdbRole } from '../../api';
import { FormPage } from '../../components/layout';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';

export function SystemFormPage() {
  const navigate = useNavigate();
  const [query] = useSearchParams();
  const me = useCurrentUser();
  const services = useResource('services', () => api.catalog.services());
  const [form, setForm] = useState<Omit<CmdbRole, 'id'|'serviceCode'>>({serviceId: query.get('serviceId') ?? '', code: '', name: '', systemType: 'WEB', description: ''});
  return <FormPage back={{to: '/systems', label: 'Systems'}} title="New system / CMDB Role" submitLabel="Create system" loading={!services.data || !me}
    onSubmit={async () => { await api.catalog.createRole(form); navigate(`/tools?role=${form.code}`); }}>
    <TextField select required label="Service" value={form.serviceId} onChange={(e) => setForm({...form, serviceId: e.target.value})}>
      {services.data?.map((s) => <MenuItem key={s.id} value={s.id}>{s.name} ({s.code})</MenuItem>)}
    </TextField>
    <TextField select required label="CMDB Role" value={form.code} onChange={(e) => setForm({...form, code: e.target.value})}>
      {me?.ownedRoles.map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
    </TextField>
    <TextField required label="System name" value={form.name} onChange={(e) => setForm({...form, name: e.target.value})} />
    <TextField select required label="System type" value={form.systemType} onChange={(e) => setForm({...form, systemType: e.target.value as CmdbRole['systemType']})}>
      {SYSTEM_TYPES.map((t) => <MenuItem key={t} value={t}>{t}</MenuItem>)}
    </TextField>
    <TextField label="Description" value={form.description ?? ''} onChange={(e) => setForm({...form, description: e.target.value})} multiline minRows={2} />
  </FormPage>;
}
