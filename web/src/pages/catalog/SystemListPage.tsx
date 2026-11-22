import { Button } from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { api } from '../../api';
import { ListPage } from '../../components/layout';

export function SystemListPage() {
  return <ListPage title="Systems / CMDB Roles" subtitle="A CMDB Role identifies a system such as a web app, Kafka service or batch processor."
    actions={<Button component={RouterLink} to="/systems/new" variant="contained">New system</Button>}
    filters={[{name: 'serviceId', label: 'Service', options: async () => (await api.catalog.services()).map((s) => ({value: s.id, label: s.name}))}]}
    load={async (f) => (await api.catalog.roles()).filter((r) => !f.serviceId || r.serviceId === f.serviceId)} columns={[
      {header: 'Service', render: (r) => r.serviceCode}, {header: 'CMDB Role', render: (r) => r.code}, {header: 'Name', render: (r) => r.name},
      {header: 'System type', render: (r) => r.systemType}, {header: 'Description', render: (r) => r.description},
    ]} rowKey={(r) => r.id} rowLink={(r) => `/tools?role=${r.code}`} emptyText="No systems registered." />;
}
