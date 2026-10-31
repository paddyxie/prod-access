import AddIcon from '@mui/icons-material/Add';
import { Button } from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { api } from '../../api';
import { ListPage } from '../../components/layout';
import { AccessChip } from '../../components/StatusChip';
import { formatTime } from '../../lib/format';

export function ApiListPage() {
  return (
    <ListPage
      title="API list"
      subtitle="The operations scripts may call as api.<service>.<operation>(). WRITE operations make a run need approval."
      actions={
        <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/apis/new">
          New API
        </Button>
      }
      filters={[
        { name: 'q', label: 'Service, operation or path' },
        { name: 'access', label: 'Access', options: [{ value: 'READ', label: 'READ' }, { value: 'WRITE', label: 'WRITE' }] },
      ]}
      load={(f) => api.apis.list(f)}
      columns={[
        { header: 'Operation', render: (a) => `${a.service}.${a.operation}`, mono: true },
        { header: 'Method', render: (a) => a.method, width: 90 },
        { header: 'Path', render: (a) => a.pathTemplate, mono: true },
        { header: 'Access', render: (a) => <AccessChip access={a.access} />, width: 100 },
        { header: 'Updated by', render: (a) => a.updatedBy },
        { header: 'Updated', render: (a) => formatTime(a.updatedAt) },
      ]}
      rowKey={(a) => a.id}
      rowLink={(a) => `/apis/${a.id}/edit`}
      emptyText="No APIs match."
    />
  );
}
