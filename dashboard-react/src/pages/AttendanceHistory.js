import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { attendanceService, deviceService, authService, exportToExcel } from '../services/apiService';
import { FiRefreshCw, FiFilter, FiDownload, FiEye, FiArrowRight, FiArrowLeft } from 'react-icons/fi';
import { useAttendanceSocket } from '../hooks/useAttendanceSocket';
import { fmtCccd } from '../utils/cccdUtils';

const TABS = [
  { key: 'all',           label: 'Tất cả',           color: '#4f46e5' },
  { key: 'success',       label: 'Thành công',        color: '#16a34a' },
  { key: 'failed',        label: 'Thất bại',          color: '#dc2626' },
  { key: 'overtime',      label: 'Quá thời gian vào', color: '#d97706' },
  { key: 'remote_unlock', label: 'Mở cửa từ xa',      color: '#7c3aed' },
];

const fmt = (d) => new Date(d).toLocaleString('vi-VN');

const isServer = (r) => {
  const m = r.comparisonMode || r.comparisonMethod;
  return m && m !== 'APP_OFFLINE';
};

const getDirectionLabel = (direction) => {
  const dirMap = { 'IN': 'Vào', 'OUT': 'Ra' };
  return dirMap[direction] || (direction ? direction : '-');
};

const getDirectionBadgeColor = (direction) => {
  const colorMap = {
    'IN': { background: '#dbeafe', color: '#0284c7', icon: FiArrowRight, text: 'Vào' },
    'OUT': { background: '#fecaca', color: '#dc2626', icon: FiArrowLeft, text: 'Ra' }
  };
  return colorMap[direction] || { background: '#f3f4f6', color: '#6b7280', icon: null, text: '-' };
};

