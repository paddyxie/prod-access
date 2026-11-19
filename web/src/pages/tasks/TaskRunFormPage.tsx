import { Alert } from '@mui/material';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api, ApiError, type ParamDef, type Params } from '../../api';
import { FormPage } from '../../components/layout';
import { ParamsForm } from '../../components/task/ParamsForm';
import { useCurrentUser } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';

export function TaskRunFormPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const me = useCurrentUser();
  const data = useResource(`task-run:${id}`, async () => {
    const task = await api.tasks.get(id);
    return { task, version: await api.tasks.version(id, task.latestVersion) };
  });
  const [values, setValues] = useState<Params>({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  const t = data.data?.task;
  const notOwned = t?.roles.filter((r) => !me?.ownedRoles.includes(r)) ?? [];
  const defs = data.data ? (JSON.parse(data.data.version.paramsJson) as Record<string, ParamDef>) : {};

  const submit = async () => {
    setErrors({});
    try {
      const run = await api.runs.submit(id, values);
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
      subtitle={t && `v${t.latestVersion} · calls ${t.tools.join(', ') || 'no tools'}`}
      submitLabel="Run"
      onSubmit={submit}
      loading={!data.data || !me}
    >
      {notOwned.length > 0 && (
        <Alert severity="warning">
          None of your teams owns {notOwned.join(', ')}, so this run will be refused.
        </Alert>
      )}
      <ParamsForm defs={defs} values={values} onChange={(k, v) => setValues((p) => ({ ...p, [k]: v }))} errors={errors} />
    </FormPage>
  );
}
