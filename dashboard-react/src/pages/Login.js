import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authService } from '../services/apiService';
import { FiShield, FiUser, FiLock, FiEye, FiEyeOff } from 'react-icons/fi';

const Login = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPw, setShowPw] = useState(false);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await authService.login(username, password);
      navigate('/');
    } catch (err) {
      
      if (err?.code === 'ECONNABORTED' || err?.message?.includes('timeout')) {
        setError('Server không phản hồi. Kiểm tra xem backend có chạy không.');
      } else if (err?.response?.status === 401 || err?.response?.status === 400) {
        setError(err?.response?.data?.message || err?.response?.data?.error || 'Tên đăng nhập hoặc mật khẩu sai');
      } else if (!err?.response) {
        setError('Không kết nối được server. Kiểm tra xem backend (http://localhost:8080) có chạy không.');
      } else {
        setError(err?.response?.data?.message || err?.response?.data?.error || err.message || 'Đăng nhập thất bại');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-box">
        <div className="auth-logo">
          <div className="auth-logo-icon">
            <FiShield size={28} color="white" />
          </div>
          <h1>Hệ Thống Kiểm Soát Ra Vào</h1>
          <p>Xác thực CCCD · Nhận diện khuôn mặt</p>
        </div>

        <h2>Đăng nhập</h2>

        {error && <div className="auth-alert auth-alert-error">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Tên đăng nhập</label>
            <div style={{ position: 'relative' }}>
              <FiUser style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
              <input
                style={{ paddingLeft: 34 }}
                placeholder="Nhập tên đăng nhập"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoFocus
              />
            </div>
          </div>

          <div className="form-group">
            <label>Mật khẩu</label>
            <div style={{ position: 'relative' }}>
              <FiLock style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
              <input
                style={{ paddingLeft: 34, paddingRight: 38 }}
                type={showPw ? 'text' : 'password'}
                placeholder="Nhập mật khẩu"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
              <button
                type="button"
                onClick={() => setShowPw(!showPw)}
                style={{ position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#9ca3af', padding: 0 }}
              >
                {showPw ? <FiEyeOff size={16} /> : <FiEye size={16} />}
              </button>
            </div>
          </div>

          <button className="auth-btn" type="submit" disabled={loading}>
            {loading ? <><span className="spinner" /> Đang đăng nhập...</> : 'Đăng nhập'}
          </button>
        </form>

        <div className="auth-links">
          <a href="/forgot-password">Quên mật khẩu?</a>
          {' · '}
          <a href="/register">Đăng ký tài khoản</a>
        </div>
      </div>
    </div>
  );
};

export default Login;
