import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../services/apiService';
import { FiShield, FiEye, FiEyeOff } from 'react-icons/fi';

const Register = () => {
  const [form, setForm] = useState({ username: '', password: '', fullName: '', email: '', organizationCode: '' });
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!form.username || !form.password || !form.email || !form.fullName) {
      setError('Vui lòng điền đầy đủ thông tin bắt buộc');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const res = await authService.register(form);
      
      const devOtp = res?.data?.dev_otp;
      navigate('/verify', { state: { email: form.email, devOtp } });
    } catch (err) {
      setError(err?.response?.data?.message || err?.response?.data?.error || err.message || 'Đăng ký thất bại');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-box" style={{ maxWidth: 460 }}>
        <div className="auth-logo">
          <div className="auth-logo-icon"><FiShield size={28} color="white" /></div>
          <h1>Hệ Thống Kiểm Soát Ra Vào</h1>
          <p>Tạo tài khoản quản lý</p>
        </div>
        <h2>Đăng ký tài khoản</h2>
        {error && <div className="auth-alert auth-alert-error">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <div className="form-group">
              <label>Họ và tên <span style={{color:'red'}}>*</span></label>
              <input placeholder="Nguyễn Văn A" value={form.fullName} onChange={(e) => set('fullName', e.target.value)} required />
            </div>
            <div className="form-group">
              <label>Tên đăng nhập <span style={{color:'red'}}>*</span></label>
              <input placeholder="admin" value={form.username} onChange={(e) => set('username', e.target.value)} required />
            </div>
          </div>
          <div className="form-group">
            <label>Email <span style={{color:'red'}}>*</span></label>
            <input type="email" placeholder="example@company.com" value={form.email} onChange={(e) => set('email', e.target.value)} required />
          </div>
          <div className="form-group">
            <label>Mật khẩu <span style={{color:'red'}}>*</span></label>
            <div style={{ position: 'relative' }}>
              <input type={showPw ? 'text' : 'password'} placeholder="Tối thiểu 8 ký tự" style={{ paddingRight: 38 }} value={form.password} onChange={(e) => set('password', e.target.value)} required />
              <button type="button" onClick={() => setShowPw(!showPw)} style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#9ca3af', padding: 0 }}>
                {showPw ? <FiEyeOff size={16} /> : <FiEye size={16} />}
              </button>
            </div>
          </div>
          <div className="form-group">
            <label>Mã công ty / tổ chức<span style={{color:'#9ca3af', fontWeight:400}}> (tuỳ chọn)</span></label>
            <input placeholder="VD: ORG001" value={form.organizationCode} onChange={(e) => set('organizationCode', e.target.value)} />
          </div>
          <button className="auth-btn" type="submit" disabled={loading}>
            {loading ? <><span className="spinner" /> Đang đăng ký...</> : 'Đăng ký'}
          </button>
        </form>
        <div className="auth-links">
          Đã có tài khoản? <a href="/login">Đăng nhập</a>
        </div>
      </div>
    </div>
  );
};

export default Register;
