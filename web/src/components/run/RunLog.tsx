import { Box, Typography } from '@mui/material';
import { useEffect, useRef, useState } from 'react';
import { api, type RunLogEntry } from '../../api';

const LEVEL_COLOR: Record<string, string> = { warn: 'warning.main', error: 'error.main' };

/** The run's log, tailed while the run is live. */
export function RunLog({ runId, live }: { runId: string; live: boolean }) {
  const [entries, setEntries] = useState<RunLogEntry[]>([]);
  const after = useRef(0);

  useEffect(() => {
    let active = true;
    const pull = async () => {
      const next = await api.runs.events(runId, after.current);
      if (!active || next.length === 0) return;
      after.current = next[next.length - 1].id;
      setEntries((prev) => [...prev, ...next]);
    };
    pull();
    const timer = live ? setInterval(pull, 1500) : undefined;
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [runId, live]);

  if (entries.length === 0) return <Typography color="text.secondary">No log output.</Typography>;
  return (
    <Box component="pre" sx={{ m: 0, p: 1.5, maxHeight: 420, overflow: 'auto', bgcolor: 'action.hover', borderRadius: 1, fontSize: 12.5, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
      {entries.map((e) => (
        <Box key={e.id} component="div" sx={{ color: e.kind === 'error' ? 'error.main' : LEVEL_COLOR[e.lvl ?? ''] }}>
          {new Date(e.ts).toLocaleTimeString()} {e.kind === 'step' ? '▸ ' : ''}
          {e.message}
        </Box>
      ))}
    </Box>
  );
}
