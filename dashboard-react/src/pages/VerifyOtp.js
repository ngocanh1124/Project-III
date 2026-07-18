import React, { useState, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { authService } from '../services/apiService';
import { FiShield } from 'react-icons/fi';

const VerifyOtp = () => {
  const loc = useLocation();
  const navigate = useNavigate();
  const emailFromState = (loc.state && loc.state.email) || '';
  const devOtp = (loc.state && loc.state.devOtp) || null;
  const [email, setEmail] = useState(emailFromState);
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);
  const [loading, setLoading] = useState(false);
  const inputsRef = useRef([]);

  const handleOtpChange = (i, val) => {
    const digits = val.replace(/\D/g, '').slice(0, 1);
    const next = [...otp];
    next[i] = digits;
    setOtp(next);
    if (digits && i < 5) inputsRef.current[i + 1]?.focus();
  };

  const handleKeyDown = (i, e) => {
    if (e.key === 'Backspace' && !otp[i] && i > 0) inputsRef.current[i - 1]?.focus();
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const code = otp.join('');
    if (code.length < 6) { setIsError(true); setMessage('Vui lòng nhập đủ 6 chữ số OTP'); return; }
    setLoading(true);
    setMessage(null);
    try {
      await authService.verifyOtp(email, code);
      setIsError(false);
      setMessage('Xác thực thành công!');
      setTimeout(() => navigate('/login'), 1200);
    } catch (err) {
      setIsError(true);
      setMessage(err?.response?.data?.message || err?.response?.data?.error || err.message || 'Mã OTP không đúng hoặc đã hết hạn');
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
        <h2>Xác thực OTP</h2>
        <p style={{ color: '#6b7280', fontSize: 13, marginBottom: 16 }}>Nhập mã 6 chữ số được gửi đến email <strong>{email}</strong></p>
        {devOtp && (
          <div style={{ background: '#fef9c3', border: '1px solid #fbbf24', borderRadius: 8, padding: '10px 14px', marginBottom: 12, fontSize: 13, color: '#92400e' }}>
            <strong>[DEV]</strong> Mail chưa cấu hình — OTP của bạn là: <strong style={{ fontSize: 18, letterSpacing: 3 }}>{devOtp}</strong>
          </div>
        )}
        {message && <div className={`auth-alert ${isError ? 'auth-alert-error' : 'auth-alert-success'}`}>{message}</div>}
        <form onSubmit={handleSubmit}>
          {!emailFromState && (
            <div className="form-group">
              <label>Email</label>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email@company.com" required />
            </div>
          )}
          <div className="form-group">
            <label>Mã OTP</label>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }}>
              {otp.map((d, i) => (
                <input
                  key={i}
                  ref={(el) => (inputsRef.current[i] = el)}
                  value={d}
                  onChange={(e) => handleOtpChange(i, e.target.value)}
                  onKeyDown={(e) => handleKeyDown(i, e)}
                  maxLength={1}
                  inputMode="numeric"
                  style={{ width: 44, height: 50, textAlign: 'center', fontSize: 22, fontWeight: 700, border: '2px solid #d1d5db', borderRadius: 8, outline: 'none' }}
                  onFocus={(e) => e.target.select()}
                />
              ))}
            </div>
          </div>
          <button className="auth-btn" type="submit" disabled={loading}>
            {loading ? <><span className="spinner" /> Đang xác thực...</> : 'Xác nhận'}
          </button>
        </form>
        <div className="auth-links" style={{ marginTop: 16 }}>
          <a href="/login">Quay lại đăng nhập</a>
        </div>
      </div>
    </div>
  );
};

export default VerifyOtp;
