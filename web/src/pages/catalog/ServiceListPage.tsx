import { Button } from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { api } from '../../api';
import { ListPage } from '../../components/layout';

export function ServiceListPage() {
  return <ListPage title="Services" subtitle="Top-level applications. Each service contains one or more CMDB systems."
    actions={<Button component={RouterLink} to="/services/new" variant="contained">New service</Button>}
    filters={[]} load={() => api.catalog.services()} columns={[
      { header: 'Code', render: (s) => s.code }, { header: 'Name', render: (s) => s.name }, { header: 'Description', render: (s) => s.description },
    ]} rowKey={(s) => s.id} rowLink={(s) => `/systems?serviceId=${s.id}`} emptyText="No services registered." />;
}
