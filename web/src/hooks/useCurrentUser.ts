import { createContext, useContext } from 'react';
import type { Me } from '../api';

export const CurrentUserContext = createContext<Me | null>(null);

/** The signed-in user with their teams and owned roles, or null while it is loading. */
export function useCurrentUser() {
  return useContext(CurrentUserContext);
}
