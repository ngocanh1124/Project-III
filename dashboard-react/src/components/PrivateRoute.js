import React, { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { authService, apiClient } from '../services/apiService';

const isTokenExpired = (token) => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    if (!payload.exp) return false;
    return payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
};

const clearAuth = () => {
  sessionStorage.removeItem('auth_token');
  sessionStorage.removeItem('auth_user');
  sessionStorage.removeItem('auth_basic');
};

const PrivateRoute = ({ children }) => {
  const token = sessionStorage.getItem('auth_token');
  const user = authService.getUser();

  
  if (!token || !user || isTokenExpired(token)) {
    clearAuth();
    return <Navigate to="/login" replace />;
  }

  
  
  return <ServerTokenGuard>{children}</ServerTokenGuard>;
};

function ServerTokenGuard({ children }) {
  const [status, setStatus] = useState('checking'); 

  useEffect(() => {
    let cancelled = false;
    apiClient.get('/api/auth/validate')
      .then(() => { if (!cancelled) setStatus('ok'); })
      .catch((err) => {
        if (!cancelled) {
          
          
          
          if (err.response?.status === 401) {
            setStatus('invalid');
          } else {
            setStatus('ok');
          }
        }
      });
    return () => { cancelled = true; };
  }, []);

  if (status === 'checking') {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center',
                    height: '100vh', background: '#0f0f23', color: '#9ca3af', fontSize: 15 }}>
        Đang xác thực...
      </div>
    );
  }
  if (status === 'invalid') {
    return <Navigate to="/login" replace />;
  }
  return children;
}

export default PrivateRoute;
