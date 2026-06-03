import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService, deviceService, permissionService, employeeService, attendanceService } from '../services/apiService';
import { FiPlus, FiEdit2, FiTrash2, FiUnlock, FiRefreshCw, FiWifi, FiWifiOff, FiEye, FiShield } from 'react-icons/fi';
import { fmtCccd } from '../utils/cccdUtils';

const EMPTY = { deviceCode: '', locationName: '', ipAddress: '' };

export default function DeviceManagement() {
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [unlocking, setUnlocking] = useState(null);
  const [msg, setMsg] = useState(null);
  const [search, setSearch] = useState('');
  const [deviceStats, setDeviceStats] = useState({});

  
  const [permModal, setPermModal] = useState(false);
  const [permDevice, setPermDevice] = useState(null);
  const [permissions, setPermissions] = useState([]);
  const [allEmployees, setAllEmployees] = useState([]);
  const [selEmployee, setSelEmployee] = useState('');
  const [permNewTime, setPermNewTime] = useState({ startTime: '', endTime: '', startDate: '', endDate: '' });
  const [editingPermId, setEditingPermId] = useState(null);
  const [editPermTime, setEditPermTime] = useState({ startTime: '', endTime: '' });
  const [syncing, setSyncing] = useState(null);
  const [unlockModal, setUnlockModal] = useState(null);
  const [unlockReason, setUnlockReason] = useState('');

  const user = authService.getUser();
  const role = (user.role || '').toLowerCase();
  const isAdmin = ['admin', 'super_admin'].includes(role);
  const canUnlock = ['admin', 'super_admin', 'operator'].includes(role);
  const navigate = useNavigate();

  const fetchDevices = useCallback(async () => {
    setLoading(true);
    try {
      const res = await deviceService.getAll();
      const list = res.data?.data?.content || res.data?.data || res.data || [];
      setDevices(Array.isArray(list) ? list : []);
    } catch { setDevices([]); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { fetchDevices(); }, [fetchDevices]);

  
  useEffect(() => {
    if (devices.length === 0) return;
    const fetchStats = async () => {
      const statsMap = {};
      const results = await Promise.allSettled(
        devices.map((d) => deviceService.getStats(d.deviceCode))
      );
      results.forEach((r, i) => {
        if (r.status === 'fulfilled') {
          statsMap[devices[i].deviceCode] = r.value.data?.data || r.value.data || {};
        }
      });
      setDeviceStats(statsMap);
    };
    fetchStats();
  }, [devices]);

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const openAdd = () => { setForm(EMPTY); setEditingId(null); setShowModal(true); };
  const openEdit = (d) => { setForm({ deviceCode: d.deviceCode, locationName: d.locationName || d.location || '', ipAddress: d.ipAddress || '' }); setEditingId(d.id); setShowModal(true); };

  const handleSave = async (e) => {
    e.preventDefault();
    try {
      if (editingId) await deviceService.update(editingId, form);
      else await deviceService.create(form);
      setShowModal(false);
      fetchDevices();
      toast('Lưu thiết bị thành công!');
    } catch (err) {
      toast(err?.response?.data?.message || err?.response?.data?.error || 'Lỗi lưu thiết bị', 'error');
    }
  };

  const handleDelete = async (d) => {
    if (!window.confirm(`Xoá thiết bị "${d.deviceCode}" tại "${d.locationName}"?`)) return;
    try { await deviceService.delete(d.id); fetchDevices(); toast('Đã xoá thiết bị'); }
    catch { toast('Lỗi xoá thiết bị', 'error'); }
  };

  const handleUnlock = (d) => {
    setUnlockModal(d);
    setUnlockReason('');
  };

  const confirmUnlock = async () => {
    const d = unlockModal;
    setUnlockModal(null);
    setUnlocking(d.deviceCode);
    try {
      await deviceService.remoteUnlock(d.deviceCode, unlockReason || 'Mở cửa từ xa');
      toast(`Đã gửi yêu cầu quét CCCD tới: ${d.locationName}`);
    } catch { toast('Gửi lệnh thất bại', 'error'); }
    finally { setUnlocking(null); }
  };

  const confirmInstantUnlock = async () => {
    const d = unlockModal;
    setUnlockModal(null);
    setUnlocking(d.deviceCode);
    try {
      await deviceService.instantUnlock(d.deviceCode, unlockReason || 'Admin mở cửa trực tiếp');
      toast(`Cửa đã mở: ${d.locationName}`);
    } catch { toast('Không thể mở cửa', 'error'); }
    finally { setUnlocking(null); }
  };

  const toast = (text, type = 'success') => {
    setMsg({ text, type });
    setTimeout(() => setMsg(null), 3000);
  };

  const handleSync = async (d) => {
    setSyncing(d.deviceCode);
    try {
      await deviceService.syncEmployees(d.deviceCode);
      toast(`Đã đồng bộ danh sách NV tới ${d.locationName || d.deviceCode}!`);
    } catch { toast('Gửi lệnh đồng bộ thất bại', 'error'); }
    finally { setSyncing(null); }
  };

  const openPermModal = async (d) => {
    setPermDevice(d);
    setSelEmployee('');
    setPermNewTime({ startTime: '', endTime: '', startDate: '', endDate: '' });
    setEditingPermId(null);
    setPermModal(true);
    try {
      const [permRes, empRes] = await Promise.all([
        permissionService.getByDevice(d.id),
        employeeService.getAll({ size: 1000 }),
      ]);
      setPermissions(permRes.data?.data || permRes.data || []);
      const empList = empRes.data?.data?.content || empRes.data?.data || empRes.data || [];
      setAllEmployees(Array.isArray(empList) ? empList : []);
    } catch { setPermissions([]); setAllEmployees([]); }
  };

  const handleAddPermission = async () => {
    if (!selEmployee) return;
    const payload = { employeeId: Number(selEmployee), deviceId: permDevice.id };
    if (permNewTime.startTime) payload.startTime = permNewTime.startTime;
    if (permNewTime.endTime) payload.endTime = permNewTime.endTime;
    if (permNewTime.startDate) payload.startDate = permNewTime.startDate;
    if (permNewTime.endDate) payload.endDate = permNewTime.endDate;
    try {
      await permissionService.create(payload);
      const res = await permissionService.getByDevice(permDevice.id);
      setPermissions(res.data?.data || res.data || []);
      setSelEmployee('');
      setPermNewTime({ startTime: '', endTime: '', startDate: '', endDate: '' });
      toast('Đã gán nhân viên vào cửa!');
    } catch (err) {
      toast(err?.response?.data?.message || err?.response?.data?.error || 'Lỗi gán quyền', 'error');
    }
  };

  const handleSavePermTime = async (permId) => {
    try {
      await permissionService.update(permId, {
        startTime: editPermTime.startTime || null,
        endTime: editPermTime.endTime || null,
      });
      const res = await permissionService.getByDevice(permDevice.id);
      setPermissions(res.data?.data || res.data || []);
      setEditingPermId(null);
      toast('Đã cập nhật khung giờ!');
    } catch { toast('Lỗi cập nhật khung giờ', 'error'); }
  };

  const handleRemovePermission = async (permId) => {
    try {
      await permissionService.delete(permId);
      setPermissions((prev) => prev.filter((p) => p.id !== permId));
      toast('Đã xoá quyền truy cập!');
    } catch { toast('Lỗi xoá quyền', 'error'); }
  };

  const filtered = devices.filter((d) =>
    `${d.deviceCode} ${d.locationName || d.location} ${d.ipAddress}`.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="page">
      <div className="page-header">
        <h1>Quản lý thiết bị ({devices.length})</h1>
        <div className="page-header-actions">
          <button className="btn btn-secondary" onClick={fetchDevices} disabled={loading}>
            <FiRefreshCw size={14} className={loading ? 'spin' : ''} /> Làm mới
          </button>
          {isAdmin && (
            <button className="btn btn-primary" onClick={openAdd}>
              <FiPlus size={14} /> Thêm thiết bị
            </button>
          )}
        </div>
      </div>

      {msg && (
        <div style={{ padding: '10px 14px', borderRadius: 8, marginBottom: 14, fontSize: 13, background: msg.type === 'error' ? '#fee2e2' : '#dcfce7', color: msg.type === 'error' ? '#dc2626' : '#16a34a' }}>
          {msg.text}
        </div>
      )}

      <div className="card">
        <div style={{ marginBottom: 14 }}>
          <input className="filters input" placeholder="Tìm kiếm thiết bị..." value={search} onChange={(e) => setSearch(e.target.value)} style={{ padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13, width: 260 }} />
        </div>

        <div className="table-wrapper">
          <table className="table">
            <thead>
              <tr>
                <th>Mã thiết bị</th>
                <th>Vị trí</th>
                <th>Phân quyền</th>
                <th>Đồng bộ</th>
                <th>Mở cửa</th>
                <th>Xem</th>
                <th>Sửa</th>
                <th>Xóa</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr><td colSpan={8} className="empty-state">Không có thiết bị nào</td></tr>
              ) : filtered.map((d) => {
                const st = deviceStats[d.deviceCode] || {};
                const total = st.todayAttempts ?? 0;
                const success = st.todaySuccess ?? 0;
                const denied = total - success;
                const rate = st.todaySuccessRate != null ? st.todaySuccessRate.toFixed(1) : (total > 0 ? ((success / total) * 100).toFixed(1) : '-');
                return (
                <tr key={d.id}>
                  <td style={{ fontFamily: 'monospace', fontWeight: 600 }}>{d.deviceCode}</td>
                  <td>
                    <div>{d.locationName || d.location || '-'}</div>
                    {d.ipAddress && <div style={{ fontSize: 11, color: '#9ca3af' }}>{d.ipAddress}</div>}
                  </td>
                  <td>
                    {isAdmin ? (
                      <button
                        className="btn btn-sm"
                        style={{ background: '#2563eb', color: 'white', border: 'none', borderRadius: 5, padding: '4px 8px', cursor: 'pointer', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}
                        onClick={() => openPermModal(d)}
                        title="Phân quyền nhân viên"
                      >
                        <FiShield size={12} /> Phân quyền
                      </button>
                    ) : (
                      <span style={{ fontSize: 13, color: '#6b7280' }}>Không</span>
                    )}
                  </td>
                  <td>
                    {(isAdmin || canUnlock) ? (
                      <button
                        className="btn btn-sm"
                        style={{ background: '#2563eb', color: 'white', border: 'none', borderRadius: 5, padding: '4px 8px', cursor: 'pointer', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}
                        onClick={() => handleSync(d)}
                        disabled={syncing === d.deviceCode}
                        title="Đồng bộ danh sách nhân viên"
                      >
                        <FiRefreshCw size={12} className={syncing === d.deviceCode ? 'spin' : ''} /> {syncing === d.deviceCode ? 'Đang...' : 'Đồng bộ'}
                      </button>
                    ) : (
                      <span style={{ fontSize: 13, color: '#6b7280' }}>Không</span>
                    )}
                  </td>
                  <td>
                    {canUnlock ? (
                      <button
                        className="btn btn-sm"
                        style={{ background: '#2563eb', color: 'white', border: 'none', borderRadius: 5, padding: '4px 8px', cursor: 'pointer', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}
                        onClick={() => handleUnlock(d)}
                        disabled={unlocking === d.deviceCode}
                        title="Mở cửa từ xa"
                      >
                        <FiUnlock size={12} /> {unlocking === d.deviceCode ? 'Đang...' : 'Mở cửa'}
                      </button>
                    ) : (
                      <span style={{ fontSize: 13, color: '#6b7280' }}>Không</span>
                    )}
                  </td>
                  <td>
                    <button
                      className="btn btn-sm"
                      style={{ background: '#2563eb', color: 'white', border: 'none', borderRadius: 5, padding: '4px 8px', cursor: 'pointer', fontSize: 12, display: 'flex', alignItems: 'center', gap: 4 }}
                      onClick={() => navigate(`/devices/${d.deviceCode}`)}
                      title="Xem chi tiết"
                    >
                      <FiEye size={12} /> Xem
                    </button>
                  </td>
                  <td>
                    {isAdmin ? (
                      <button className="btn-icon" onClick={() => openEdit(d)} title="Chỉnh sửa">
                        <FiEdit2 size={14} />
                      </button>
                    ) : <span style={{ fontSize: 13, color: '#6b7280' }}>-</span>}
                  </td>
                  <td>
                    {isAdmin ? (
                      <button className="btn-icon" onClick={() => handleDelete(d)} title="Xoá" style={{ color: '#dc2626' }}>
                        <FiTrash2 size={14} />
                      </button>
                    ) : <span style={{ fontSize: 13, color: '#6b7280' }}>-</span>}
                  </td>
                </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingId ? 'Chỉnh sửa thiết bị' : 'Thêm thiết bị mới'}</h3>
              <button className="modal-close" onClick={() => setShowModal(false)}>×</button>
            </div>
            <form onSubmit={handleSave}>
              <div className="modal-body">
                <div className="form-group">
                  <label>Mã thiết bị <span style={{ color: 'red' }}>*</span></label>
                  <input placeholder="VD: GATE_A, DEV001" value={form.deviceCode} onChange={(e) => set('deviceCode', e.target.value)} required disabled={!!editingId} />
                  <div className="form-error" style={{ display: 'none' }}></div>
                </div>
                <div className="form-group">
                  <label>Tên vị trí <span style={{ color: 'red' }}>*</span></label>
                  <input placeholder="VD: Cổng A, Cửa chính tầng 1" value={form.locationName} onChange={(e) => set('locationName', e.target.value)} required />
                </div>
                <div className="form-group">
                  <label>Địa chỉ IP <span style={{ color: '#9ca3af', fontWeight: 400 }}>(tuỳ chọn)</span></label>
                  <input placeholder="VD: 192.168.1.10" value={form.ipAddress} onChange={(e) => set('ipAddress', e.target.value)} />
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Huỷ</button>
                <button type="submit" className="btn btn-primary">{editingId ? 'Cập nhật' : 'Thêm mới'}</button>
              </div>
            </form>
          </div>
        </div>
      )}
      {}
      {permModal && permDevice && (
        <div className="modal-overlay" onClick={() => setPermModal(false)}>
          <div className="modal" style={{ minWidth: 480 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3><FiShield size={16} style={{ marginRight: 6 }} />Phân quyền: {permDevice.locationName || permDevice.deviceCode}</h3>
              <button className="modal-close" onClick={() => setPermModal(false)}>×</button>
            </div>
            <div className="modal-body">
              {}
              <div style={{ marginBottom: 16 }}>
                <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
                  <select
                    value={selEmployee}
                    onChange={(e) => setSelEmployee(e.target.value)}
                    style={{ flex: 1, padding: '8px 10px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13 }}
                  >
                    <option value="">-- Chọn nhân viên --</option>
                    {allEmployees.map((emp) => (
                      <option key={emp.id || emp.cccd} value={emp.id}>
                        {emp.fullName} ({fmtCccd(emp.cccd)})
                      </option>
                    ))}
                  </select>
                  <button className="btn btn-primary" onClick={handleAddPermission} disabled={!selEmployee}>
                    <FiPlus size={14} /> Thêm
                  </button>
                </div>
                {}
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', background: '#f8fafc', padding: '8px 10px', borderRadius: 6, border: '1px solid #e5e7eb' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1, minWidth: 110 }}>
                    <label style={{ fontSize: 11, color: '#6b7280' }}>Giờ vào từ</label>
                    <input type="time" value={permNewTime.startTime} onChange={(e) => setPermNewTime(t => ({ ...t, startTime: e.target.value }))} style={{ padding: '4px 6px', border: '1px solid #d1d5db', borderRadius: 5, fontSize: 13 }} />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1, minWidth: 110 }}>
                    <label style={{ fontSize: 11, color: '#6b7280' }}>Giờ vào đến</label>
                    <input type="time" value={permNewTime.endTime} onChange={(e) => setPermNewTime(t => ({ ...t, endTime: e.target.value }))} style={{ padding: '4px 6px', border: '1px solid #d1d5db', borderRadius: 5, fontSize: 13 }} />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1, minWidth: 120 }}>
                    <label style={{ fontSize: 11, color: '#6b7280' }}>Ngày bắt đầu</label>
                    <input type="date" value={permNewTime.startDate} onChange={(e) => setPermNewTime(t => ({ ...t, startDate: e.target.value }))} style={{ padding: '4px 6px', border: '1px solid #d1d5db', borderRadius: 5, fontSize: 13 }} />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1, minWidth: 120 }}>
                    <label style={{ fontSize: 11, color: '#6b7280' }}>Ngày kết thúc</label>
                    <input type="date" value={permNewTime.endDate} onChange={(e) => setPermNewTime(t => ({ ...t, endDate: e.target.value }))} style={{ padding: '4px 6px', border: '1px solid #d1d5db', borderRadius: 5, fontSize: 13 }} />
                  </div>
                </div>
                <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 4 }}>Để trống = không giới hạn giờ/ngày</div>
              </div>

              {}
              <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 8 }}>
                Nhân viên được phép vào cửa này ({permissions.length}):
              </div>
              {permissions.length === 0 ? (
                <div style={{ textAlign: 'center', color: '#9ca3af', padding: '20px 0', fontSize: 13 }}>
                  Chưa có nhân viên nào được phân quyền
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 320, overflowY: 'auto' }}>
                  {permissions.map((p) => (
                    <div key={p.id} style={{ background: '#f8fafc', borderRadius: 6, border: '1px solid #e5e7eb' }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 12px' }}>
                        <div>
                          <span style={{ fontWeight: 600 }}>{p.employeeName || p.employee?.fullName || '?'}</span>
                          <span style={{ color: '#9ca3af', fontSize: 12, marginLeft: 8 }}>
                            {fmtCccd(p.cccd || p.employeeCccd || p.employee?.cccd)}
                          </span>
                          {p.startTime && p.endTime && (
                            <span style={{ marginLeft: 10, fontSize: 11, background: '#dbeafe', color: '#1d4ed8', borderRadius: 4, padding: '1px 6px' }}>
                              {p.startTime} – {p.endTime}
                            </span>
                          )}
                        </div>
                        <div style={{ display: 'flex', gap: 4 }}>
                          <button
                            onClick={() => { setEditingPermId(editingPermId === p.id ? null : p.id); setEditPermTime({ startTime: p.startTime || '', endTime: p.endTime || '' }); }}
                            style={{ background: 'none', border: 'none', color: '#0ea5e9', cursor: 'pointer', padding: 4 }}
                            title="Sửa khung giờ"
                          >
                            <FiEdit2 size={13} />
                          </button>
                          <button
                            onClick={() => handleRemovePermission(p.id)}
                            style={{ background: 'none', border: 'none', color: '#dc2626', cursor: 'pointer', padding: 4 }}
                            title="Xoá quyền"
                          >
                            <FiTrash2 size={14} />
                          </button>
                        </div>
                      </div>
                      {editingPermId === p.id && (
                        <div style={{ padding: '8px 12px', borderTop: '1px solid #e5e7eb', display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap', background: '#eff6ff' }}>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                            <label style={{ fontSize: 11, color: '#6b7280' }}>Giờ vào từ</label>
                            <input type="time" value={editPermTime.startTime} onChange={(e) => setEditPermTime(t => ({ ...t, startTime: e.target.value }))} style={{ padding: '3px 6px', border: '1px solid #bfdbfe', borderRadius: 5, fontSize: 13, width: 100 }} />
                          </div>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                            <label style={{ fontSize: 11, color: '#6b7280' }}>Giờ vào đến</label>
                            <input type="time" value={editPermTime.endTime} onChange={(e) => setEditPermTime(t => ({ ...t, endTime: e.target.value }))} style={{ padding: '3px 6px', border: '1px solid #bfdbfe', borderRadius: 5, fontSize: 13, width: 100 }} />
                          </div>
                          <button className="btn btn-primary" style={{ fontSize: 12, padding: '4px 10px' }} onClick={() => handleSavePermTime(p.id)}>Lưu</button>
                          <button className="btn btn-secondary" style={{ fontSize: 12, padding: '4px 10px' }} onClick={() => setEditingPermId(null)}>Hủy</button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setPermModal(false)}>Đóng</button>
            </div>
          </div>
        </div>
      )}
      {}
      {unlockModal && (
        <div className="modal-overlay" onClick={() => setUnlockModal(null)}>
          <div className="modal" style={{ maxWidth: 440 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3><FiUnlock size={16} style={{ marginRight: 6 }} />Mở cửa: {unlockModal.locationName}</h3>
              <button className="modal-close" onClick={() => setUnlockModal(null)}>×</button>
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
                  <div style={{ fontSize: 12, color: '#6b7280', marginTop: 4 }}>Mở cửa tức thì không cần quét gì, cửa mở ngay lập tức</div>
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setUnlockModal(null)}>Huỷ</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

