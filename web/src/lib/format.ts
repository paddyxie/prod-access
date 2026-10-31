export function formatTime(ms: number | null | undefined) {
  return ms ? new Date(ms).toLocaleString() : '—';
}

export function formatDuration(from: number | null | undefined, to: number | null | undefined) {
  if (!from) return '—';
  const s = Math.round(((to ?? Date.now()) - from) / 1000);
  if (s < 60) return `${s}s`;
  if (s < 3600) return `${Math.floor(s / 60)}m ${s % 60}s`;
  return `${Math.floor(s / 3600)}h ${Math.floor((s % 3600) / 60)}m`;
}

export function shortId(id: string) {
  return id.slice(0, 8);
}

export function prettyJson(json: string | null | undefined) {
  if (!json) return '';
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}
