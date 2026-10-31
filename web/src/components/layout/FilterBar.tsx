import { Button, MenuItem, Paper, Stack, TextField } from '@mui/material';
import { useEffect, useEffectEvent, useState } from 'react';
import { useResource } from '../../hooks/useResource';

export interface Option {
  value: string;
  label: string;
}

export interface FilterDef {
  name: string;
  label: string;
  /** A free-text box (default) or a dropdown. */
  options?: Option[] | (() => Promise<Option[]>);
}

interface Props {
  defs: FilterDef[];
  values: Record<string, string>;
  onChange: (name: string, value: string) => void;
  onReset: () => void;
}

/** The query conditions of a list, always above it. Values live in the URL, so a filtered list can be shared. */
export function FilterBar({ defs, values, onChange, onReset }: Props) {
  const active = defs.some((d) => values[d.name]);
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction="row" spacing={2} useFlexGap sx={{ flexWrap: 'wrap', alignItems: 'center' }}>
        {defs.map((d) =>
          d.options ? (
            <SelectFilter key={d.name} def={d} value={values[d.name]} onChange={(v) => onChange(d.name, v)} />
          ) : (
            <TextFilter key={d.name} def={d} value={values[d.name]} onCommit={(v) => onChange(d.name, v)} />
          ),
        )}
        <Button onClick={onReset} disabled={!active}>
          Reset
        </Button>
      </Stack>
    </Paper>
  );
}

const FIELD_WIDTH = 220;

function SelectFilter({ def, value, onChange }: { def: FilterDef; value: string; onChange: (v: string) => void }) {
  const source = def.options!;
  const loaded = useResource(`filter-options:${def.name}`, () => (typeof source === 'function' ? source() : Promise.resolve(source)));
  return (
    <TextField select size="small" label={def.label} value={loaded.data ? value : ''} onChange={(e) => onChange(e.target.value)} sx={{ width: FIELD_WIDTH }}>
      <MenuItem value="">
        <em>Any</em>
      </MenuItem>
      {loaded.data?.map((o) => (
        <MenuItem key={o.value} value={o.value}>
          {o.label}
        </MenuItem>
      ))}
    </TextField>
  );
}

/** Applies typed text after a short pause, so the list doesn't refetch on every keystroke. */
function TextFilter({ def, value, onCommit }: { def: FilterDef; value: string; onCommit: (v: string) => void }) {
  const [text, setText] = useState(value);
  const [synced, setSynced] = useState(value);
  if (value !== synced) {
    // The URL changed underneath (reset, back button): show what it says.
    setSynced(value);
    setText(value);
  }
  const commit = useEffectEvent(onCommit);
  useEffect(() => {
    if (text === value) return;
    const t = setTimeout(() => commit(text.trim()), 300);
    return () => clearTimeout(t);
  }, [text, value]);
  return <TextField size="small" label={def.label} value={text} onChange={(e) => setText(e.target.value)} sx={{ width: FIELD_WIDTH }} />;
}
