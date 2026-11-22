import ChevronLeft from '@mui/icons-material/ChevronLeft';
import ChevronRight from '@mui/icons-material/ChevronRight';
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined';
import LightModeOutlined from '@mui/icons-material/LightModeOutlined';
import MenuOutlined from '@mui/icons-material/MenuOutlined';
import TerminalOutlined from '@mui/icons-material/TerminalOutlined';
import { Avatar, Box, Chip, Divider, Drawer, IconButton, Link, List, ListItemButton, ListItemIcon, Stack, Tooltip, Typography } from '@mui/material';
import { useColorScheme } from '@mui/material/styles';
import { useLayoutEffect, useRef, useState } from 'react';
import { Link as RouterLink, NavLink, Outlet, useLocation } from 'react-router';
import { api } from '../../api';
import { CurrentUserContext } from '../../hooks/useCurrentUser';
import { useResource } from '../../hooks/useResource';
import { NAV } from '../../nav';
import { consoleHeaderHeight } from '../../theme';

const sidebarWidth = 218;
const collapsedWidth = 60;

export function AppShell() {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const { pathname } = useLocation();
  const main = useRef<HTMLElement>(null);
  useLayoutEffect(() => { main.current?.scrollTo(0, 0); }, [pathname]);
  const me = useResource('me', api.me);
  const status = useResource('admin-status', api.adminStatus, 15000);
  const user = me.data?.user ?? me.error?.message;

  return (
    <CurrentUserContext value={me.data ?? null}>
      <Box sx={{ display: 'flex', height: '100dvh', overflow: 'hidden', bgcolor: 'background.default' }}>
        <Link href="#main-content" sx={{ position: 'absolute', top: -60, left: 8, zIndex: 'tooltip', bgcolor: 'background.paper', p: 1, '&:focus': { top: 8 } }}>
          Skip to content
        </Link>
        <Box component="aside" sx={{ display: { xs: 'none', md: 'flex' }, width: collapsed ? collapsedWidth : sidebarWidth, flexShrink: 0, bgcolor: 'background.paper', borderRight: 1, borderColor: 'divider' }}>
          <Sidebar collapsed={collapsed} user={user} paused={status.data?.paused} onToggle={() => setCollapsed(!collapsed)} />
        </Box>
        <Drawer open={mobileOpen} onClose={() => setMobileOpen(false)} sx={{ display: { md: 'none' } }} slotProps={{ paper: { sx: { width: sidebarWidth, backgroundImage: 'none' } } }}>
          <Sidebar user={user} paused={status.data?.paused} onNavigate={() => setMobileOpen(false)} />
        </Drawer>
        <Stack sx={{ flex: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', display: { md: 'none' }, flexShrink: 0, minHeight: 48, px: 1.5, bgcolor: 'background.paper', borderBottom: 1, borderColor: 'divider' }}>
            <IconButton aria-label="Open navigation" aria-expanded={mobileOpen} onClick={() => setMobileOpen(true)} size="small"><MenuOutlined /></IconButton>
            <Brand />
            {status.data?.paused && <Chip size="small" color="warning" label="Dispatch paused" />}
          </Stack>
          <Box component="main" id="main-content" ref={main} tabIndex={-1} sx={{ minWidth: 0, flex: 1, overflow: 'auto', outline: 0 }}>
            <Outlet />
          </Box>
        </Stack>
      </Box>
    </CurrentUserContext>
  );
}

function Brand({ collapsed = false, onNavigate }: { collapsed?: boolean; onNavigate?: () => void }) {
  return (
    <Link component={RouterLink} to="/" onClick={onNavigate} aria-label="Prod Access home" underline="none" color="text.primary" sx={{ display: 'inline-flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
      <Box sx={{ display: 'grid', placeItems: 'center', bgcolor: 'primary.main', color: 'primary.contrastText', flexShrink: 0, width: 29, height: 29, borderRadius: 1 }}>
        <TerminalOutlined fontSize="small" />
      </Box>
      {!collapsed && <Typography noWrap sx={{ fontWeight: 750, fontSize: '0.9rem' }}>Prod Access</Typography>}
    </Link>
  );
}

function Sidebar({ collapsed = false, onToggle, onNavigate, user, paused }: {
  collapsed?: boolean; onToggle?: () => void; onNavigate?: () => void; user?: string; paused?: boolean;
}) {
  const { mode, systemMode, setMode } = useColorScheme();
  const dark = (mode === 'system' ? systemMode : mode) === 'dark';
  const appearanceLabel = dark ? 'Use light theme' : 'Use dark theme';
  const collapseLabel = collapsed ? 'Expand navigation' : 'Collapse navigation';

  return (
    <Stack sx={{ width: '100%', height: '100%', minHeight: 0 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: collapsed ? 'center' : 'space-between', height: consoleHeaderHeight, flexShrink: 0, px: collapsed ? 0 : 1.75, borderBottom: 1, borderColor: 'divider' }}>
        <Brand collapsed={collapsed} onNavigate={onNavigate} />
        {onToggle && !collapsed && <Tooltip title={collapseLabel}><IconButton aria-label={collapseLabel} size="small" onClick={onToggle}><ChevronLeft fontSize="small" /></IconButton></Tooltip>}
      </Box>
      <List component="nav" aria-label="Main navigation" disablePadding sx={{ flex: 1, overflow: 'auto', px: 1, pt: 1 }}>
        {NAV.map((group, index) => (
          <Box key={group.label} sx={{ mb: 1.5 }}>
            {collapsed ? index > 0 && <Divider sx={{ mb: 1 }} /> : <Typography component="div" variant="caption" sx={{ color: 'text.secondary', px: 1, py: 0.5, fontWeight: 650 }}>{group.label}</Typography>}
            {group.items.map((item) => (
              <Tooltip key={item.path} title={collapsed ? item.label : ''} placement="right">
                <ListItemButton component={NavLink} to={item.path} onClick={onNavigate} aria-label={item.label} sx={{
                  minHeight: 36, mb: 0.25, px: 1, justifyContent: collapsed ? 'center' : 'flex-start', gap: 1,
                  borderRadius: 1, color: 'text.secondary', fontSize: '0.8rem', fontWeight: 550,
                  '&[aria-current="page"]': { bgcolor: 'action.selected', color: 'primary.main', fontWeight: 700 },
                }}>
                  <ListItemIcon sx={{ minWidth: 0, color: 'inherit' }}>{item.icon}</ListItemIcon>
                  {!collapsed && item.label}
                </ListItemButton>
              </Tooltip>
            ))}
          </Box>
        ))}
      </List>
      <Stack sx={{ p: 1, borderTop: 1, borderColor: 'divider', gap: 0.75 }}>
        {paused && <Tooltip title="Dispatch paused"><Chip size="small" color="warning" label={collapsed ? 'Ⅱ' : 'Dispatch paused'} /></Tooltip>}
        {collapsed && onToggle && <Tooltip title={collapseLabel} placement="right"><IconButton aria-label={collapseLabel} size="small" onClick={onToggle}><ChevronRight fontSize="small" /></IconButton></Tooltip>}
        <Stack direction={collapsed ? 'column' : 'row'} sx={{ minWidth: 0, alignItems: 'center', gap: 1 }}>
          {user && <Avatar sx={{ width: 25, height: 25, fontSize: '0.72rem', bgcolor: 'action.selected', color: 'text.primary' }} aria-label={user}>{user[0]?.toUpperCase()}</Avatar>}
          {!collapsed && <Typography noWrap variant="body2" sx={{ flex: 1 }}>{user}</Typography>}
          <Tooltip title={appearanceLabel} placement={collapsed ? 'right' : 'top'}>
            <IconButton aria-label={appearanceLabel} size="small" onClick={() => setMode(dark ? 'light' : 'dark')}>
              {dark ? <LightModeOutlined fontSize="small" /> : <DarkModeOutlined fontSize="small" />}
            </IconButton>
          </Tooltip>
        </Stack>
      </Stack>
    </Stack>
  );
}
