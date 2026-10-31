import { Alert, TextField } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api, ApiError, type ParamDef, type Params } from '../../api';
import { FormPage } from '../../components/layout';
import { ParamsForm } from '../../components/task/ParamsForm';
import { useResource } from '../../hooks/useResource';

export function TaskRunFormPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const data = useResource(`task-run:${id}`, async () => {
    const task = await api.tasks.get(id);
    return { task, version: await api.tasks.version(id, task.latestVersion) };
  });
  const [values, setValues] = useState<Params>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [confirm, setConfirm] = useState('');

  const t = data.data?.task;
  const defs = data.data ? (JSON.parse(data.data.version.paramsJson) as Record<string, ParamDef>) : {};

  const submit = async () => {
    setErrors({});
    try {
      const run = await api.runs.submit(id, values, confirm);
      navigate(`/runs/${run.id}`);
    } catch (e) {
      if (e instanceof ApiError) setErrors(e.fields);
      throw e;
    }
  };

  return (
    <FormPage
      back={{ to: `/tasks/${id}`, label: t?.name ?? 'Task' }}
      title={`Run ${t?.name ?? ''}`}
      subtitle={t && `v${t.latestVersion} · as client ${t.clientName}`}
      submitLabel={t?.hasWrite ? 'Submit for approval' : 'Run'}
      onSubmit={submit}
      loading={!data.data}
    >
      {t?.hasWrite && (
        <Alert severity="warning">
          This task writes data as client <b>{t.clientName}</b>. It runs only after someone else approves it.
        </Alert>
      )}
      <ParamsForm defs={defs} values={values} onChange={(k, v) => setValues((p) => ({ ...p, [k]: v }))} errors={errors} />
      {t?.hasWrite && (
        <TextField required label="Confirm client name" helperText={`Type ${t.clientName} to confirm`} value={confirm} onChange={(e) => setConfirm(e.target.value)} />
      )}
    </FormPage>
  );
}