export default function AttendanceHistory() {
  const navigate = useNavigate();
  const user = authService.getUser();
  const role = (user?.role || '').toLowerCase();
  const isAdminLevel = ['admin', 'super_admin'].includes(role);

  
  const [devices, setDevices] = useState([]);
  const [devLoading, setDevLoading] = useState(false);
  const [devSearch, setDevSearch] = useState('');

  
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [tab, setTab] = useState('all');
  const [exporting, setExporting] = useState(false);
  const [filters, setFilters] = useState({ dateFrom: '', dateTo: '', cccd: '', deviceCode: '' });

  const setF = (k, v) => setFilters((f) => ({ ...f, [k]: v }));

  
  useEffect(() => {
    if (isAdminLevel) return;
    setDevLoading(true);
    deviceService.getAll()
      .then((res) => {
        const list = res.data?.data?.content || res.data?.data || res.data || [];
        setDevices(Array.isArray(list) ? list : []);
      })
      .catch(() => setDevices([]))
      .finally(() => setDevLoading(false));
  }, [isAdminLevel]);

  const fetchLogs = useCallback(async () => {
    if (!isAdminLevel) return;
    setLoading(true);
    try {
      let res;
      if (tab === 'overtime') {
        res = await attendanceService.getViolations({
          page, size: 20,
          ...(filters.deviceCode && { deviceCode: filters.deviceCode }),
        });
      } else if (tab === 'remote_unlock') {
        res = await attendanceService.getSpecialCases({
          page, size: 20,
          ...(filters.deviceCode && { deviceCode: filters.deviceCode }),
          ...(filters.dateFrom && { startDate: filters.dateFrom }),
          ...(filters.dateTo && { endDate: filters.dateTo }),
        });
      } else {
        res = await attendanceService.getHistory({
          page, size: 20, sort: 'scanTime,desc',
          ...(filters.dateFrom && { startDate: filters.dateFrom }),
          ...(filters.dateTo && { endDate: filters.dateTo }),
          ...(filters.cccd && { cccd: filters.cccd }),
          ...(filters.deviceCode && { deviceCode: filters.deviceCode }),
          ...(tab === 'success' && { accessGranted: true }),
          ...(tab === 'failed' && { accessGranted: false }),
        });
      }
      const d = res.data?.data || res.data || {};
      const content = d.content || (Array.isArray(d) ? d : []);
      const filtered = tab === 'failed'
        ? content.filter((r) => !r.alertType || r.alertType !== 'OUTSIDE_HOURS')
        : content;
      setLogs(filtered);
      setTotalPages(d.totalPages || 0);
      setTotal(d.totalElements || content.length);
    } catch { setLogs([]); }
    finally { setLoading(false); }
  }, [page, filters, tab, isAdminLevel]);

  useEffect(() => { if (isAdminLevel) fetchLogs(); }, [fetchLogs, isAdminLevel]);

  
  useAttendanceSocket((log) => {
    if (!isAdminLevel || tab !== 'all' || page !== 0) return;
    setLogs((prev) => [log, ...prev.slice(0, 19)]);
    setTotal((t) => t + 1);
  });

  const handleFilter = (e) => { e.preventDefault(); setPage(0); fetchLogs(); };
  const handleTabChange = (newTab) => { setTab(newTab); setPage(0); };

  const handleExport = async () => {
    setExporting(true);
    try {
      let allLogs = [];
      if (tab === 'overtime') {
        const res = await attendanceService.getViolations({
          page: 0, size: 9999,
          ...(filters.deviceCode && { deviceCode: filters.deviceCode }),
        });
        const d = res.data?.data || res.data || {};
        allLogs = d.content || [];
      } else if (tab === 'remote_unlock') {
        const res = await attendanceService.getSpecialCases({
          page: 0, size: 9999,
          ...(filters.deviceCode && { deviceCode: filters.deviceCode }),
          ...(filters.dateFrom && { startDate: filters.dateFrom }),
          ...(filters.dateTo && { endDate: filters.dateTo }),
        });
        const d = res.data?.data || res.data || {};
        allLogs = d.content || [];
      } else {
        const res = await attendanceService.getHistory({
          page: 0, size: 9999, sort: 'scanTime,desc',
          ...(filters.dateFrom && { startDate: filters.dateFrom }),
          ...(filters.dateTo && { endDate: filters.dateTo }),
          ...(filters.cccd && { cccd: filters.cccd }),
          ...(filters.deviceCode && { deviceCode: filters.deviceCode }),
          ...(tab === 'success' && { accessGranted: true }),
          ...(tab === 'failed' && { accessGranted: false }),
        });
        const d = res.data?.data || res.data || {};
        allLogs = tab === 'failed'
          ? (d.content || []).filter((r) => !r.alertType || r.alertType !== 'OUTSIDE_HOURS')
          : (d.content || []);
      }
      const tabLabel = TABS.find((t) => t.key === tab)?.label || 'tat-ca';
      const dateTag = filters.dateFrom ? `${filters.dateFrom}_${filters.dateTo || 'now'}` : new Date().toISOString().slice(0, 10);
      const rows = allLogs.map((r) => ({
        'Số CCCD': fmtCccd(r.cccd),
        'Họ tên': r.fullName || r.employeeName || r.capturedName || '',
        'Thiết bị': r.deviceCode || '',
        'Chiều di chuyển': getDirectionLabel(r.direction),
        'Thời gian': r.scanTime ? new Date(r.scanTime).toLocaleString('vi-VN') : '',
        'Độ khớp (%)': r.matchScore != null ? (r.matchScore * 100).toFixed(1) : r.score != null ? (r.score * 100).toFixed(1) : '',
        'Phương thức': isServer(r) ? 'Server' : 'App',
        'Kết quả': (r.accessGranted ?? r.matched) ? 'Vào' : 'Từ chối',
        'Ghi chú': r.alertType || '',
      }));
      exportToExcel(rows, `diem-danh-${tabLabel}-${dateTag}.xlsx`, 'Điểm danh');
    } catch (e) { console.error(e); }
    finally { setExporting(false); }
  };

  const isSpecial = (r) => {
    const mode = r.comparisonMode || r.comparisonMethod;
    return mode === 'REMOTE_UNLOCK';
  };

  const tabBtnStyle = (key) => {
    const t = TABS.find((t) => t.key === key);
    const active = tab === key;
    return {
      padding: '9px 20px',
      border: 'none',
      borderBottom: active ? `2.5px solid ${t.color}` : '2.5px solid transparent',
      background: 'none',
      cursor: 'pointer',
      fontWeight: active ? 700 : 400,
      color: active ? t.color : '#6b7280',
      fontSize: 14,
      transition: 'all 0.15s',
      whiteSpace: 'nowrap',
    };
  };

  
  if (!isAdminLevel) {
    const filteredDevs = devices.filter((d) =>
      `${d.deviceCode} ${d.locationName || d.location || ''}`.toLowerCase().includes(devSearch.toLowerCase())
    );
    const reloadDevices = () => {
      setDevLoading(true);
      deviceService.getAll()
        .then((res) => { const list = res.data?.data?.content || res.data?.data || res.data || []; setDevices(Array.isArray(list) ? list : []); })
        .catch(() => setDevices([]))
        .finally(() => setDevLoading(false));
    };
    return (
      <div className="page">
        <div className="page-header">
          <h1>Điểm danh theo thiết bị</h1>
          <div className="page-header-actions">
            <button className="btn btn-secondary btn-sm" onClick={reloadDevices} disabled={devLoading}>
              <FiRefreshCw size={13} className={devLoading ? 'spin' : ''} /> Làm mới
            </button>
          </div>
        </div>
        <div className="card">
          <div style={{ marginBottom: 14 }}>
            <input
              className="filters input"
              placeholder="Tìm kiếm thiết bị..."
              value={devSearch}
              onChange={(e) => setDevSearch(e.target.value)}
              style={{ padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13, width: 260 }}
            />
          </div>
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th>Mã thiết bị</th>
                  <th>Vị trí</th>
                  <th>Xem điểm danh</th>
                </tr>
              </thead>
              <tbody>
                {devLoading ? (
                  <tr><td colSpan={3} className="empty-state"><div className="spinner spinner-dark" /></td></tr>
                ) : filteredDevs.length === 0 ? (
                  <tr><td colSpan={3} className="empty-state">Không có thiết bị nào</td></tr>
                ) : filteredDevs.map((d) => (
                  <tr key={d.id}>
                    <td style={{ fontFamily: 'monospace', fontWeight: 600 }}>{d.deviceCode}</td>
                    <td>{d.locationName || d.location || '-'}</td>
                    <td>
                      <button
                        className="btn btn-sm"
                        style={{ background: '#2563eb', color: 'white', border: 'none', borderRadius: 5, padding: '4px 8px', cursor: 'pointer', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}
                        onClick={() => navigate(`/devices/${d.deviceCode}`)}
                      >
                        <FiEye size={12} /> Xem
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    );
  }

  
  return (
    <div className="page">
      <div className="page-header">
        <h1>
          Lịch sử ra vào{' '}
          {total > 0 && <span style={{ fontSize: 15, fontWeight: 400, color: '#6b7280' }}>({total} bản ghi)</span>}
        </h1>
        <div className="page-header-actions">
          <button className="btn btn-success btn-sm" onClick={handleExport} disabled={exporting || loading}>
            <FiDownload size={13} /> {exporting ? 'Đang xuất...' : 'Xuất Excel'}
          </button>
          <button className="btn btn-secondary btn-sm" onClick={fetchLogs} disabled={loading}>
            <FiRefreshCw size={13} className={loading ? 'spin' : ''} /> Làm mới
          </button>
        </div>
      </div>

      {}
      <div className="card" style={{ padding: '14px 18px', borderRadius: 8, marginBottom: 0 }}>
        <form onSubmit={handleFilter} style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'flex-end' }}>
          <div>
            <label style={{ fontSize: 12, color: '#6b7280', display: 'block', marginBottom: 4 }}>Từ ngày</label>
            <input type="date" value={filters.dateFrom} onChange={(e) => setF('dateFrom', e.target.value)} style={{ padding: '7px 10px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13 }} />
          </div>
          <div>
            <label style={{ fontSize: 12, color: '#6b7280', display: 'block', marginBottom: 4 }}>Đến ngày</label>
            <input type="date" value={filters.dateTo} onChange={(e) => setF('dateTo', e.target.value)} style={{ padding: '7px 10px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13 }} />
          </div>
          <div>
            <label style={{ fontSize: 12, color: '#6b7280', display: 'block', marginBottom: 4 }}>Số CCCD</label>
            <input placeholder="034091..." value={filters.cccd} onChange={(e) => setF('cccd', e.target.value)} style={{ padding: '7px 10px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13, width: 140 }} />
          </div>
          <div>
            <label style={{ fontSize: 12, color: '#6b7280', display: 'block', marginBottom: 4 }}>Mã thiết bị</label>
            <input placeholder="GATE_A..." value={filters.deviceCode} onChange={(e) => setF('deviceCode', e.target.value)} style={{ padding: '7px 10px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13, width: 120 }} />
          </div>
          <button type="submit" className="btn btn-primary btn-sm"><FiFilter size={13} /> Lọc</button>
          <button type="button" className="btn btn-secondary btn-sm"
            onClick={() => { setFilters({ dateFrom: '', dateTo: '', cccd: '', deviceCode: '' }); setPage(0); }}>
            Xoá lọc
          </button>
        </form>
      </div>

      {}
      <div style={{ display: 'flex', background: '#fff', borderBottom: '1px solid #e5e7eb', borderRadius: '8px 8px 0 0', marginTop: 16, paddingLeft: 4, overflowX: 'auto' }}>
        {TABS.map((t) => (
          <button key={t.key} style={tabBtnStyle(t.key)} onClick={() => handleTabChange(t.key)}>
            {t.label}
          </button>
        ))}
      </div>

      <div className="card" style={{ borderRadius: '0 0 8px 8px', marginTop: 0 }}>
        {loading ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : (
          <>
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th>Số CCCD</th>
                    <th>Họ tên</th>
                    <th>Thiết bị</th>
                    <th>Chiều di chuyển</th>
                    <th>Thời gian</th>
                    <th>Trạng thái</th>
                    <th>Ảnh selfie</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.length === 0 ? (
                    <tr><td colSpan={7} className="empty-state">Không có dữ liệu điểm danh</td></tr>
                  ) : logs.map((r) => {
                    const granted = r.accessGranted ?? r.matched;
                    const isOutsideHours = r.alertType === 'OUTSIDE_HOURS';
                    const special = isSpecial(r);
                    const directionBg = getDirectionBadgeColor(r.direction);
                    return (
                      <tr key={r.id} style={special ? { background: '#f8fafc' } : {}}>
                        <td style={{ fontFamily: 'monospace', fontSize: 12 }}>{fmtCccd(r.cccd)}</td>
                        <td style={{ fontWeight: 500 }}>{r.fullName || r.employeeName || r.capturedName || '-'}</td>
                        <td><code style={{ fontSize: 11 }}>{r.deviceCode || '-'}</code></td>
                        <td>
                          <span className="badge" style={{ ...directionBg, fontSize: 11, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 4, width: 'fit-content' }}>
                            {directionBg.icon && <directionBg.icon size={14} />}
                            {getDirectionLabel(r.direction)}
                          </span>
                        </td>
                        <td style={{ whiteSpace: 'nowrap', fontSize: 12 }}>{r.scanTime ? fmt(r.scanTime) : '-'}</td>
                        <td>
                          {special ? (
                            <span className="badge" style={{ background: '#ede9fe', color: '#7c3aed', fontSize: 11 }}>Mở từ xa</span>
                          ) : isOutsideHours ? (
                            <span className="badge" style={{ background: '#fef3c7', color: '#d97706', fontSize: 11 }}>Quá giờ</span>
                          ) : (
                            <span className={`badge badge-${granted ? 'success' : 'danger'}`}>
                              {granted ? 'Vào' : 'Từ chối'}
                            </span>
                          )}
                        </td>
                        <td>
                          {(r.selfieImage || r.imageLiveUrl) ? (
                            <img
                              src={(r.selfieImage || r.imageLiveUrl).startsWith('data:') ? (r.selfieImage || r.imageLiveUrl) : `data:image/jpeg;base64,${r.selfieImage || r.imageLiveUrl}`}
                              alt="selfie"
                              className="photo-thumb"
                              onClick={() => setSelected(r)}
                              onError={(e) => { e.target.style.display = 'none'; }}
                            />
                          ) : (
                            <span style={{ fontSize: 12, color: '#9ca3af' }}>-</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="pagination">
                <button className="page-btn" onClick={() => setPage(0)} disabled={page === 0}>«</button>
                <button className="page-btn" onClick={() => setPage((p) => p - 1)} disabled={page === 0}>‹</button>
                {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
                  const p = Math.max(0, Math.min(page - 3, totalPages - 7)) + i;
                  return <button key={p} className={`page-btn ${p === page ? 'active' : ''}`} onClick={() => setPage(p)}>{p + 1}</button>;
                })}
                <button className="page-btn" onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages - 1}>›</button>
                <button className="page-btn" onClick={() => setPage(totalPages - 1)} disabled={page >= totalPages - 1}>»</button>
                <span style={{ marginLeft: 8, fontSize: 13, color: '#6b7280' }}>Trang {page + 1}/{totalPages}</span>
              </div>
            )}
          </>
        )}
      </div>

      {}
      {selected && (
        <div className="modal-overlay" onClick={() => setSelected(null)}>
          <div className="modal" style={{ maxWidth: 420 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Chi tiết điểm danh</h3>
              <button className="modal-close" onClick={() => setSelected(null)}>×</button>
            </div>
            <div className="modal-body" style={{ textAlign: 'center' }}>
              {(selected.selfieImage || selected.imageLiveUrl) ? (
                <img
                  src={(selected.selfieImage || selected.imageLiveUrl).startsWith('data:')
                    ? (selected.selfieImage || selected.imageLiveUrl)
                    : `data:image/jpeg;base64,${selected.selfieImage || selected.imageLiveUrl}`}
                  alt="selfie"
                  style={{ width: '100%', maxWidth: 280, borderRadius: 10, border: '1px solid #e5e7eb', marginBottom: 16 }}
                  onError={(e) => { e.target.style.display = 'none'; }}
                />
              ) : (
                <div style={{ height: 140, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#9ca3af', fontSize: 13, marginBottom: 16 }}>
                  Không có ảnh selfie
                </div>
              )}
              <div style={{ fontSize: 13, textAlign: 'left', display: 'grid', gridTemplateColumns: '1fr 1.2fr', gap: '8px 12px' }}>
                {[
                  ['Số CCCD', fmtCccd(selected.cccd)],
                  ['Họ tên', selected.fullName || selected.employeeName || selected.capturedName],
                  ['Thiết bị', selected.deviceCode],
                  ['Thời gian', selected.scanTime ? fmt(selected.scanTime) : '-'],
                ].map(([k, v]) => (
                  <React.Fragment key={k}>
                    <span style={{ color: '#6b7280', fontWeight: 500 }}>{k}</span>
                    <span style={{ fontWeight: 600, color: '#1f2937' }}>{v ?? '-'}</span>
                  </React.Fragment>
                ))}
              </div>
              
              <div style={{ background: '#f8fafc', border: '2px solid #e5e7eb', borderRadius: 8, padding: 12, margin: '12px 0' }}>
                <div style={{ fontSize: 12, color: '#6b7280', fontWeight: 600, marginBottom: 8 }}>CHIỀU DI CHUYỂN</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {(() => {
                    const dirBg = getDirectionBadgeColor(selected.direction);
                    const Icon = dirBg.icon;
                    return (
                      <span className="badge" style={{ ...dirBg, fontSize: 14, fontWeight: 700, padding: '6px 12px', display: 'flex', alignItems: 'center', gap: 6 }}>
                        {Icon && <Icon size={18} />}
                        {getDirectionLabel(selected.direction)}
                      </span>
                    );
                  })()}
                </div>
              </div>

              <div style={{ fontSize: 13, textAlign: 'left', display: 'grid', gridTemplateColumns: '1fr 1.2fr', gap: '8px 12px' }}>
                {[
                  ['Kết quả', (selected.accessGranted ?? selected.matched) ? 'Thành công' : 'Thất bại'],
                  ['Phương thức', isServer(selected) ? 'Server-side' : 'App-side'],
                  ['Ghi chú', selected.alertType || '-'],
                  ['Đặc biệt', isSpecial(selected) ? 'Admin mở cửa từ xa' : 'Không'],
                ].map(([k, v]) => (
                  <React.Fragment key={k}>
                    <span style={{ color: '#6b7280', fontWeight: 500 }}>{k}</span>
                    <span style={{ fontWeight: 600, color: '#1f2937' }}>{v ?? '-'}</span>
                  </React.Fragment>
                ))}
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setSelected(null)}>Đóng</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}