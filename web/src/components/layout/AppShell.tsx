import { AppBar, Box, Chip, Drawer, List, ListItemButton, ListItemIcon, ListItemText, Toolbar, Typography } from '@mui/material';
import { NavLink, Outlet, useLocation } from 'react-router';
import { api } from '../../api';
import { CurrentUserContext } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';
import { NAV } from '../../nav';

const DRAWER_WIDTH = 220;

export function AppShell() {
  const { pathname } = useLocation();
  const me = useResource('me', api.me);
  const status = useResource('admin-status', api.adminStatus, 15000);

  return (
    <CurrentUserContext value={me.data ?? null}>
      <Box sx={{ display: 'flex', minHeight: '100vh' }}>
        <AppBar position="fixed" elevation={0} color="default" sx={{ zIndex: (t) => t.zIndex.drawer + 1, borderBottom: 1, borderColor: 'divider' }}>
          <Toolbar variant="dense" sx={{ gap: 2 }}>
            <Typography variant="h6" sx={{ fontWeight: 700, flex: 1 }}>
              Prod Access
            </Typography>
            {status.data?.paused && <Chip size="small" color="warning" label="Dispatch paused" />}
            <Typography variant="body2" color="text.secondary">
              {me.data?.user ?? me.error?.message}
            </Typography>
          </Toolbar>
        </AppBar>
        <Drawer variant="permanent" sx={{ width: DRAWER_WIDTH, flexShrink: 0, '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}>
          <Toolbar variant="dense" />
          <List dense>
            {NAV.map((item) => (
              <ListItemButton key={item.path} component={NavLink} to={item.path} selected={pathname.startsWith(item.path)}>
                <ListItemIcon sx={{ minWidth: 36 }}>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItemButton>
            ))}
          </List>
        </Drawer>
        <Box component="main" sx={{ flex: 1, minWidth: 0, p: 3 }}>
          <Toolbar variant="dense" />
          <Box sx={{ maxWidth: 1280, mx: 'auto' }}>
            <Outlet />
          </Box>
        </Box>
      </Box>
    </CurrentUserContext>
  );
}
