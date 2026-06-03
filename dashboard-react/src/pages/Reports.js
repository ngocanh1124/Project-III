import React, { useState, useCallback } from 'react';
import { authService, attendanceService, employeeService, exportToExcel } from '../services/apiService';
import { FiDownload, FiRefreshCw } from 'react-icons/fi';
import { fmtCccd } from '../utils/cccdUtils';

const today = () => new Date().toISOString().slice(0, 10);
const fromDefault = () => { const d = new Date(); d.setDate(d.getDate() - 30); return d.toISOString().slice(0, 10); };

export default function Reports() {
  const [dateFrom, setDateFrom] = useState(fromDefault);
  const [dateTo, setDateTo] = useState(today);
  const [preview, setPreview] = useState(null);
  const [selectedDevice, setSelectedDevice] = useState('');
  const [loadingPreview, setLoadingPreview] = useState(false);
  const [exportingAttendance, setExportingAttendance] = useState(false);
  const [exportingEmployee, setExportingEmployee] = useState(false);
  const [msg, setMsg] = useState(null);
  const user = authService.getUser();
  const role = (user.role || '').toLowerCase();
  const isViewer = role === 'viewer';

  const toast = (text, type = 'success') => { setMsg({ text, type }); setTimeout(() => setMsg(null), 3000); };

  const loadPreview = useCallback(async () => {
    setLoadingPreview(true);
    try {
      const res = await attendanceService.getHistory({ dateFrom, dateTo, page: 0, size: 1000 });
      const d = res.data?.data || res.data || {};
      const rows = d.content || [];
      const success = rows.filter((r) => r.accessGranted ?? r.matched).length;
      const denied = rows.length - success;
      const rate = rows.length > 0 ? ((success / rows.length) * 100).toFixed(1) : 0;
      const devices = {};
      rows.forEach((r) => { const code = r.deviceCode || 'Không rõ'; devices[code] = (devices[code] || 0) + 1; });
      const mode2Count = rows.filter((r) => r.comparisonMode && r.comparisonMode !== 'APP_OFFLINE').length;
      const previewData = { total: rows.length, success, denied, rate, devices, mode2Count, rows };
      setPreview(previewData);
      setSelectedDevice((prev) => prev || Object.keys(devices)[0] || '');
    } catch { toast('Không tải được dữ liệu xem trước', 'error'); }
    finally { setLoadingPreview(false); }
  }, [dateFrom, dateTo]);

  const handleExportAttendance = async () => {
    if (!preview) { toast('Hãy xem trước trước khi xuất', 'error'); return; }
    if (!selectedDevice) { toast('Chọn thiết bị để xuất báo cáo', 'error'); return; }
    setExportingAttendance(true);
    try {
      const rows = preview.rows
        .filter((r) => (r.accessGranted ?? r.matched) && r.deviceCode === selectedDevice)
        .map((r) => ({
        'Số CCCD': fmtCccd(r.cccd),
        'Họ tên': r.fullName || r.employeeName || r.capturedName || '',
        'Thiết bị': r.deviceCode || '',
        'Thời gian': r.scanTime ? new Date(r.scanTime).toLocaleString('vi-VN') : '',
        'Độ khớp (%)': r.score != null ? (r.score * 100).toFixed(1) : r.matchScore != null ? (r.matchScore * 100).toFixed(1) : '',
        'Phương thức': r.comparisonMode && r.comparisonMode !== 'APP_OFFLINE' ? 'Server' : 'App',
        'Kết quả': 'Vào',
      }));
      exportToExcel(rows, `diem-danh-${selectedDevice}-${dateFrom}-${dateTo}.xlsx`, selectedDevice);
      toast(`Xuất ${rows.length} lượt vào thiết bị ${selectedDevice} thành công!`);
    } catch { toast('Lỗi xuất file', 'error'); }
    finally { setExportingAttendance(false); }
  };

  const handleExportEmployee = async () => {
    setExportingEmployee(true);
    try {
      const res = await employeeService.getAll({ page: 0, size: 9999 });
      const d = res.data?.data || res.data || {};
      const emps = d.content || (Array.isArray(d) ? d : []);
      const rows = emps.map((e) => ({
        'Số CCCD': fmtCccd(e.cccd), 'Họ tên': e.fullName, 'Ngày sinh': e.birthday || '',
        'Giới tính': e.gender || '', 'Phòng ban': e.department || '',
        'Chức vụ': e.position || '', 'Email': e.email || '', 'Điện thoại': e.phone || '',
        'Trạng thái': e.isActive !== false ? 'Hoạt động' : 'Ngừng',
      }));
      exportToExcel(rows, `danh-sach-nhan-vien-${today()}.xlsx`, 'Nhân viên');
      toast('Xuất danh sách nhân viên thành công!');
    } catch { toast('Lỗi xuất danh sách nhân viên', 'error'); }
    finally { setExportingEmployee(false); }
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Báo cáo & Xuất dữ liệu</h1>
      </div>

      {msg && (
        <div style={{ padding: '10px 14px', borderRadius: 8, marginBottom: 14, fontSize: 13, background: msg.type === 'error' ? '#fee2e2' : '#dcfce7', color: msg.type === 'error' ? '#dc2626' : '#16a34a' }}>
          {msg.text}
        </div>
      )}

      <div className="card">
        <h3 style={{ margin: '0 0 16px', fontSize: 15, fontWeight: 600 }}>Báo cáo điểm danh</h3>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 16 }}>
          <div>
            <label style={{ fontSize: 12, color: '#6b7280', display: 'block', marginBottom: 4 }}>Từ ngày</label>
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} style={{ padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13 }} />
          </div>
          <div>
            <label style={{ fontSize: 12, color: '#6b7280', display: 'block', marginBottom: 4 }}>Đến ngày</label>
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} style={{ padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13 }} />
          </div>
          <button className="btn btn-primary btn-sm" onClick={loadPreview} disabled={loadingPreview}>
            <FiRefreshCw size={13} className={loadingPreview ? 'spin' : ''} /> {loadingPreview ? 'Đang tải...' : 'Xem trước'}
          </button>
          <button className="btn btn-success btn-sm" onClick={handleExportAttendance} disabled={exportingAttendance || !preview || !selectedDevice}>
            <FiDownload size={13} /> {exportingAttendance ? 'Đang xuất...' : 'Xuất Excel theo thiết bị'}
          </button>
        </div>

        {preview && (
          <>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12, flexWrap: 'wrap' }}>
              <label style={{ fontSize: 13, color: '#374151', fontWeight: 600 }}>Chọn thiết bị</label>
              <select value={selectedDevice} onChange={(e) => setSelectedDevice(e.target.value)} style={{ padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13 }}>
                {Object.keys(preview.devices).map((code) => (
                  <option key={code} value={code}>{code}</option>
                ))}
              </select>
            </div>
            <div className="stats-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', marginBottom: 20 }}>
              {[
                { label: 'Tổng lượt', value: preview.total },
                { label: 'Thành công', value: preview.success, color: '' },
                { label: 'Từ chối', value: preview.denied, color: '' },
                { label: 'Tỉ lệ thành công', value: `${preview.rate}%`, color: '' },
              ].map((c) => (
                <div key={c.label} className={`stat-card ${c.color || ''}`}>
                  <div className="value">{c.value}</div>
                  <div className="label">{c.label}</div>
                </div>
              ))}
            </div>
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 10 }}>Lượt theo thiết bị</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {Object.entries(preview.devices).sort((a, b) => b[1] - a[1]).map(([code, count]) => (
                  <div key={code} style={{ background: '#f1f5f9', borderRadius: 8, padding: '8px 14px', fontSize: 13, display: 'flex', gap: 8, alignItems: 'center' }}>
                    <code style={{ fontWeight: 700, color: '#374151', fontSize: 12 }}>{code}</code>
                    <span style={{ background: '#f3f4f6', color: '#374151', borderRadius: 12, padding: '1px 8px', fontSize: 12, fontWeight: 700 }}>{count}</span>
                  </div>
                ))}
              </div>
            </div>
          </>
        )}
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h3 style={{ margin: '0 0 4px', fontSize: 15, fontWeight: 600 }}>Danh sách nhân viên</h3>
            <p style={{ margin: 0, fontSize: 13, color: '#6b7280' }}>Xuất toàn bộ danh sách nhân viên ra Excel</p>
          </div>
          <button className="btn btn-success" onClick={handleExportEmployee} disabled={exportingEmployee}>
            <FiDownload size={14} /> {exportingEmployee ? 'Đang xuất...' : 'Xuất danh sách nhân viên'}
          </button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 16 }}>
        {[
          { title: 'Báo cáo điểm danh', desc: 'Xuất toàn bộ lịch sử điểm danh theo khoảng thời gian với đầy đủ thông tin ảnh selfie và độ khớp khuôn mặt.', icon: '📋' },
          { title: 'Phân tích theo thiết bị', desc: 'Xem thống kê lượt ra vào theo từng thiết bị, phân tích tỉ lệ thành công và từ chối.', icon: '📊' },
          { title: 'Trường hợp đặc biệt', desc: 'Báo cáo các lượt chấm công có độ khớp thấp (43–57%) cần xem xét thủ công.', icon: '⚠️' },
        ].map((c) => (
          <div key={c.title} className="card" style={{ display: 'flex', gap: 14 }}>
            <span style={{ fontSize: 30 }}>{c.icon}</span>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: '#1f2937', marginBottom: 4 }}>{c.title}</div>
              <div style={{ fontSize: 12.5, color: '#6b7280', lineHeight: 1.5 }}>{c.desc}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

