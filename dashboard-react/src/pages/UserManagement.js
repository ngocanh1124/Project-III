import React, { useState, useEffect, useCallback } from 'react';
import { authService, apiClient } from '../services/apiService';
import { FiPlus, FiEdit2, FiTrash2, FiRefreshCw, FiLock, FiUnlock, FiKey } from 'react-icons/fi';

const ROLES = ['VIEWER', 'HR_MANAGER', 'OPERATOR', 'ADMIN', 'SUPER_ADMIN'];
const ROLE_ORDINAL = { VIEWER: 0, HR_MANAGER: 1, OPERATOR: 2, ADMIN: 3, SUPER_ADMIN: 4 };
const ROLE_LABELS = {
  VIEWER:      { label: 'Chỉ xem',     color: '#374151', bg: '#f3f4f6' },
  HR_MANAGER:  { label: 'Quản lý HR',  color: '#374151', bg: '#f3f4f6' },
  OPERATOR:    { label: 'Vận hành',    color: '#374151', bg: '#f3f4f6' },
  ADMIN:       { label: 'Quản trị',    color: '#374151', bg: '#f3f4f6' },
  SUPER_ADMIN: { label: 'Super Admin', color: '#374151', bg: '#f3f4f6' },
};

const EMPTY_FORM = { username: '', email: '', fullName: '', password: '', role: 'VIEWER' };

