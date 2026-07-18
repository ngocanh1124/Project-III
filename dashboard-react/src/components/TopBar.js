import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useLocation } from 'react-router-dom';
import { FiBell, FiLogOut, FiCheckCircle, FiXCircle, FiUnlock, FiAlertTriangle, FiMenu } from 'react-icons/fi';
import { authService, attendanceService } from '../services/apiService';
import { useAlertSocket } from '../hooks/useAttendanceSocket';
import './TopBar.css';

const PAGE_TITLES = {
  '/':           'Tổng quan',
  '/attendance': 'Lịch sử ra vào',
  '/employees':  'Quản lý nhân viên',
  '/devices':    'Quản lý thiết bị',
  '/reports':    'Báo cáo & Thống kê',
  '/settings':   'Cài đặt hệ thống',
};

function TopBar({ onMenuClick }) {
  const [showNotif, setShowNotif] = useState(false);
  const [notifs, setNotifs] = useState([]);
  const [unread, setUnread] = useState(0);
  const [alerts, setAlerts] = useState([]); 
  const [alertUnread, setAlertUnread] = useState(0);
  const dropRef = useRef();

  const location = useLocation();
  const title = Object.entries(PAGE_TITLES).find(
    ([key]) => key === '/' ? location.pathname === '/' : location.pathname.startsWith(key)
  )?.[1] || 'CCCD Access';

  const user = authService.getUser();
  const initials = (user.fullName || user.username || 'AD').slice(0, 2).toUpperCase();

  
  useEffect(() => {
    const handler = (e) => {
      if (dropRef.current && !dropRef.current.contains(e.target)) setShowNotif(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  
  useEffect(() => {
    const fetchNotifs = async () => {
      try {
        const res = await attendanceService.getHistory({ page: 0, size: 8 });
        const items = res.data?.data?.content || res.data?.content || [];
        setNotifs(items);
        if (!showNotif) {
          const denied = items.filter(n => !n.matched).length;
          setUnread(Math.min(denied, 9));
        }
      } catch {}
    };
    fetchNotifs();
    const t = setInterval(fetchNotifs, 30000);
    return () => clearInterval(t);
  }, [showNotif]); 

  
  const handleAlert = useCallback((log) => {
    setAlerts((prev) => [log, ...prev].slice(0, 20));
    setAlertUnread((n) => Math.min(n + 1, 99));
  }, []);
  useAlertSocket(handleAlert);

  const fmtTime = (d) => {
    if (!d) return '';
    const dt = new Date(d);
    return dt.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) + ' ' +
      dt.toLocaleDateString('vi-VN');
  };

  const handleBell = () => {
    setShowNotif(s => !s);
    setUnread(0);
    setAlertUnread(0);
  };

  return (
    <header className="topbar">
      <div className="topbar-left">
        <button className="icon-btn mobile-menu-btn" onClick={onMenuClick} aria-label="Menu">
          <FiMenu size={20} />
        </button>
        <h2 className="topbar-title">{title}</h2>
      </div>
      <div className="topbar-right">
        <div style={{ position: 'relative' }} ref={dropRef}>
          <button className="icon-btn" title="Thông báo" onClick={handleBell} style={{ position: 'relative' }}>
            <FiBell size={18} />
            {(unread + alertUnread) > 0 && (
              <span style={{
                position: 'absolute', top: 3, right: 3,
                width: 15, height: 15, borderRadius: '50%',
                background: alertUnread > 0 ? '#f97316' : '#ef4444', color: 'white',
                fontSize: 9, display: 'flex', alignItems: 'center',
                justifyContent: 'center', fontWeight: 700, lineHeight: 1,
                pointerEvents: 'none'
              }}>{Math.min(unread + alertUnread, 99)}</span>
            )}
          </button>
          {showNotif && (
            <div style={{
              position: 'absolute', top: 'calc(100% + 8px)', right: 0,
              width: 340, background: 'white', borderRadius: 10,
              boxShadow: '0 8px 24px rgba(0,0,0,0.18)', zIndex: 1000,
              overflow: 'hidden', border: '1px solid #e5e7eb'
            }}>
              {}
              {alerts.length > 0 && (
                <>
                  <div style={{ padding: '10px 16px', borderBottom: '1px solid #fed7aa', fontWeight: 700, fontSize: 13, color: '#c2410c', background: '#fff7ed', display: 'flex', alignItems: 'center', gap: 6 }}>
                    <FiAlertTriangle size={14} /> Cảnh báo vào ngoài giờ ({alerts.length})
                  </div>
                  <div style={{ maxHeight: 180, overflowY: 'auto' }}>
                    {alerts.map((a, i) => (
                      <div key={i} style={{ padding: '8px 16px', borderBottom: '1px solid #fff7ed', display: 'flex', alignItems: 'flex-start', gap: 10, background: '#fffbf5' }}>
                        <span style={{ marginTop: 2, color: '#ea580c', flexShrink: 0 }}><FiAlertTriangle size={14} /></span>
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ fontSize: 13, fontWeight: 600, color: '#9a3412' }}>
                            {a.employeeName || a.capturedName || 'N/V không rõ'}
                          </div>
                          <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 1 }}>
                            {a.deviceCode} · {fmtTime(a.scanTime)}
                          </div>
                        </div>
                        <span style={{ fontSize: 10, padding: '2px 7px', borderRadius: 10, fontWeight: 600, background: '#fed7aa', color: '#c2410c', flexShrink: 0 }}>
                          Ngoài giờ
                        </span>
                      </div>
                    ))}
                  </div>
                </>
              )}
              <div style={{ padding: '12px 16px', borderBottom: '1px solid #f1f5f9', fontWeight: 600, fontSize: 14, color: '#1f2937' }}>
                🔔 Lượt điểm danh gần nhất
              </div>
              {notifs.length === 0 ? (
                <div style={{ padding: '24px 16px', textAlign: 'center', color: '#9ca3af', fontSize: 13 }}>Không có dữ liệu</div>
              ) : notifs.map((n, i) => (
                <div key={i} style={{
                  padding: '9px 16px', borderBottom: '1px solid #f9fafb',
                  display: 'flex', alignItems: 'flex-start', gap: 10,
                  background: n.alertType === 'OUTSIDE_HOURS' ? '#fff7ed' : n.matched === false ? '#fff7f7' : n.comparisonMode === 'REMOTE_UNLOCK' ? '#f0fdf4' : 'white'
                }}>
                  <span style={{ marginTop: 2, flexShrink: 0, color: n.alertType === 'OUTSIDE_HOURS' ? '#ea580c' : n.comparisonMode === 'REMOTE_UNLOCK' ? '#7c3aed' : n.matched ? '#16a34a' : '#dc2626' }}>
                    {n.alertType === 'OUTSIDE_HOURS' ? <FiAlertTriangle size={15} /> : n.comparisonMode === 'REMOTE_UNLOCK' ? <FiUnlock size={15} /> : n.matched ? <FiCheckCircle size={15} /> : <FiXCircle size={15} />}
                  </span>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 500, color: '#1f2937', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {n.employeeName || n.capturedName || 'Không xác định'}
                    </div>
                    <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 1 }}>
                      {n.deviceCode} · {fmtTime(n.scanTime)}
                    </div>
                  </div>
                  <span style={{
                    fontSize: 10, padding: '2px 7px', borderRadius: 10, fontWeight: 600, flexShrink: 0,
                    background: n.alertType === 'OUTSIDE_HOURS' ? '#fed7aa' : n.comparisonMode === 'REMOTE_UNLOCK' ? '#ede9fe' : n.matched ? '#dcfce7' : '#fee2e2',
                    color: n.alertType === 'OUTSIDE_HOURS' ? '#c2410c' : n.comparisonMode === 'REMOTE_UNLOCK' ? '#7c3aed' : n.matched ? '#16a34a' : '#dc2626'
                  }}>
                    {n.alertType === 'OUTSIDE_HOURS' ? 'Ngoài giờ' : n.comparisonMode === 'REMOTE_UNLOCK' ? 'Mở cửa' : n.matched ? 'OK' : 'Từ chối'}
                  </span>
                </div>
              ))}
              <div style={{ padding: '9px 16px', textAlign: 'center', fontSize: 11, color: '#9ca3af', background: '#fafafa' }}>
                8 lượt gần nhất · tự động làm mới mỗi 30 giây
              </div>
            </div>
          )}
        </div>
        <div className="divider" />
        <div className="user-chip">
          <div className="user-avatar-sm">{initials}</div>
          <span>{user.fullName || user.username || 'Admin'}</span>
        </div>
        <button className="icon-btn" onClick={authService.logout} title="Đăng xuất">
          <FiLogOut size={18} />
        </button>
      </div>
    </header>
  );
}

export default TopBar;
