import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../services/apiService';
import { FiShield, FiMail, FiArrowLeft } from 'react-icons/fi';

const ForgotPassword = () => {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage(null);
    try {
      await authService.forgotPassword(email);
      setIsError(false);
      setSent(true);
      setMessage('OTP đã được gửi! Đang chuyển đến trang đặt lại mật khẩu...');
      setTimeout(() => navigate('/reset-password', { state: { email } }), 1500);
    } catch (err) {
      setMessage(err?.response?.data?.message || err?.response?.data?.error || 'Gửi yêu cầu thất bại. Vui lòng thử lại.');
      setIsError(true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-box">
        <div className="auth-logo">
          <div className="auth-logo-icon"><FiShield size={28} color="white" /></div>
          <h1>Hệ Thống Kiểm Soát Ra Vào</h1>
        </div>
        <h2>Quên mật khẩu</h2>
        <p style={{ color: '#6b7280', fontSize: 13, marginBottom: 16 }}>Nhập email đã đăng ký để nhận liên kết đặt lại mật khẩu.</p>
        {message && <div className={`auth-alert ${isError ? 'auth-alert-error' : 'auth-alert-success'}`}>{message}</div>}
        {!sent && (
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>Email</label>
              <div style={{ position: 'relative' }}>
                <FiMail style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
                <input type="email" style={{ paddingLeft: 34 }} placeholder="example@company.com" value={email} onChange={(e) => setEmail(e.target.value)} required />
              </div>
            </div>
            <button className="auth-btn" type="submit" disabled={loading}>
              {loading ? <><span className="spinner" /> Đang gửi...</> : 'Gửi yêu cầu'}
            </button>
          </form>
        )}
        <div className="auth-links" style={{ marginTop: 16 }}>
          <a href="/login" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <FiArrowLeft size={14} /> Quay lại đăng nhập
          </a>
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;
