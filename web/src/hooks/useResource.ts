import { useEffect, useEffectEvent, useState } from 'react';

interface State<T> {
  key: string;
  data?: T;
  error?: Error;
}

/**
 * Loads data for `key` (refetching when the key changes) and optionally keeps polling it.
 * `key` must capture everything `load` depends on, e.g. `run:${id}`.
 */
export function useResource<T>(key: string, load: () => Promise<T>, pollMs?: number) {
  const [state, setState] = useState<State<T>>({ key: '' });
  const [tick, setTick] = useState(0);
  const fetchData = useEffectEvent(load);

  useEffect(() => {
    let live = true;
    const once = () =>
      fetchData().then(
        (data) => live && setState({ key, data }),
        (error: Error) => live && setState((s) => ({ key, data: s.key === key ? s.data : undefined, error })),
      );
    once();
    const timer = pollMs ? setInterval(once, pollMs) : undefined;
    return () => {
      live = false;
      clearInterval(timer);
    };
  }, [key, tick, pollMs]);

  const current = state.key === key;
  return {
    data: current ? state.data : undefined,
    error: current ? state.error : undefined,
    loading: !current,
    reload: () => setTick((t) => t + 1),
  };
}
