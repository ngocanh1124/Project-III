import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { attendanceService, deviceService, authService, exportToExcel } from '../services/apiService';
import { FiArrowLeft, FiUnlock, FiRefreshCw, FiDownload } from 'react-icons/fi';
import { fmtCccd } from '../utils/cccdUtils';
import { useAttendanceSocket, useDeviceLogSocket, usePendingAccessSocket } from '../hooks/useAttendanceSocket';

const fmt = (d) => new Date(d).toLocaleString('vi-VN');
const fmtScore = (s) => s != null ? `${(s * 100).toFixed(1)}%` : '-';
const fmtDateOnly = (d) => new Date(d).toLocaleDateString('vi-VN');

const groupLogsByDate = (logs) => {
  const grouped = {};
  logs.forEach((log) => {
    const date = log.scanTime ? fmtDateOnly(log.scanTime) : 'Unknown';
    if (!grouped[date]) grouped[date] = [];
    grouped[date].push(log);
  });
  return Object.entries(grouped).sort((a, b) => new Date(b[0]) - new Date(a[0]));
};

export default function DeviceDashboard() {
  const { deviceCode } = useParams();
  const navigate = useNavigate();
  const [device, setDevice] = useState(null);
  const [stats, setStats] = useState(null);
  const [logs, setLogs] = useState([]);
  const [specialCases, setSpecialCases] = useState([]);
  const [violationLogs, setViolationLogs] = useState([]);
  const [tab, setTab] = useState('success'); 
  const [loading, setLoading] = useState(true);
  const [selectedLog, setSelectedLog] = useState(null);
  const [unlocking, setUnlocking] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [filterDate, setFilterDate] = useState('');
  const [exportMonth, setExportMonth] = useState(new Date().toISOString().slice(0, 7));
  const [exporting, setExporting] = useState(false);
  const [msg, setMsg] = useState(null);
  const [unlockModal, setUnlockModal] = useState(false);
  const [unlockReason, setUnlockReason] = useState('');
  const [pendingRequests, setPendingRequests] = useState([]);
  const [approvingId, setApprovingId] = useState(null);

  
  const [esp32Logs, setEsp32Logs] = useState([]);
  const [relayStatus, setRelayStatus] = useState({ relay1: 'CLOSED', relay2: 'CLOSED', online: false });

  const user = authService.getUser();
  const isAdmin = ['admin', 'super_admin'].includes((user?.role || '').toLowerCase());
  const canUnlock = ['admin', 'super_admin', 'operator'].includes((user?.role || '').toLowerCase());

  const toast = (text, type = 'success') => {
    setMsg({ text, type });
    setTimeout(() => setMsg(null), 3000);
  };

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const [statsRes, histRes, specialRes, violationsRes] = await Promise.allSettled([
        deviceService.getStats(deviceCode),
        attendanceService.getHistory({
          deviceCode,
          page,
          size: 15,
          sort: 'scanTime,desc',
          startDate: filterDate || undefined,
          endDate: filterDate || undefined,
        }),
        attendanceService.getSpecialCases({ deviceCode, page: 0, size: 20 }),
        attendanceService.getViolations({ deviceCode, page: 0, size: 50 }),
      ]);

      if (statsRes.status === 'fulfilled') {
        const d = statsRes.value.data?.data || statsRes.value.data || {};
        setDevice(d);
        setStats(d);
      }
      if (histRes.status === 'fulfilled') {
        const d = histRes.value.data?.data || histRes.value.data || {};
        setLogs(d.content || []);
        setTotalPages(d.totalPages || 0);
      } else {
        console.error('DeviceDashboard history API failed:', histRes.reason);
      }
      if (specialRes.status === 'fulfilled') {
        const d = specialRes.value.data?.data || specialRes.value.data || {};
        setSpecialCases(d.content || []);
      }
      if (violationsRes.status === 'fulfilled') {
        const d = violationsRes.value.data?.data || violationsRes.value.data || {};
        setViolationLogs(d.content || []);
      }
    } finally {
      setLoading(false);
    }
  }, [deviceCode, page, filterDate]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  
  useAttendanceSocket((log) => {
    if (log.deviceCode !== deviceCode) return;
    const isUnlock = ['REMOTE_UNLOCK', 'REMOTE_ENTRY'].includes(log.comparisonMode || log.comparisonMethod);
    const isOutsideHours = log.alertType === 'OUTSIDE_HOURS' || log.status === 'OUTSIDE_HOURS';
    if (isUnlock) {
      setSpecialCases((prev) => [log, ...prev]);
    } else if (isOutsideHours) {
      setViolationLogs((prev) => [log, ...prev]);
    } else {
      setLogs((prev) => [log, ...prev.slice(0, 14)]);
      setStats((s) => s ? {
        ...s,
        todayAttempts: (s.todayAttempts || 0) + 1,
        todaySuccess: (s.todaySuccess || 0) + (log.accessGranted ? 1 : 0),
        monthAttempts: (s.monthAttempts || 0) + 1,
        monthSuccess: (s.monthSuccess || 0) + (log.accessGranted ? 1 : 0),
      } : s);
    }
  });

  
  usePendingAccessSocket((log) => {
    if (log.deviceCode !== deviceCode) return;
    setPendingRequests((prev) => {
      if (prev.some(r => r.id === log.id)) return prev;
      return [log, ...prev];
    });
  });

  
  useDeviceLogSocket((entry) => {
    setEsp32Logs((prev) => [{ ...entry, _id: Date.now() }, ...prev.slice(0, 49)]);
    if (entry.relay1 || entry.relay2) {
      setRelayStatus((s) => ({
        ...s,
        relay1: entry.relay1 || s.relay1,
        relay2: entry.relay2 || s.relay2,
        online: entry.online !== undefined ? entry.online : true,
      }));
    }
  }, deviceCode);

  const handleExportMonth = async () => {
    if (!exportMonth) { toast('Chọn tháng để xuất dữ liệu', 'error'); return; }
    setExporting(true);
    try {
      const res = await attendanceService.exportByDeviceMonth(deviceCode, exportMonth);
      const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `attendance_${deviceCode}_${exportMonth}.xlsx`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      toast(`Đã xuất báo cáo thiết bị ${deviceCode} tháng ${exportMonth}!`);
    } catch (err) {
      toast(`Lỗi xuất file: ${err?.response?.data?.message || err.message}`, 'error');
    } finally {
      setExporting(false);
    }
  };

  const handleUnlock = () => {
    setUnlockModal(true);
    setUnlockReason('');
  };

  const confirmUnlock = async () => {
    setUnlockModal(false);
    setUnlocking(true);
    try {
      await deviceService.remoteUnlock(deviceCode, unlockReason || 'Mở cửa từ xa');
      toast('Đã gửi yêu cầu quét CCCD tới thiết bị!');
      fetchAll();
    } catch {
      toast('Gửi lệnh mở cửa thất bại', 'error');
    } finally {
      setUnlocking(false);
    }
  };

  const confirmInstantUnlock = async () => {
    setUnlockModal(false);
    setUnlocking(true);
    try {
      await deviceService.instantUnlock(deviceCode, unlockReason || 'Admin mở cửa trực tiếp');
      toast('Cửa đã mở!');
      fetchAll();
    } catch {
      toast('Không thể mở cửa', 'error');
    } finally {
      setUnlocking(false);
    }
  };

  const handleApprove = async (logId, visitorName) => {
    setApprovingId(logId);
    try {
      await attendanceService.approvePendingAccess(logId);
      setPendingRequests((prev) => prev.filter(r => r.id !== logId));
      toast(`Đã mở cửa cho ${visitorName || 'khách'}!`);
    } catch (err) {
      toast(`Lỗi phê duyệt: ${err?.response?.data?.message || err.message}`, 'error');
    } finally {
      setApprovingId(null);
    }
  };

  const successLogs = logs.filter(r => r.accessGranted === true);
  const failedLogs = logs.filter(r => r.accessGranted !== true && r.alertType !== 'OUTSIDE_HOURS');
  const tableLogs = tab === 'success' ? successLogs : tab === 'failed' ? failedLogs : tab === 'special' ? specialCases : violationLogs;

  return (
    <div className="page">
      {}
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button className="btn btn-secondary btn-sm" onClick={() => navigate('/devices')}>
            <FiArrowLeft size={14} /> Quay lại
          </button>
          <div>
            <h1 style={{ marginBottom: 2 }}>Thiết bị: {deviceCode}</h1>
            <span style={{ fontSize: 13, color: '#6b7280' }}>
              {device?.location || device?.locationName || 'Đang tải...'}
              {device?.ipAddress ? ` · ${device.ipAddress}` : ''}            </span>
          </div>
        </div>
        <div className="page-header-actions">
          <button className="btn btn-secondary" onClick={fetchAll} disabled={loading}>
            <FiRefreshCw size={14} className={loading ? 'spin' : ''} /> Làm mới
          </button>
          {isAdmin && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <input
                type="month"
                value={exportMonth}
                onChange={(e) => setExportMonth(e.target.value)}
                style={{ padding: '6px 10px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13 }}
              />
              <button className="btn btn-secondary" onClick={handleExportMonth} disabled={exporting}>
                <FiDownload size={14} /> {exporting ? 'Đang xuất...' : 'Xuất Excel'}
              </button>
            </div>
          )}
          {!isAdmin && (
            <button className="btn btn-secondary" onClick={() => {
              const rows = tableLogs.map((r) => ({
                'Số CCCD': fmtCccd(r.cccd || r.employeeCccd || ''),
                'Họ tên': r.employeeName || '',
                'Thời gian': r.scanTime ? fmt(r.scanTime) : '',
                'Kết quả': r.accessGranted ? 'Thành công' : 'Thất bại',
                'Thiết bị': r.deviceCode || deviceCode,
              }));
              exportToExcel(rows, `diem-danh-${deviceCode}-${new Date().toISOString().slice(0,10)}.xlsx`, deviceCode);
            }}>
              <FiDownload size={14} /> Tải Excel
            </button>
          )}
          {canUnlock && (
          <button className="btn btn-success" onClick={handleUnlock} disabled={unlocking}>
            <FiUnlock size={14} /> {unlocking ? 'Đang gửi...' : 'Mở cửa từ xa'}
          </button>
          )}
        </div>
      </div>

      {msg && (
        <div style={{ padding: '10px 14px', borderRadius: 8, marginBottom: 14, fontSize: 13, background: msg.type === 'error' ? '#fee2e2' : '#dcfce7', color: msg.type === 'error' ? '#dc2626' : '#16a34a' }}>
          {msg.text}
        </div>
      )}

      {}
      <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(140px,1fr))' }}>
        {[
          { label: 'Tổng lượt tháng này', value: stats?.monthAttempts ?? '-', color: '' },
          { label: 'Thành công', value: stats?.monthSuccess ?? '-', color: '' },
          { label: 'Từ chối', value: (stats?.monthAttempts != null && stats?.monthSuccess != null) ? stats.monthAttempts - stats.monthSuccess : '-', color: '' },
        ].map((c) => (
          <div key={c.label} className={`stat-card ${c.color}`}>
            <div className="value">{c.value}</div>
            <div className="label">{c.label}</div>
          </div>
        ))}
      </div>

      {}
      {pendingRequests.length > 0 && (
        <div style={{ background: '#fffbeb', border: '2px solid #f59e0b', borderRadius: 10, padding: 16, marginBottom: 16 }}>
          <div style={{ fontWeight: 700, fontSize: 15, color: '#92400e', marginBottom: 10 }}>
            ⏳ Yêu cầu mở cửa đang chờ phê duyệt ({pendingRequests.length})
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {pendingRequests.map((req) => (
              <div key={req.id} style={{ display: 'flex', alignItems: 'center', gap: 12, background: 'white', borderRadius: 8, padding: '10px 14px', boxShadow: '0 1px 4px rgba(0,0,0,0.08)' }}>
                {req.selfieImage && (
                  <img
                    src={`data:image/jpeg;base64,${req.selfieImage}`}
                    alt="selfie"
                    style={{ width: 56, height: 56, borderRadius: 8, objectFit: 'cover', border: '2px solid #f59e0b' }}
                  />
                )}
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, fontSize: 14 }}>{req.capturedName || req.fullName || 'Khách'}</div>
                  <div style={{ fontSize: 12, color: '#6b7280' }}>CCCD: {fmtCccd(req.cccd)} · {req.scanTime ? fmt(req.scanTime) : ''}</div>
                  <div style={{ fontSize: 12, color: '#6b7280' }}>ID yêu cầu: #{req.id}</div>
                </div>
                {canUnlock && (
                  <button
                    className="btn btn-success"
                    style={{ minWidth: 100 }}
                    onClick={() => handleApprove(req.id, req.capturedName || req.fullName)}
                    disabled={approvingId === req.id}
                  >
                    {approvingId === req.id ? 'Đang mở...' : '🔓 Mở cửa'}
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {}
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14, flexWrap: 'wrap', gap: 10 }}>
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            <button className={`btn btn-sm ${tab === 'success' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setTab('success')}>
              Thành công
            </button>
            <button
              className={`btn btn-sm ${tab === 'failed' ? '' : 'btn-secondary'}`}
              style={tab === 'failed' ? { background: '#ef4444', color: 'white', border: 'none' } : {}}
              onClick={() => setTab('failed')}
            >
              Thất bại
            </button>
            <button className={`btn btn-sm ${tab === 'special' ? 'btn-warning' : 'btn-secondary'}`} onClick={() => setTab('special')}>
              Mở cửa từ xa ({specialCases.length})
            </button>
            <button
              className={`btn btn-sm ${tab === 'violations' ? '' : 'btn-secondary'}`}
              style={tab === 'violations' ? { background: '#f59e0b', color: 'white', border: 'none' } : {}}
              onClick={() => setTab('violations')}
            >
              ⚠ Vi phạm thời gian ({violationLogs.length})
            </button>
          </div>
          {(tab === 'success' || tab === 'failed') && (
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input type="date" value={filterDate} onChange={(e) => { setFilterDate(e.target.value); setPage(0); }}
                style={{ padding: '6px 10px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13 }} />
              {filterDate && <button className="btn btn-sm btn-secondary" onClick={() => setFilterDate('')}>Xoá lọc</button>}
            </div>
          )}
        </div>

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
                    <th>Thời gian</th>
                    {tab === 'special' ? <th>Lý do</th> : <th>Độ khớp</th>}
                    <th>Trạng thái</th>
                    <th>Ảnh</th>
                  </tr>
                </thead>
                <tbody>
                  {tableLogs.length === 0 ? (
                    <tr><td colSpan={6} className="empty-state">Không có dữ liệu</td></tr>
                  ) : (tab === 'success' || tab === 'failed') ? (
                    
                    groupLogsByDate(tableLogs).flatMap(([date, logsForDate]) => [
                      <tr key={`date-${date}`} style={{ background: '#f3f4f6' }}>
                        <td colSpan={6} style={{ fontWeight: 'bold', fontSize: '14px', padding: '10px 14px' }}>
                          {date}
                        </td>
                      </tr>,
                      ...logsForDate.map((r) => {
                        const granted = r.accessGranted ?? r.matched;
                        return (
                          <tr key={r.id}>
                            <td style={{ fontFamily: 'monospace' }}>{fmtCccd(r.cccd)}</td>
                            <td>{r.fullName || r.employeeName || r.capturedName || '-'}</td>
                            <td style={{ whiteSpace: 'nowrap', fontSize: 12 }}>{r.scanTime ? fmt(r.scanTime) : '-'}</td>
                            <td style={{ color: '#111827', fontSize: 13 }}>
                              <span className={`badge badge-${granted ? 'success' : 'danger'}`} style={{ fontSize: 11 }}>
                                {granted ? 'Thành công' : 'Thất bại'}
                              </span>
                            </td>
                            <td style={{ color: '#111827', fontSize: 13, fontWeight: 500 }}>
                              {granted ? 'Vào' : 'Từ chối'}
                            </td>
                            <td>
                              {(r.selfieImage || r.imageLiveUrl) ? (
                                <img
                                  src={r.selfieImage?.startsWith('data:') ? r.selfieImage : `data:image/jpeg;base64,${r.selfieImage || r.imageLiveUrl}`}
                                  alt="selfie"
                                  className="photo-thumb"
                                  onClick={() => setSelectedLog(r)}
                                  onError={(e) => { e.target.style.display = 'none'; }}
                                />
                              ) : <span style={{ fontSize: 12, color: '#9ca3af' }}>-</span>}
                            </td>
                          </tr>
                        );
                      }),
                    ])
                  ) : tab === 'violations' ? (
                    
                    groupLogsByDate(tableLogs).flatMap(([date, logsForDate]) => [
                      <tr key={`viol-date-${date}`} style={{ background: '#f3f4f6' }}>
                        <td colSpan={6} style={{ fontWeight: 'bold', fontSize: '14px', padding: '10px 14px', color: '#374151' }}>
                          ⚠ {date}
                        </td>
                      </tr>,
                      ...logsForDate.map((r) => (
                        <tr key={r.id} style={{ background: '#fafafa' }}>
                          <td style={{ fontFamily: 'monospace' }}>{fmtCccd(r.cccd) || '-'}</td>
                          <td>{r.fullName || r.employeeName || r.capturedName || '-'}</td>
                          <td style={{ whiteSpace: 'nowrap', fontSize: 12 }}>{r.scanTime ? fmt(r.scanTime) : '-'}</td>
                          <td>
                            <span style={{ fontSize: 11, padding: '2px 7px', borderRadius: 10, fontWeight: 700, background: '#fef3c7', color: '#92400e' }}>
                              Ngoài giờ
                            </span>
                          </td>
                          <td>
                            {(r.selfieImage || r.imageLiveUrl) ? (
                              <img
                                src={r.selfieImage?.startsWith('data:') ? r.selfieImage : `data:image/jpeg;base64,${r.selfieImage || r.imageLiveUrl}`}
                                alt="selfie"
                                className="photo-thumb"
                                onClick={() => setSelectedLog(r)}
                                onError={(e) => { e.target.style.display = 'none'; }}
                              />
                            ) : <span style={{ fontSize: 12, color: '#9ca3af' }}>-</span>}
                          </td>
                        </tr>
                      )),
                    ])
                  ) : (
                    
                    groupLogsByDate(tableLogs).flatMap(([date, logsForDate]) => [
                      <tr key={`sc-date-${date}`} style={{ background: '#f3f4f6' }}>
                        <td colSpan={6} style={{ fontWeight: 'bold', fontSize: '14px', padding: '10px 14px', color: '#374151' }}>
                          {date}
                        </td>
                      </tr>,
                      ...logsForDate.map((r) => {
                        const isGranted = r.accessGranted ?? r.matched;
                        return (
                            <tr key={r.id} style={{ background: '#fafafa' }}>
                            <td style={{ fontFamily: 'monospace' }}>{fmtCccd(r.cccd)}</td>
                            <td>{r.cccd ? (r.fullName || r.employeeName || '') : ''}</td>
                            <td style={{ whiteSpace: 'nowrap', fontSize: 12 }}>{r.scanTime ? fmt(r.scanTime) : '-'}</td>
                            <td>{r.reason || r.notes || '-'}</td>
                            <td>
                              <span className={`badge badge-${isGranted ? 'success' : 'danger'}`}>
                                {isGranted ? 'Thành công' : 'Thất bại'}
                              </span>
                            </td>
                            <td>
                              {(r.selfieImage || r.imageLiveUrl) ? (
                                <img
                                  src={r.selfieImage?.startsWith('data:') ? r.selfieImage : `data:image/jpeg;base64,${r.selfieImage || r.imageLiveUrl}`}
                                  alt="selfie"
                                  className="photo-thumb"
                                  onClick={() => setSelectedLog(r)}
                                  onError={(e) => { e.target.style.display = 'none'; }}
                                />
                              ) : <span style={{ fontSize: 12, color: '#9ca3af' }}>-</span>}
                            </td>
                          </tr>
                        );
                      }),
                    ])
                  )}
                </tbody>
              </table>
            </div>

            {}
            {(tab === 'success' || tab === 'failed') && totalPages > 1 && (
              <div className="pagination">
                <button className="page-btn" onClick={() => setPage(0)} disabled={page === 0}>«</button>
                <button className="page-btn" onClick={() => setPage((p) => p - 1)} disabled={page === 0}>‹</button>
                {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
                  const p = Math.max(0, Math.min(page - 2, totalPages - 5)) + i;
                  return (
                    <button key={p} className={`page-btn ${p === page ? 'active' : ''}`} onClick={() => setPage(p)}>{p + 1}</button>
                  );
                })}
                <button className="page-btn" onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages - 1}>›</button>
                <button className="page-btn" onClick={() => setPage(totalPages - 1)} disabled={page >= totalPages - 1}>»</button>
              </div>
            )}
          </>
        )}
      </div>

      {}
      {selectedLog && (
        <div className="modal-overlay" onClick={() => setSelectedLog(null)}>
          <div className="modal" style={{ maxWidth: 400 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Chi tiết điểm danh</h3>
              <button className="modal-close" onClick={() => setSelectedLog(null)}>×</button>
            </div>
            <div className="modal-body" style={{ textAlign: 'center' }}>
              <img
                src={selectedLog.selfieImage?.startsWith('data:') ? selectedLog.selfieImage : `data:image/jpeg;base64,${selectedLog.selfieImage || selectedLog.imageLiveUrl}`}
                alt="selfie"
                style={{ width: '100%', maxWidth: 260, borderRadius: 10, border: '1px solid #e5e7eb', marginBottom: 14 }}
                onError={(e) => { e.target.style.display = 'none'; }}
              />
              <div style={{ fontSize: 13, textAlign: 'left', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 0' }}>
                {[
                  ['CCCD', fmtCccd(selectedLog.cccd)],
                  ['Họ tên', selectedLog.fullName || selectedLog.capturedName],
                  ['Xác thực', (selectedLog.accessGranted ?? selectedLog.matched) ? 'Thành công' : 'Thất bại'],
                  ['Phương thức', (selectedLog.comparisonMode || selectedLog.comparisonMethod) && (selectedLog.comparisonMode || selectedLog.comparisonMethod) !== 'APP_OFFLINE' ? 'Server‑side' : 'App‑side'],
                  ['Kết quả', (selectedLog.accessGranted ?? selectedLog.matched) ? 'Vào' : 'Từ chối'],
                  ['Thời gian', selectedLog.scanTime ? fmt(selectedLog.scanTime) : '-'],
                ].map(([k, v]) => (
                  <React.Fragment key={k}>
                    <span style={{ color: '#6b7280' }}>{k}</span>
                    <span style={{ fontWeight: 600, color: '#1f2937' }}>{v ?? '-'}</span>
                  </React.Fragment>
                ))}
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setSelectedLog(null)}>Đóng</button>
            </div>
          </div>
        </div>
      )}
      {}
      {unlockModal && (
        <div className="modal-overlay" onClick={() => setUnlockModal(false)}>
          <div className="modal" style={{ maxWidth: 440 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3><FiUnlock size={16} style={{ marginRight: 6 }} />Mở cửa từ xa: {deviceCode}</h3>
              <button className="modal-close" onClick={() => setUnlockModal(false)}>×</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>Lý do mở cửa</label>
                <input
                  className="form-control"
                  placeholder="VD: Giao hàng, Sửa chữa, Khách họn..."
                  value={unlockReason}
                  onChange={(e) => setUnlockReason(e.target.value)}
                  autoFocus
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginTop: 16 }}>
                <div style={{ border: '2px solid #3b82f6', borderRadius: 10, padding: 14, cursor: 'pointer' }}
                  onClick={confirmUnlock}>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#111827' }}>Yêu cầu quét CCCD</div>
                  <div style={{ fontSize: 12, color: '#6b7280', marginTop: 4 }}>Khách vẫn cần quét QR + chip + khuôn mặt, nhưng bỏ qua kiểm tra phân quyền</div>
                </div>
                <div style={{ border: '2px solid #16a34a', borderRadius: 10, padding: 14, cursor: 'pointer' }}
                  onClick={confirmInstantUnlock}>
                  <div style={{ fontWeight: 700, fontSize: 14, color: '#111827' }}>Mở cửa ngay</div>
                  <div style={{ fontSize: 12, color: '#6b7280', marginTop: 4 }}>Mở cửa tịp thì không cần quét gì, cửa mở ngay lập tức</div>
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setUnlockModal(false)}>Huỷ</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
