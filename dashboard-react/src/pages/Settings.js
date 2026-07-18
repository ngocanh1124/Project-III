import React, { useState, useEffect } from 'react';
import { authService, organizationService, apiClient } from '../services/apiService';
import { FiUser, FiLock, FiSettings, FiSave, FiEye, FiEyeOff, FiBriefcase, FiShield, FiWifi } from 'react-icons/fi';

export default function Settings() {
  const [user, setUser] = useState(null);
  const [profile, setProfile] = useState({ fullName: '', email: '', cccd: '' });
  const [pw, setPw] = useState({ current: '', newPw: '', confirm: '' });
  const [showPw, setShowPw] = useState({ current: false, newPw: false, confirm: false });
  const [sysConfig, setSysConfig] = useState({
    apiUrl: localStorage.getItem('apiUrl') || 'http://localhost:8080',
    refreshInterval: localStorage.getItem('refreshInterval') || '30',
  });
  const [saving, setSaving] = useState({ profile: false, pw: false, sys: false, org: false, pin: false });
  const [msgs, setMsgs] = useState({ profile: null, pw: null, sys: null, org: null, pin: null });
  const [masterPin, setMasterPin] = useState('');
  const [currentPin, setCurrentPin] = useState(null);
  const [orgs, setOrgs] = useState([]);
  const [orgForm, setOrgForm] = useState({ name: '', code: '', description: '', phone: '', email: '', address: '' });
  const [editingOrg, setEditingOrg] = useState(null);

  useEffect(() => {
    const u = authService.getUser();
    setUser(u);
    if (u) setProfile({ 
      fullName: u.fullName || u.username || '', 
      email: u.email || '', 
      cccd: u.cccd || '' 
    });
    organizationService.getAll().then(r => {
      const list = r.data;
      setOrgs(Array.isArray(list) ? list : []);
    }).catch(() => {});
    apiClient.get('/api/config/master-pin').then(r => setCurrentPin(r.data?.pin || null)).catch(() => {});
  }, []);

  const myRole = (user?.role || '').toLowerCase();
  const isAdmin = ['admin', 'super_admin'].includes(myRole);

  const handleSaveMasterPin = async (e) => {
    e.preventDefault();
    if (!masterPin || masterPin.length < 4) { toast('pin', 'PIN phải có ít nhất 4 ký tự số', 'error'); return; }
    setSaving((s) => ({ ...s, pin: true }));
    try {
      await apiClient.post('/api/config/update-pin', { newPin: masterPin });
      setCurrentPin(masterPin);
      setMasterPin('');
      toast('pin', 'Cập nhật Master PIN thành công!');
    } catch (err) {
      toast('pin', err?.response?.data || 'Lỗi cập nhật PIN', 'error');
    } finally {
      setSaving((s) => ({ ...s, pin: false }));
    }
  };

  const toast = (key, text, type = 'success') => {
    setMsgs((m) => ({ ...m, [key]: { text, type } }));
    setTimeout(() => setMsgs((m) => ({ ...m, [key]: null })), 3500);
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSaving((s) => ({ ...s, profile: true }));
    try {
      await authService.updateProfile(profile);
      
      const u = authService.getUser();
      if (u) {
        const updated = { ...u, ...profile };
        sessionStorage.setItem('auth_user', JSON.stringify(updated));
        setUser(updated);
      }
      toast('profile', 'Cập nhật hồ sơ thành công!');
    } catch (err) {
      toast('profile', err?.response?.data?.message || err?.response?.data?.error || 'Lỗi cập nhật hồ sơ', 'error');
    } finally {
      setSaving((s) => ({ ...s, profile: false }));
    }
  };

  const handleChangePw = async (e) => {
    e.preventDefault();
    if (pw.newPw !== pw.confirm) { toast('pw', 'Mật khẩu mới không khớp', 'error'); return; }
    if (pw.newPw.length < 6) { toast('pw', 'Mật khẩu mới phải ≥ 6 ký tự', 'error'); return; }
    setSaving((s) => ({ ...s, pw: true }));
    try {
      await authService.changeMyPassword({ currentPassword: pw.current, newPassword: pw.newPw });
      setPw({ current: '', newPw: '', confirm: '' });
      toast('pw', 'Đổi mật khẩu thành công!');
    } catch (err) {
      toast('pw', err?.response?.data?.message || err?.response?.data?.error || 'Mật khẩu hiện tại không đúng', 'error');
    } finally {
      setSaving((s) => ({ ...s, pw: false }));
    }
  };

  const handleSaveSys = (e) => {
    e.preventDefault();
    localStorage.setItem('apiUrl', sysConfig.apiUrl);
    localStorage.setItem('refreshInterval', sysConfig.refreshInterval);
    toast('sys', `Đã lưu! Mọi request sẽ dùng: ${sysConfig.apiUrl}`);
  };

  const handleTestConnection = async () => {
    setSaving((s) => ({ ...s, sys: true }));
    const url = sysConfig.apiUrl.replace(/\/$/, '');
    try {
      const res = await fetch(`${url}/actuator/health`, { signal: AbortSignal.timeout(4000) });
      if (res.ok) {
        toast('sys', `Kết nối thành công tới ${url}`);
      } else {
        toast('sys', `Server phản hồi HTTP ${res.status} — kiểm tra lại URL`, 'error');
      }
    } catch {
      toast('sys', `Không thể kết nối tới ${url} — kiểm tra server đang chạy và URL đúng`, 'error');
    } finally {
      setSaving((s) => ({ ...s, sys: false }));
    }
  };

  const handleSaveOrg = async (e) => {
    e.preventDefault();
    setSaving((s) => ({ ...s, org: true }));
    try {
      if (editingOrg) {
        const updated = await organizationService.update(editingOrg.id, orgForm);
        setOrgs((prev) => prev.map((o) => o.id === editingOrg.id ? (updated.data?.id ? updated.data : o) : o));
        toast('org', 'Cập nhật tổ chức thành công!');
      } else {
        const created = await organizationService.create(orgForm);
        setOrgs((prev) => [...prev, ...(created.data?.id ? [created.data] : [])]);
        toast('org', 'Tạo tổ chức thành công!');
      }
      setOrgForm({ name: '', code: '', description: '', phone: '', email: '', address: '' });
      setEditingOrg(null);
    } catch (err) {
      toast('org', err?.response?.data?.error || err?.response?.data?.message || 'Lỗi lưu tổ chức', 'error');
    } finally {
      setSaving((s) => ({ ...s, org: false }));
    }
  };

  const Alert = ({ msg }) => msg ? (
    <div style={{ padding: '9px 13px', borderRadius: 7, fontSize: 13, marginTop: 10, background: msg.type === 'error' ? '#fee2e2' : '#dcfce7', color: msg.type === 'error' ? '#dc2626' : '#16a34a' }}>
      {msg.text}
    </div>
  ) : null;

  const EyeToggle = ({ k }) => (
    <button type="button" onClick={() => setShowPw((s) => ({ ...s, [k]: !s[k] }))}
      style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#9ca3af' }}>
      {showPw[k] ? <FiEyeOff size={16} /> : <FiEye size={16} />}
    </button>
  );

  return (
    <div className="page">
      <div className="page-header">
        <h1>Cài đặt</h1>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 20 }}>

        {}
        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
            <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white' }}>
              <FiUser size={18} />
            </div>
            <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>Hồ sơ tài khoản</h3>
          </div>

          {user && (
            <div style={{ display: 'flex', gap: 10, alignItems: 'center', padding: '10px 12px', background: '#f8fafc', borderRadius: 8, marginBottom: 16, fontSize: 13 }}>
              <div className="avatar" style={{ width: 42, height: 42, borderRadius: '50%', background: 'var(--primary)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, flexShrink: 0 }}>
                {(user.fullName || user.username || '?').charAt(0).toUpperCase()}
              </div>
              <div>
                <div style={{ fontWeight: 600, color: '#1f2937' }}>{user.fullName || user.username}</div>
                <div style={{ color: '#6b7280', fontSize: 12 }}>{user.email || user.username}</div>
              </div>
            </div>
          )}

          <form onSubmit={handleSaveProfile}>
            <div className="form-group">
              <label>Họ và tên</label>
              <input value={profile.fullName} onChange={(e) => setProfile((p) => ({ ...p, fullName: e.target.value }))} placeholder="Nguyễn Văn A" />
            </div>
            <div className="form-group">
              <label>Số CCCD</label>
              <input value={profile.cccd} onChange={(e) => setProfile((p) => ({ ...p, cccd: e.target.value }))} placeholder="001200001234" />
            </div>
            <div className="form-group">
              <label>Email</label>
              <input type="email" value={profile.email} onChange={(e) => setProfile((p) => ({ ...p, email: e.target.value }))} placeholder="email@company.com" />
            </div>
            <button type="submit" className="btn btn-primary" disabled={saving.profile}>
              <FiSave size={14} /> {saving.profile ? 'Đang lưu...' : 'Lưu hồ sơ'}
            </button>
            <Alert msg={msgs.profile} />
          </form>
        </div>

        {}
        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
            <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white' }}>
              <FiLock size={18} />
            </div>
            <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>Đổi mật khẩu</h3>
          </div>
          <form onSubmit={handleChangePw}>
            {[
              { k: 'current', label: 'Mật khẩu hiện tại', ph: '••••••••' },
              { k: 'newPw', label: 'Mật khẩu mới', ph: '••••••••' },
              { k: 'confirm', label: 'Nhập lại mật khẩu mới', ph: '••••••••' },
            ].map(({ k, label, ph }) => (
              <div key={k} className="form-group">
                <label>{label}</label>
                <div style={{ position: 'relative' }}>
                  <input
                    type={showPw[k] ? 'text' : 'password'}
                    value={pw[k]}
                    onChange={(e) => setPw((p) => ({ ...p, [k]: e.target.value }))}
                    placeholder={ph}
                    required
                    style={{ paddingRight: 38 }}
                  />
                  <EyeToggle k={k} />
                </div>
              </div>
            ))}
            <button type="submit" className="btn btn-primary" disabled={saving.pw}>
              <FiLock size={14} /> {saving.pw ? 'Đang lưu...' : 'Đổi mật khẩu'}
            </button>
            <Alert msg={msgs.pw} />
          </form>
        </div>

        {}
        <div className="card">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
            <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white' }}>
              <FiSettings size={18} />
            </div>
            <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>Cài đặt hệ thống</h3>
          </div>
          <form onSubmit={handleSaveSys}>
            <div className="form-group">
              <label>Backend API URL</label>
              <input
                value={sysConfig.apiUrl}
                onChange={(e) => setSysConfig((c) => ({ ...c, apiUrl: e.target.value }))}
                placeholder="http://192.168.x.x:8080"
              />
              <small style={{ fontSize: 11, color: '#9ca3af', display: 'block', marginTop: 4 }}>
              </small>
            </div>
            <div className="form-group">
              <label>Tự động làm mới Dashboard (giây)</label>
              <input
                type="number"
                min={5}
                max={300}
                value={sysConfig.refreshInterval}
                onChange={(e) => setSysConfig((c) => ({ ...c, refreshInterval: e.target.value }))}
              />
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button type="submit" className="btn btn-primary" disabled={saving.sys}>
                <FiSave size={14} /> {saving.sys ? 'Đang xử lý...' : 'Lưu cài đặt'}
              </button>
              <button type="button" className="btn btn-secondary" disabled={saving.sys} onClick={handleTestConnection}>
                <FiWifi size={14} /> Kiểm tra kết nối
              </button>
            </div>
            <Alert msg={msgs.sys} />
          </form>
        </div>

      </div>

      {}
      {isAdmin && <div className="card" style={{ marginTop: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
          <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white' }}>
            <FiBriefcase size={18} />
          </div>
          <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>Quản lý tổ chức</h3>
        </div>

        {}
        {orgs.length > 0 && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 8 }}>Tổ chức hiện có:</div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {orgs.map((o) => (
                <div key={o.id} onClick={() => { setEditingOrg(o); setOrgForm({ name: o.name || '', code: o.code || '', description: o.description || '', phone: o.phone || '', email: o.email || '', address: o.address || '' }); }}
                  style={{ padding: '6px 12px', background: editingOrg?.id === o.id ? '#dbeafe' : '#f8fafc', border: `1px solid ${editingOrg?.id === o.id ? 'var(--primary)' : '#e5e7eb'}`, borderRadius: 6, cursor: 'pointer', fontSize: 13 }}>
                  <strong>{o.name}</strong> <span style={{ color: '#9ca3af', fontSize: 11 }}>({o.code})</span>
                </div>
              ))}
            </div>
          </div>
        )}

        <form onSubmit={handleSaveOrg}>
          <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 10, color: editingOrg ? '#8b5cf6' : '#374151' }}>
            {editingOrg ? `Chỉnh sửa: ${editingOrg.name}` : 'Tạo tổ chức mới'}
            {editingOrg && <button type="button" onClick={() => { setEditingOrg(null); setOrgForm({ name: '', code: '', description: '', phone: '', email: '', address: '' }); }} style={{ marginLeft: 10, fontSize: 11, color: '#9ca3af', background: 'none', border: 'none', cursor: 'pointer' }}>Hủy</button>}
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div className="form-group">
              <label>Tên tổ chức <span style={{ color: 'red' }}>*</span></label>
              <input value={orgForm.name} onChange={(e) => setOrgForm((f) => ({ ...f, name: e.target.value }))} placeholder="Công ty ABC" required />
            </div>
            <div className="form-group">
              <label>Mã tổ chức <span style={{ color: 'red' }}>*</span></label>
              <input value={orgForm.code} onChange={(e) => setOrgForm((f) => ({ ...f, code: e.target.value }))} placeholder="ABC001" required disabled={!!editingOrg} />
            </div>
            <div className="form-group">
              <label>Số điện thoại</label>
              <input value={orgForm.phone} onChange={(e) => setOrgForm((f) => ({ ...f, phone: e.target.value }))} placeholder="0901234567" />
            </div>
            <div className="form-group">
              <label>Email</label>
              <input type="email" value={orgForm.email} onChange={(e) => setOrgForm((f) => ({ ...f, email: e.target.value }))} placeholder="contact@company.com" />
            </div>
            <div className="form-group" style={{ gridColumn: '1 / -1' }}>
              <label>Địa chỉ</label>
              <input value={orgForm.address} onChange={(e) => setOrgForm((f) => ({ ...f, address: e.target.value }))} placeholder="123 Đường ABC, TP.HCM" />
            </div>
          </div>
          <Alert msg={msgs.org} />
          <button type="submit" className="btn btn-primary" disabled={saving.org} style={{ marginTop: 12 }}>
            <FiSave size={14} /> {saving.org ? 'Đang lưu...' : editingOrg ? 'Cập nhật tổ chức' : 'Tạo tổ chức'}
          </button>
        </form>
      </div>}

      {}
      {isAdmin && <div className="card" style={{ marginTop: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
          <div style={{ width: 36, height: 36, borderRadius: '50%', background: 'var(--primary)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white' }}>
            <FiShield size={18} />
          </div>
          <h3 style={{ margin: 0, fontSize: 15, fontWeight: 600 }}>Master PIN thiết bị (App Android)</h3>
        </div>

        {currentPin && (
          <div style={{ padding: '10px 14px', background: '#f3f4f6', border: '1px solid #e5e7eb', borderRadius: 8, marginBottom: 16, fontSize: 13 }}>
            PIN hiện tại: <code style={{ fontSize: 16, fontWeight: 700, letterSpacing: 4, color: '#374151' }}>{currentPin}</code>
          </div>
        )}
        <form onSubmit={handleSaveMasterPin}>
          <div className="form-group">
            <label>Master PIN mới (số)</label>
            <input
              type="number"
              value={masterPin}
              onChange={(e) => setMasterPin(e.target.value)}
              placeholder="Ví dụ: 123456"
              maxLength={8}
            />

          </div>
          <button type="submit" className="btn btn-primary" disabled={saving.pin}>
            <FiShield size={14} /> {saving.pin ? 'Đang lưu...' : 'Cập nhật Master PIN'}
          </button>
          <Alert msg={msgs.pin} />
        </form>
      </div>}

      {}
      {!isAdmin && (
        <div className="card" style={{ marginTop: 20, background: '#f3f4f6', border: '1px solid #e5e7eb' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <FiShield size={18} style={{ color: 'var(--primary)', flexShrink: 0 }} />
            <div>
              <div style={{ fontWeight: 600, fontSize: 14, color: '#374151' }}>Quyền hạn chế</div>
              <div style={{ fontSize: 12, color: '#6b7280', marginTop: 4 }}>Tài khoản của bạn thuộc vai trò <strong>Người xem (VIEWER)</strong>. Cấu hình hệ thống, quản lý tổ chức và mã PIN thiết bị chỉ dành cho Admin.</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

