import React, { useState, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { authService } from '../services/apiService';
import { FiShield, FiLock, FiEye, FiEyeOff, FiArrowLeft } from 'react-icons/fi';

const ResetPassword = () => {
  const loc = useLocation();
  const navigate = useNavigate();
  const emailFromState = (loc.state && loc.state.email) || '';

  const [email, setEmail] = useState(emailFromState);
  const [otp, setOtp] = useState(['', '', '', '', '', '']);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const inputsRef = useRef([]);

  const handleOtpChange = (i, val) => {
    const digit = val.replace(/\D/g, '').slice(0, 1);
    const next = [...otp];
    next[i] = digit;
    setOtp(next);
    if (digit && i < 5) inputsRef.current[i + 1]?.focus();
  };

  const handleKeyDown = (i, e) => {
    if (e.key === 'Backspace' && !otp[i] && i > 0) inputsRef.current[i - 1]?.focus();
  };

  const handlePaste = (e) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6);
    const next = ['', '', '', '', '', ''];
    for (let i = 0; i < pasted.length; i++) next[i] = pasted[i];
    setOtp(next);
    inputsRef.current[Math.min(pasted.length, 5)]?.focus();
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const code = otp.join('');
    if (code.length < 6) {
      setIsError(true);
      setMessage('Vui lòng nhập đủ 6 chữ số OTP.');
      return;
    }
    if (newPassword.length < 6) {
      setIsError(true);
      setMessage('Mật khẩu mới phải có ít nhất 6 ký tự.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setIsError(true);
      setMessage('Mật khẩu xác nhận không khớp.');
      return;
    }
    setLoading(true);
    setMessage(null);
    try {
      await authService.changePassword({ email, otpCode: code, newPassword });
      setIsError(false);
      setMessage('Đặt lại mật khẩu thành công! Đang chuyển về trang đăng nhập...');
      setDone(true);
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      setIsError(true);
      setMessage(
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        'Mã OTP không đúng hoặc đã hết hạn. Vui lòng thử lại.'
      );
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
        <h2>Đặt lại mật khẩu</h2>
        <p style={{ color: '#6b7280', fontSize: 13, marginBottom: 16 }}>
          Nhập mã OTP đã gửi đến email <strong>{email || 'của bạn'}</strong> và mật khẩu mới.
        </p>

        {message && (
          <div className={`auth-alert ${isError ? 'auth-alert-error' : 'auth-alert-success'}`}>
            {message}
          </div>
        )}

        {!done && (
          <form onSubmit={handleSubmit}>
            {!emailFromState && (
              <div className="form-group">
                <label>Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="email@company.com"
                  required
                />
              </div>
            )}

            <div className="form-group">
              <label>Mã OTP (6 chữ số)</label>
              <div style={{ display: 'flex', gap: 8, justifyContent: 'center' }} onPaste={handlePaste}>
                {otp.map((d, i) => (
                  <input
                    key={i}
                    ref={(el) => (inputsRef.current[i] = el)}
                    value={d}
                    onChange={(e) => handleOtpChange(i, e.target.value)}
                    onKeyDown={(e) => handleKeyDown(i, e)}
                    maxLength={1}
                    inputMode="numeric"
                    style={{
                      width: 44,
                      height: 50,
                      textAlign: 'center',
                      fontSize: 22,
                      fontWeight: 700,
                      border: '2px solid #d1d5db',
                      borderRadius: 8,
                      outline: 'none',
                    }}
                    onFocus={(e) => e.target.select()}
                  />
                ))}
              </div>
            </div>

            <div className="form-group">
              <label>Mật khẩu mới</label>
              <div style={{ position: 'relative' }}>
                <FiLock style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
                <input
                  type={showPassword ? 'text' : 'password'}
                  style={{ paddingLeft: 34, paddingRight: 36 }}
                  placeholder="Tối thiểu 6 ký tự"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                  minLength={6}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#9ca3af', padding: 0 }}
                >
                  {showPassword ? <FiEyeOff size={16} /> : <FiEye size={16} />}
                </button>
              </div>
            </div>

            <div className="form-group">
              <label>Xác nhận mật khẩu mới</label>
              <div style={{ position: 'relative' }}>
                <FiLock style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
                <input
                  type={showConfirm ? 'text' : 'password'}
                  style={{ paddingLeft: 34, paddingRight: 36 }}
                  placeholder="Nhập lại mật khẩu mới"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                  minLength={6}
                />
                <button
                  type="button"
                  onClick={() => setShowConfirm((v) => !v)}
                  style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#9ca3af', padding: 0 }}
                >
                  {showConfirm ? <FiEyeOff size={16} /> : <FiEye size={16} />}
                </button>
              </div>
            </div>

            <button className="auth-btn" type="submit" disabled={loading}>
              {loading ? <><span className="spinner" /> Đang xử lý...</> : 'Đặt lại mật khẩu'}
            </button>
          </form>
        )}

        <div className="auth-links" style={{ marginTop: 16 }}>
          <a href="/forgot-password" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <FiArrowLeft size={14} /> Gửi lại OTP
          </a>
          <span style={{ margin: '0 8px', color: '#d1d5db' }}>|</span>
          <a href="/login">Quay lại đăng nhập</a>
        </div>
      </div>
    </div>
  );
};

export default ResetPassword;
