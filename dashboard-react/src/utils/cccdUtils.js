

export const fmtCccd = (cccd) => {
  if (!cccd) return '';
  const s = String(cccd).trim();
  return s.length === 12 ? s.slice(3) : s;
};
