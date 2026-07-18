import React, { useState } from 'react';
import { BrowserRouter as Router, Routes, Route, useLocation } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import TopBar from './components/TopBar';
import Dashboard from './pages/Dashboard';
import AttendanceHistory from './pages/AttendanceHistory';
import EmployeeManagement from './pages/EmployeeManagement';
import DeviceManagement from './pages/DeviceManagement';
import DeviceDashboard from './pages/DeviceDashboard';
import Reports from './pages/Reports';
import Settings from './pages/Settings';
import UserManagement from './pages/UserManagement';
import Login from './pages/Login';
import ForgotPassword from './pages/ForgotPassword';
import Register from './pages/Register';
import VerifyOtp from './pages/VerifyOtp';
import ResetPassword from './pages/ResetPassword';
import PrivateRoute from './components/PrivateRoute';
import './App.css';

const AUTH_PATHS = ['/login', '/register', '/verify', '/forgot-password', '/reset-password'];

function AppContent() {
  const location = useLocation();
  const isAuthPage = AUTH_PATHS.some((p) => location.pathname.startsWith(p));
  const [mobileOpen, setMobileOpen] = useState(false);

  if (isAuthPage) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/verify" element={<VerifyOtp />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
      </Routes>
    );
  }

  return (
    <div className="app-container">
      <Sidebar mobileOpen={mobileOpen} onMobileClose={() => setMobileOpen(false)} />
      <div className="main-content">
        <TopBar onMenuClick={() => setMobileOpen(o => !o)} />
        <div className="page-content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/attendance" element={<AttendanceHistory />} />
            <Route path="/employees" element={<EmployeeManagement />} />
            <Route path="/devices" element={<DeviceManagement />} />
            <Route path="/devices/:deviceCode" element={<DeviceDashboard />} />
            <Route path="/reports" element={<Reports />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/users" element={<UserManagement />} />
          </Routes>
        </div>
      </div>
    </div>
  );
}

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/verify" element={<VerifyOtp />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route
          path="/*"
          element={
            <PrivateRoute>
              <AppContent />
            </PrivateRoute>
          }
        />
      </Routes>
    </Router>
  );
}

export default App;
