import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField } from '@mui/material';
import { useState } from 'react';

interface Props {
  open: boolean;
  title: string;
  text: string;
  confirmLabel: string;
  /** Ask for a free-text value (e.g. a reason) as well as confirmation. */
  inputLabel?: string;
  danger?: boolean;
  onConfirm: (input: string) => void;
  onClose: () => void;
}

export function PromptDialog({ open, title, text, confirmLabel, inputLabel, danger, onConfirm, onClose }: Props) {
  const [input, setInput] = useState('');
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{text}</DialogContentText>
        {inputLabel && <TextField autoFocus fullWidth margin="normal" label={inputLabel} value={input} onChange={(e) => setInput(e.target.value)} />}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" color={danger ? 'error' : 'primary'} onClick={() => onConfirm(input)}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
