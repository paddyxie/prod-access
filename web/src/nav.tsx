import AccountTreeOutlined from '@mui/icons-material/AccountTreeOutlined';
import ApiOutlined from '@mui/icons-material/ApiOutlined';
import BusinessOutlined from '@mui/icons-material/BusinessOutlined';
import DnsOutlined from '@mui/icons-material/DnsOutlined';
import HistoryOutlined from '@mui/icons-material/HistoryOutlined';
import ManageSearchOutlined from '@mui/icons-material/ManageSearchOutlined';
import PlayCircleOutlined from '@mui/icons-material/PlayCircleOutlined';

export const NAV = [
  { label: 'Operations', items: [
    { path: '/runs', label: 'Executions', icon: <PlayCircleOutlined fontSize="small" /> },
    { path: '/workflows', label: 'Workflows', icon: <AccountTreeOutlined fontSize="small" /> },
    { path: '/audit', label: 'Tool audit', icon: <ManageSearchOutlined fontSize="small" /> },
  ] },
  { label: 'Catalog', items: [
    { path: '/services', label: 'Services', icon: <BusinessOutlined fontSize="small" /> },
    { path: '/systems', label: 'Systems', icon: <DnsOutlined fontSize="small" /> },
    { path: '/tools', label: 'Tools', icon: <ApiOutlined fontSize="small" /> },
  ] },
  { label: 'Archive', items: [
    { path: '/legacy', label: 'Legacy audit', icon: <HistoryOutlined fontSize="small" /> },
  ] },
];
