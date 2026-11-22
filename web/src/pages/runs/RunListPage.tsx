import AddIcon from '@mui/icons-material/Add';
import { Button } from '@mui/material';
import { Link as RouterLink } from 'react-router';
import { api } from '../../api';
import { ListPage } from '../../components/layout';
import { kindOptions, statusOptions, taskOptions } from '../../lib/options';
import { runColumns } from '../../components/run/runColumns';

export function RunListPage() {
  return (
    <ListPage
      title="Executions"
      actions={
        <Button variant="contained" startIcon={<AddIcon />} component={RouterLink} to="/runs/new">
          Ad-hoc script
        </Button>
      }
      filters={[
        { name: 'status', label: 'Status', options: statusOptions },
        { name: 'workflowId', label: 'Workflow', options: taskOptions },
        { name: 'kind', label: 'Kind', options: kindOptions },
        { name: 'submittedBy', label: 'Submitted by' },
      ]}
      load={(f) => api.runs.list(f)}
      columns={runColumns}
      rowKey={(r) => r.id}
      rowLink={(r) => `/runs/${r.id}`}
      emptyText="No runs match."
      pollMs={4000}
    />
  );
}
