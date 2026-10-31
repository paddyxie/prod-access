import { FormControlLabel, MenuItem, Switch, TextField, Typography } from '@mui/material';
import type { ParamDef, Params } from '../../api';

interface Props {
  defs: Record<string, ParamDef>;
  values: Params;
  onChange: (name: string, value: string) => void;
  /** Per-param messages from the server's validation. */
  errors?: Record<string, string>;
}

/** Inputs generated from the params a task script declares. Values are sent as text; the server coerces them. */
export function ParamsForm({ defs, values, onChange, errors = {} }: Props) {
  const names = Object.keys(defs);
  if (names.length === 0) return <Typography color="text.secondary">This task takes no parameters.</Typography>;
  return names.map((name) => {
    const d = defs[name];
    const value = values[name] ?? '';
    const label = (d.label ?? name) + (d.required === false || d.default !== undefined ? '' : ' *');
    const help = errors[name] ?? [d.description, hint(d)].filter(Boolean).join(' · ');
    const common = { label, value, error: !!errors[name], helperText: help, fullWidth: true, onChange: (e: { target: { value: string } }) => onChange(name, e.target.value) };

    switch (d.type) {
      case 'boolean':
        return (
          <FormControlLabel
            key={name}
            label={label}
            control={<Switch checked={(value || String(d.default ?? false)) === 'true'} onChange={(e) => onChange(name, String(e.target.checked))} />}
          />
        );
      case 'enum':
        return (
          <TextField key={name} select {...common}>
            {(d.options ?? []).map((o) => (
              <MenuItem key={String(o)} value={String(o)}>
                {String(o)}
              </MenuItem>
            ))}
          </TextField>
        );
      case 'number':
        return <TextField key={name} type="number" {...common} />;
      case 'date':
        return <TextField key={name} type="date" {...common} slotProps={{ inputLabel: { shrink: true } }} />;
      case 'datetime':
        return <TextField key={name} type="datetime-local" {...common} slotProps={{ inputLabel: { shrink: true } }} />;
      case 'text':
      case 'json':
      case 'csv':
      case 'list':
        return <TextField key={name} multiline minRows={3} {...common} slotProps={{ htmlInput: { style: { fontFamily: 'monospace' } } }} />;
      default:
        return <TextField key={name} {...common} />;
    }
  });
}

function hint(d: ParamDef) {
  const def = d.default !== undefined ? `default: ${JSON.stringify(d.default)}` : '';
  switch (d.type) {
    case 'list':
      return ['one per line or comma-separated', def].filter(Boolean).join(' · ');
    case 'csv':
      return ['CSV with a header row', d.columns ? `columns: ${d.columns.join(', ')}` : '', def].filter(Boolean).join(' · ');
    case 'json':
      return ['JSON', def].filter(Boolean).join(' · ');
    default:
      return def;
  }
}