export default function UserManagement() {
  const [users, setUsers]           = useState([]);
  const [loading, setLoading]       = useState(false);
  const [showModal, setShowModal]   = useState(false);
  const [showPwModal, setShowPwModal] = useState(null); 
  const [newPw, setNewPw]           = useState('');
  const [form, setForm]             = useState(EMPTY_FORM);
  const [msg, setMsg]               = useState(null);
  const [search, setSearch]         = useState('');

  const me = authService.getUser();
  const myRole = (me?.role || '').toLowerCase();
  const myRoleUpper = (me?.role || '').toUpperCase();
  const myOrdinal = ROLE_ORDINAL[myRoleUpper] ?? -1;
  const isSuperAdmin = myRole === 'super_admin';

  const toast = (text, type = 'success') => {
    setMsg({ text, type });
    setTimeout(() => setMsg(null), 3500);
  };

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const res = await apiClient.get('/api/admin/users');
      setUsers(Array.isArray(res.data) ? res.data : []);
    } catch {
      toast('Lỗi tải danh sách tài khoản', 'error');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchUsers(); }, [fetchUsers]);

  const handleCreate = async (e) => {
    e.preventDefault();
    try {
      await apiClient.post('/api/admin/users', form);
      setShowModal(false);
      setForm(EMPTY_FORM);
      fetchUsers();
      toast('Tạo tài khoản thành công!');
    } catch (err) {
      toast(err?.response?.data?.error || 'Lỗi tạo tài khoản', 'error');
    }
  };

  const handleRoleChange = async (id, newRole) => {
    try {
      await apiClient.put(`/api/admin/users/${id}/role`, { role: newRole });
      setUsers((prev) => prev.map((u) => u.id === id ? { ...u, role: newRole } : u));
      toast('Đã cập nhật quyền!');
    } catch (err) {
      toast(err?.response?.data?.error || 'Lỗi thay đổi quyền', 'error');
    }
  };

  const handleToggle = async (id) => {
    try {
      const res = await apiClient.put(`/api/admin/users/${id}/toggle-active`);
      setUsers((prev) => prev.map((u) => u.id === id ? { ...u, isActive: res.data.isActive } : u));
      toast('Đã cập nhật trạng thái!');
    } catch (err) {
      toast(err?.response?.data?.error || 'Lỗi thay đổi trạng thái', 'error');
    }
  };

  const handleResetPw = async () => {
    if (!newPw || newPw.length < 6) { toast('Mật khẩu tối thiểu 6 ký tự', 'error'); return; }
    try {
      await apiClient.put(`/api/admin/users/${showPwModal}/reset-password`, { password: newPw });
      setShowPwModal(null);
      setNewPw('');
      toast('Đã đặt lại mật khẩu!');
    } catch {
      toast('Lỗi đặt lại mật khẩu', 'error');
    }
  };

  const handleDelete = async (id, username) => {
    if (!window.confirm(`Xóa tài khoản "${username}"? Không thể hoàn tác.`)) return;
    try {
      await apiClient.delete(`/api/admin/users/${id}`);
      setUsers((prev) => prev.filter((u) => u.id !== id));
      toast('Đã xóa tài khoản!');
    } catch (err) {
      toast(err?.response?.data?.error || 'Lỗi xóa tài khoản', 'error');
    }
  };

  const filtered = users.filter((u) =>
    `${u.username} ${u.email} ${u.fullName}`.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="page">
      <div className="page-header">
        <h1>Quản lý tài khoản ({users.length})</h1>
        <div className="page-header-actions">
          <button className="btn btn-secondary btn-sm" onClick={fetchUsers} disabled={loading}>
            <FiRefreshCw size={13} className={loading ? 'spin' : ''} />
          </button>
          <button className="btn btn-primary" onClick={() => { setForm(EMPTY_FORM); setShowModal(true); }}>
            <FiPlus size={14} /> Tạo tài khoản
          </button>
        </div>
      </div>

      {msg && (
        <div style={{ padding: '10px 14px', borderRadius: 8, marginBottom: 14, fontSize: 13,
          background: msg.type === 'error' ? '#fee2e2' : '#dcfce7',
          color: msg.type === 'error' ? '#dc2626' : '#16a34a' }}>
          {msg.text}
        </div>
      )}

      {}
      <div className="card" style={{ marginBottom: 16, padding: '12px 16px' }}>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
          <span style={{ fontSize: 12, color: '#6b7280', fontWeight: 600, marginRight: 4 }}>Cấp quyền:</span>
          {ROLES.map((r) => {
            const { label, color, bg } = ROLE_LABELS[r];
            const desc = {
              VIEWER:      'Chỉ xem tổng quan',
              HR_MANAGER:  'Quản lý nhân viên',
              OPERATOR:    'Vận hành thiết bị, mở cửa từ xa',
              ADMIN:       'Toàn bộ tính năng tổ chức',
              SUPER_ADMIN: 'Toàn hệ thống + quản lý tài khoản',
            }[r];
            return (
              <span key={r} title={desc}
                style={{ fontSize: 11, padding: '3px 10px', borderRadius: 12,
                  fontWeight: 600, color, background: bg, cursor: 'help' }}>
                {label}
              </span>
            );
          })}
        </div>
      </div>

      <div className="card">
        <div style={{ marginBottom: 14 }}>
          <input
            style={{ padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13, width: 280 }}
            placeholder="Tìm theo tên, username, email..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>

        {loading ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : (
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Họ tên</th>
                  <th>Email</th>
                  <th>Quyền</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr><td colSpan={6} className="empty-state">Không có tài khoản nào</td></tr>
                ) : filtered.map((u) => {
                  const roleInfo = ROLE_LABELS[u.role] || ROLE_LABELS.VIEWER;
                  const isMe = u.username === me?.username;
                  const canDelete = !isMe && (ROLE_ORDINAL[u.role] ?? 99) < myOrdinal;
                  return (
                    <tr key={u.id} style={isMe ? { background: '#eff6ff' } : {}}>
                      <td>
                        <span style={{ fontFamily: 'monospace', fontSize: 13, fontWeight: 600 }}>{u.username}</span>
                        {isMe && <span style={{ marginLeft: 6, fontSize: 10, color: 'var(--primary)', fontWeight: 700 }}>● Bạn</span>}
                      </td>
                      <td style={{ fontWeight: 500 }}>{u.fullName || '-'}</td>
                      <td style={{ fontSize: 12, color: '#6b7280' }}>{u.email}</td>
                      <td>
                        {isMe ? (
                          
                          <span style={{ fontSize: 12, padding: '3px 10px', borderRadius: 12,
                            fontWeight: 600, color: roleInfo.color, background: roleInfo.bg }}>
                            {roleInfo.label}
                          </span>
                        ) : (
                          <select
                            value={u.role}
                            onChange={(e) => handleRoleChange(u.id, e.target.value)}
                            style={{ fontSize: 12, padding: '4px 8px', border: '1px solid #d1d5db',
                              borderRadius: 6, background: roleInfo.bg, color: roleInfo.color,
                              fontWeight: 600, cursor: 'pointer' }}
                          >
                            {ROLES.map((r) => (
                              <option key={r} value={r}>{ROLE_LABELS[r].label}</option>
                            ))}
                          </select>
                        )}
                      </td>
                      <td>
                        <span className={`badge badge-${u.isActive ? 'success' : 'secondary'}`}>
                          {u.isActive ? 'Hoạt động' : 'Bị khóa'}
                        </span>
                      </td>
                      <td>
                        <div className="table-actions">
                          {!isMe && (
                            <button className="btn-icon" title={u.isActive ? 'Khóa tài khoản' : 'Mở khóa'}
                              onClick={() => handleToggle(u.id)}
                              style={{ color: 'var(--primary)' }}>
                              {u.isActive ? <FiLock size={14} /> : <FiUnlock size={14} />}
                            </button>
                          )}
                          {canDelete && (
                            <button className="btn-icon" title="Xóa tài khoản"
                              onClick={() => handleDelete(u.id, u.username)}
                              style={{ color: '#dc2626' }}>
                              <FiTrash2 size={14} />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" style={{ maxWidth: 440 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Tạo tài khoản mới</h3>
              <button className="modal-close" onClick={() => setShowModal(false)}>×</button>
            </div>
            <form onSubmit={handleCreate}>
              <div className="modal-body">
                <div className="form-group">
                  <label>Username <span style={{ color: 'red' }}>*</span></label>
                  <input placeholder="vd: nguyen.van.a" value={form.username}
                    onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))} required />
                </div>
                <div className="form-group">
                  <label>Họ và tên</label>
                  <input placeholder="Nguyễn Văn A" value={form.fullName}
                    onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))} />
                </div>
                <div className="form-group">
                  <label>Email <span style={{ color: 'red' }}>*</span></label>
                  <input type="email" placeholder="a@example.com" value={form.email}
                    onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))} required />
                </div>
                <div className="form-group">
                  <label>Mật khẩu <span style={{ color: 'red' }}>*</span></label>
                  <input type="password" placeholder="Tối thiểu 6 ký tự" value={form.password}
                    onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))} required minLength={6} />
                </div>
                <div className="form-group">
                  <label>Phân quyền</label>
                  <select value={form.role} onChange={(e) => setForm((f) => ({ ...f, role: e.target.value }))}>
                    {ROLES.map((r) => <option key={r} value={r}>{ROLE_LABELS[r].label}</option>)}
                  </select>
                  <p style={{ fontSize: 11, color: '#6b7280', marginTop: 4 }}>
                    {{
                      VIEWER:      '→ Chỉ xem tổng quan',
                      HR_MANAGER:  '→ Quản lý nhân viên, import Excel',
                      OPERATOR:    '→ Xem thiết bị, mở cửa từ xa',
                      ADMIN:       '→ Toàn bộ tính năng tổ chức',
                      SUPER_ADMIN: '→ Toàn hệ thống, quản lý tài khoản',
                    }[form.role]}
                  </p>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Hủy</button>
                <button type="submit" className="btn btn-primary"><FiPlus size={14} /> Tạo tài khoản</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {}
      {showPwModal && (
        <div className="modal-overlay" onClick={() => setShowPwModal(null)}>
          <div className="modal" style={{ maxWidth: 360 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Đặt lại mật khẩu</h3>
              <button className="modal-close" onClick={() => setShowPwModal(null)}>×</button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label>Mật khẩu mới</label>
                <input type="password" placeholder="Tối thiểu 6 ký tự" value={newPw}
                  onChange={(e) => setNewPw(e.target.value)} autoFocus />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={() => setShowPwModal(null)}>Hủy</button>
              <button className="btn btn-primary" onClick={handleResetPw}><FiKey size={14} /> Đặt lại</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
