import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService, deviceService, permissionService, employeeService } from '../services/apiService';
import { FiPlus, FiEdit2, FiTrash2, FiUnlock, FiRefreshCw, FiEye, FiShield, FiArrowRight, FiArrowLeft } from 'react-icons/fi';
import { fmtCccd } from '../utils/cccdUtils';

const EMPTY = { deviceCode: '', locationName: '', ipAddress: '', direction: 'IN' };

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
  const [correspondingDevice, setCorrespondingDevice] = useState(null); // ✅ Device cặp (IN/OUT)
  const [permissions, setPermissions] = useState([]);
  const [correspondingPermissions, setCorrespondingPermissions] = useState([]); // ✅ Permissions của device cặp
  const [allEmployees, setAllEmployees] = useState([]);
  const [selEmployee, setSelEmployee] = useState('');
  const [permNewTime, setPermNewTime] = useState({ startTime: '', endTime: '', startDate: '', endDate: '' });
  const [editingPermId, setEditingPermId] = useState(null);
  const [editPermTime, setEditPermTime] = useState({ startTime: '', endTime: '' });
  const [syncing, setSyncing] = useState(null);
  const [syncingPerm, setSyncingPerm] = useState(null); // ✅ Tracking copy permissions
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
      const res = await deviceService.getAll({ t: Date.now() }); // Force fresh data from API
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
  const openEdit = (d) => { setForm({ deviceCode: d.deviceCode, locationName: d.locationName || d.location || '', ipAddress: d.ipAddress || '', direction: d.direction || 'IN' }); setEditingId(d.id); setShowModal(true); };

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
    
    // ✅ Tìm device cặp (IN/OUT cùng location)
    // Strip _IN/_OUT suffix khi so sánh
    const normalizedLocation = (d.locationName || '').replace(/_IN$|_OUT$/, '');
    console.log('🔍 Looking for paired device:');
    console.log('  Current device:', d.deviceCode, 'location:', d.locationName, 'normalized:', normalizedLocation, 'direction:', d.direction);
    console.log('  Available devices:', devices.map(dev => ({ 
      code: dev.deviceCode, 
      loc: dev.locationName, 
      normalized: (dev.locationName || '').replace(/_IN$|_OUT$/, ''),
      dir: dev.direction 
    })));
    
    const correspondingDev = devices.find(dev => {
      const devNormalizedLocation = (dev.locationName || '').replace(/_IN$|_OUT$/, '');
      return devNormalizedLocation === normalizedLocation && 
             dev.direction !== d.direction && 
             dev.id !== d.id;
    });
    console.log('  Found paired device:', correspondingDev);
    setCorrespondingDevice(correspondingDev || null);
    
    try {
      const promises = [
        permissionService.getByDevice(d.id),
        employeeService.getAll({ size: 1000 }),
      ];
      
      // Load permissions của device cặp nếu có
      if (correspondingDev) {
        promises.push(permissionService.getByDevice(correspondingDev.id));
      }
      
      const results = await Promise.all(promises);
      setPermissions(results[0].data?.data || results[0].data || []);
      const empList = results[1].data?.data?.content || results[1].data?.data || results[1].data || [];
      setAllEmployees(Array.isArray(empList) ? empList : []);
      
      if (correspondingDev && results[2]) {
        setCorrespondingPermissions(results[2].data?.data || results[2].data || []);
      } else {
        setCorrespondingPermissions([]);
      }
    } catch { 
      setPermissions([]); 
      setAllEmployees([]);
      setCorrespondingPermissions([]);
    }
  };

  const handleAddPermission = async () => {
    if (!selEmployee) return;
    const payload = { employeeId: Number(selEmployee), deviceId: permDevice.id };
    if (permNewTime.startTime) payload.startTime = permNewTime.startTime;
    if (permNewTime.endTime) payload.endTime = permNewTime.endTime;
    if (permNewTime.startDate) payload.startDate = permNewTime.startDate;
    if (permNewTime.endDate) payload.endDate = permNewTime.endDate;
    try {
      const createRes = await permissionService.create(payload);
      const res = await permissionService.getByDevice(permDevice.id);
      setPermissions(res.data?.data || res.data || []);
      
      // ✅ Nếu auto-create device OUT (corresponding), hiển thị permissions của OUT
      const correspondingDevices = createRes.data?.data?.corresponding;
      if (correspondingDevices && correspondingDevices.length > 0) {
        const outDevice = correspondingDevices[0]; // Device OUT được tạo
        setTimeout(async () => {
          // Load device OUT vừa được tạo
          const outDeviceRes = await permissionService.getByDevice(outDevice.id);
          setPermissions(outDeviceRes.data?.data || outDeviceRes.data || []);
          setPermDevice({ ...permDevice, ...outDevice }); // Switch view sang OUT device
          toast(`✅ Quyền IN tạo xong! Tự động tạo quyền OUT: ${outDevice.deviceCode}`);
        }, 500);
      } else {
        toast('✅ Đã gán nhân viên vào cửa!');
      }
      
      setSelEmployee('');
      setPermNewTime({ startTime: '', endTime: '', startDate: '', endDate: '' });
      // ✅ Reload device list để cập nhật
      await fetchDevices();
      toast('✅ Đã gán nhân viên! Quyền OUT cũng được tạo tự động.');
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

  const syncAllDevices = async () => {
    try {
      setSyncing('all');
      const res = await permissionService.syncAllDevices();
      const { syncedCount, totalDevices } = res.data?.data || {};
      toast(`✅ Đã đồng bộ ${syncedCount}/${totalDevices} thiết bị thành công!`);
    } catch (err) {
      toast(err?.response?.data?.message || 'Lỗi đồng bộ thiết bị', 'error');
    } finally {
      setSyncing(null);
    }
  };

  // ✅ Copy permissions từ device này sang device khác
  const syncPermissionsBetweenDevices = async (direction) => {
    if (!permDevice || !correspondingDevice) return;
    
    setSyncingPerm(direction);
    try {
      const sourceDevice = direction === 'IN_TO_OUT' ? permDevice : correspondingDevice;
      const targetDevice = direction === 'IN_TO_OUT' ? correspondingDevice : permDevice;
      const sourcePerms = direction === 'IN_TO_OUT' ? permissions : correspondingPermissions;

      if (sourcePerms.length === 0) {
        toast(`Device ${sourceDevice.deviceCode} chưa có phân quyền!`, 'error');
        return;
      }

      // Copy từng permission
      for (const perm of sourcePerms) {
        const existingPerm = (direction === 'IN_TO_OUT' ? correspondingPermissions : permissions)
          .find(p => p.employeeId === perm.employeeId || 
                      (p.employee?.id === perm.employee?.id) ||
                      (p.cccd === perm.cccd));
        
        if (!existingPerm) {
          const payload = {
            employeeId: perm.employeeId || perm.employee?.id,
            deviceId: targetDevice.id,
            startTime: perm.startTime || '',
            endTime: perm.endTime || '',
            startDate: perm.startDate || '',
            endDate: perm.endDate || '',
          };
          await permissionService.create(payload);
        }
      }

      // Reload danh sách
      const [newPerms, newCorrespondingPerms] = await Promise.all([
        permissionService.getByDevice(permDevice.id),
        correspondingDevice ? permissionService.getByDevice(correspondingDevice.id) : Promise.resolve({ data: [] })
      ]);
      
      setPermissions(newPerms.data?.data || newPerms.data || []);
      setCorrespondingPermissions(newCorrespondingPerms.data?.data || newCorrespondingPerms.data || []);
      toast(`✅ Đồng bộ từ ${sourceDevice.deviceCode} sang ${targetDevice.deviceCode} thành công!`);
    } catch (err) {
      toast(err?.response?.data?.message || 'Lỗi đồng bộ quyền', 'error');
    } finally {
      setSyncingPerm(null);
    }
  };

  // ✅ Hợp nhất danh sách (lấy union của cả 2)
  const mergePermissionsBoth = async () => {
    if (!permDevice || !correspondingDevice) return;
    
    setSyncingPerm('merge');
    try {
      const allPerms = [...permissions, ...correspondingPermissions];
      const uniqueEmps = new Set();
      const toAdd = [];

      for (const perm of allPerms) {
        const empId = perm.employeeId || perm.employee?.id;
        if (!uniqueEmps.has(empId)) {
          uniqueEmps.add(empId);
          toAdd.push(perm);
        }
      }

      // Add missing perms to IN device
      for (const perm of toAdd) {
        const exists = permissions.find(p => (p.employeeId || p.employee?.id) === (perm.employeeId || perm.employee?.id));
        if (!exists) {
          await permissionService.create({
            employeeId: perm.employeeId || perm.employee?.id,
            deviceId: permDevice.id,
            startTime: perm.startTime || '',
            endTime: perm.endTime || '',
            startDate: perm.startDate || '',
            endDate: perm.endDate || '',
          });
        }
      }

      // Add missing perms to OUT device
      for (const perm of toAdd) {
        const exists = correspondingPermissions.find(p => (p.employeeId || p.employee?.id) === (perm.employeeId || perm.employee?.id));
        if (!exists) {
          await permissionService.create({
            employeeId: perm.employeeId || perm.employee?.id,
            deviceId: correspondingDevice.id,
            startTime: perm.startTime || '',
            endTime: perm.endTime || '',
            startDate: perm.startDate || '',
            endDate: perm.endDate || '',
          });
        }
      }

      // Reload
      const [newPerms, newCorrespondingPerms] = await Promise.all([
        permissionService.getByDevice(permDevice.id),
        permissionService.getByDevice(correspondingDevice.id)
      ]);
      
      setPermissions(newPerms.data?.data || newPerms.data || []);
      setCorrespondingPermissions(newCorrespondingPerms.data?.data || newCorrespondingPerms.data || []);
      toast(`✅ Hợp nhất danh sách ${permDevice.deviceCode} & ${correspondingDevice.deviceCode} thành công!`);
    } catch (err) {
      toast(err?.response?.data?.message || 'Lỗi hợp nhất quyền', 'error');
    } finally {
      setSyncingPerm(null);
    }
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
          <button className="btn btn-secondary" onClick={syncAllDevices} disabled={syncing === 'all'}>
            <FiRefreshCw size={14} className={syncing === 'all' ? 'spin' : ''} /> Đồng bộ tất cả
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
                <th>Chiều di chuyển</th>
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
                <tr><td colSpan={9} className="empty-state">Không có thiết bị nào</td></tr>
              ) : filtered.map((d) => {
                const st = deviceStats[d.deviceCode] || {};
                const total = st.todayAttempts ?? 0;
                const success = st.todaySuccess ?? 0;
                return (
                <tr key={d.id}>
                  <td style={{ fontFamily: 'monospace', fontWeight: 600 }}>{d.deviceCode}</td>
                  <td>
                    <div>{d.locationName || d.location || '-'}</div>
                    {d.ipAddress && <div style={{ fontSize: 11, color: '#9ca3af' }}>{d.ipAddress}</div>}
                  </td>
                  <td>
                    <span className="badge" style={{ 
                      background: d.direction === 'OUT' ? '#fecaca' : '#dbeafe', 
                      color: d.direction === 'OUT' ? '#dc2626' : '#0284c7', 
                      fontSize: 12, 
                      fontWeight: 700,
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 6,
                      padding: '4px 8px'
                    }}>
                      {d.direction === 'OUT' ? <FiArrowLeft size={14} /> : <FiArrowRight size={14} />}
                      {d.direction === 'OUT' ? 'Ra' : 'Vào'}
                    </span>
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
                  <label>Chiều di chuyển <span style={{ color: 'red' }}>*</span> {!isAdmin && <span style={{ fontSize: 11, color: '#9ca3af' }}>(chỉ admin/super_admin)</span>}</label>
                  <select value={form.direction || 'IN'} onChange={(e) => set('direction', e.target.value)} disabled={!isAdmin} style={{ padding: '8px 10px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13, width: '100%', backgroundColor: !isAdmin ? '#f3f4f6' : 'white', cursor: !isAdmin ? 'not-allowed' : 'pointer' }}>
                    <option value="IN">Vào (IN)</option>
                    <option value="OUT">Ra (OUT)</option>
                  </select>
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
          <div className="modal" style={{ minWidth: 900, maxWidth: 1200 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <h3 style={{ margin: 0 }}>
                <FiShield size={16} style={{ marginRight: 6 }} />
                Phân quyền: {permDevice.locationName || permDevice.deviceCode}
              </h3>
              {/* ✅ Nút hợp nhất */}
              {correspondingDevice && (
                <button
                  className="btn btn-primary"
                  onClick={mergePermissionsBoth}
                  disabled={syncingPerm}
                  style={{ fontSize: 12, padding: '6px 12px', marginRight: 12 }}
                  title="Hợp nhất danh sách với device cặp"
                >
                  🔄 Hợp nhất
                </button>
              )}
              <button className="modal-close" onClick={() => setPermModal(false)}>×</button>
            </div>
            <div className="modal-body">
              {/* Single column view */}
              <div style={{ background: '#f0fdf4', padding: '10px 12px', borderRadius: 6, marginBottom: 12, border: '1px solid #86efac' }}>
                <div style={{ fontWeight: 600, fontSize: 13, color: '#15803d' }}>
                  📍 {permDevice.deviceCode} ({permDevice.direction})
                </div>
              </div>
              
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
                  <button className="btn btn-primary" onClick={handleAddPermission} disabled={!selEmployee} style={{ fontSize: 12, padding: '6px 12px' }}>
                    <FiPlus size={14} /> Thêm
                  </button>
                </div>
                
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', background: '#f8fafc', padding: '8px 10px', borderRadius: 6, border: '1px solid #e5e7eb' }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1, minWidth: 100 }}>
                    <label style={{ fontSize: 11, color: '#6b7280' }}>Từ giờ</label>
                    <input type="time" value={permNewTime.startTime} onChange={(e) => setPermNewTime(t => ({ ...t, startTime: e.target.value }))} style={{ padding: '4px 6px', border: '1px solid #d1d5db', borderRadius: 5, fontSize: 13 }} />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1, minWidth: 100 }}>
                    <label style={{ fontSize: 11, color: '#6b7280' }}>Đến giờ</label>
                    <input type="time" value={permNewTime.endTime} onChange={(e) => setPermNewTime(t => ({ ...t, endTime: e.target.value }))} style={{ padding: '4px 6px', border: '1px solid #d1d5db', borderRadius: 5, fontSize: 13 }} />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1, minWidth: 110 }}>
                    <label style={{ fontSize: 11, color: '#6b7280' }}>Từ ngày</label>
                    <input type="date" value={permNewTime.startDate} onChange={(e) => setPermNewTime(t => ({ ...t, startDate: e.target.value }))} style={{ padding: '4px 6px', border: '1px solid #d1d5db', borderRadius: 5, fontSize: 13 }} />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2, flex: 1, minWidth: 110 }}>
                    <label style={{ fontSize: 11, color: '#6b7280' }}>Đến ngày</label>
                    <input type="date" value={permNewTime.endDate} onChange={(e) => setPermNewTime(t => ({ ...t, endDate: e.target.value }))} style={{ padding: '4px 6px', border: '1px solid #d1d5db', borderRadius: 5, fontSize: 13 }} />
                  </div>
                </div>
                <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 4 }}>Để trống = không giới hạn</div>
              </div>

              <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 8, fontWeight: 600 }}>
                Danh sách phân quyền ({permissions.length}):
              </div>
              {permissions.length === 0 ? (
                <div style={{ textAlign: 'center', color: '#9ca3af', padding: '20px 0', fontSize: 13 }}>
                  Chưa có nhân viên
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 380, overflowY: 'auto' }}>
                  {permissions.map((p) => (
                    <div key={p.id} style={{ background: '#f8fafc', borderRadius: 6, border: '1px solid #e5e7eb', padding: '8px 10px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                        <div style={{ flex: 1 }}>
                          <span style={{ fontWeight: 600, fontSize: 13 }}>{p.employeeName || p.employee?.fullName || '?'}</span>
                          <span style={{ color: '#9ca3af', fontSize: 11, marginLeft: 6 }}>
                            {fmtCccd(p.cccd || p.employeeCccd || p.employee?.cccd)}
                          </span>
                          {p.startTime && p.endTime && (
                            <span style={{ marginLeft: 6, fontSize: 10, background: '#dbeafe', color: '#1d4ed8', borderRadius: 3, padding: '1px 5px' }}>
                              {p.startTime}–{p.endTime}
                            </span>
                          )}
                        </div>
                        <div style={{ display: 'flex', gap: 3 }}>
                          <button
                            onClick={() => { setEditingPermId(editingPermId === p.id ? null : p.id); setEditPermTime({ startTime: p.startTime || '', endTime: p.endTime || '' }); }}
                            style={{ background: 'none', border: 'none', color: '#0ea5e9', cursor: 'pointer', padding: 4 }}
                            title="Sửa"
                          >
                            <FiEdit2 size={13} />
                          </button>
                          <button
                            onClick={() => handleRemovePermission(p.id)}
                            style={{ background: 'none', border: 'none', color: '#dc2626', cursor: 'pointer', padding: 4 }}
                            title="Xoá"
                          >
                            <FiTrash2 size={13} />
                          </button>
                        </div>
                      </div>
                      {editingPermId === p.id && (
                        <div style={{ padding: '8px 0', borderTop: '1px solid #e5e7eb', marginTop: 6, display: 'flex', gap: 6, alignItems: 'flex-end', flexWrap: 'wrap' }}>
                          <input type="time" value={editPermTime.startTime} onChange={(e) => setEditPermTime(t => ({ ...t, startTime: e.target.value }))} style={{ padding: '3px 6px', border: '1px solid #bfdbfe', borderRadius: 5, fontSize: 12, width: 80 }} />
                          <input type="time" value={editPermTime.endTime} onChange={(e) => setEditPermTime(t => ({ ...t, endTime: e.target.value }))} style={{ padding: '3px 6px', border: '1px solid #bfdbfe', borderRadius: 5, fontSize: 12, width: 80 }} />
                          <button className="btn btn-primary" style={{ fontSize: 11, padding: '3px 8px' }} onClick={() => handleSavePermTime(p.id)}>Lưu</button>
                          <button className="btn btn-secondary" style={{ fontSize: 11, padding: '3px 8px' }} onClick={() => setEditingPermId(null)}>Hủy</button>
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

