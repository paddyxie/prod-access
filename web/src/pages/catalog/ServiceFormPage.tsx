import { TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '../../api';
import { FormPage } from '../../components/layout';

export function ServiceFormPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ code: '', name: '', description: '' });
  return <FormPage back={{to: '/services', label: 'Services'}} title="New service" submitLabel="Create service"
    onSubmit={async () => { const s = await api.catalog.createService(form); navigate(`/systems/new?serviceId=${s.id}`); }}>
    <TextField required label="Service code" value={form.code} onChange={(e) => setForm({...form, code: e.target.value})} />
    <TextField required label="Name" value={form.name} onChange={(e) => setForm({...form, name: e.target.value})} />
    <TextField label="Description" value={form.description} onChange={(e) => setForm({...form, description: e.target.value})} multiline minRows={2} />
  </FormPage>;
}
