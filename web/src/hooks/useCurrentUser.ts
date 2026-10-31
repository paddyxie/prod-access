import { createContext, useContext } from 'react';

export const CurrentUserContext = createContext<string | null>(null);

/** The signed-in user, or null while it is loading. */
export function useCurrentUser() {
  return useContext(CurrentUserContext);
}
