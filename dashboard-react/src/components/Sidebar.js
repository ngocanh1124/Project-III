import React, { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { FiMenu, FiX, FiHome, FiUsers, FiHardDrive, FiSettings, FiShield, FiLogOut, FiUserCheck } from 'react-icons/fi';
import { authService } from '../services/apiService';
import './Sidebar.css';

const menuItems = [
  { path: '/',          label: 'Tổng quan', icon: FiHome },
  { path: '/employees', label: 'Nhân viên', icon: FiUsers },
  { path: '/devices',   label: 'Thiết bị',  icon: FiHardDrive },
  { path: '/settings',  label: 'Cài đặt',   icon: FiSettings,  adminOnly: true },
  { path: '/users',     label: 'Tài khoản', icon: FiUserCheck, adminOnly: true },
];

function getMenuItems(role) {
  const isAdminLevel = ['admin', 'super_admin'].includes(role);
  if (role === 'viewer')
    return menuItems.filter((i) => ['/', '/devices'].includes(i.path));
  if (role === 'hr_manager')
    return menuItems.filter((i) => ['/', '/employees'].includes(i.path));
  if (role === 'operator')
    return menuItems.filter((i) => ['/', '/devices'].includes(i.path));
  
  return isAdminLevel ? menuItems : menuItems.filter((i) => !i.adminOnly);
}

function Sidebar({ mobileOpen, onMobileClose }) {
  const [isOpen, setIsOpen] = useState(true);
  const location = useLocation();
  const user = authService.getUser();
  const initials = (user.fullName || user.username || 'AD').slice(0, 2).toUpperCase();
  const role = (user.role || '').toLowerCase();

  const isActive = (path) => path === '/'
    ? location.pathname === '/'
    : location.pathname.startsWith(path);

  const handleNavClick = () => {
    if (mobileOpen) onMobileClose();
  };

  return (
    <>
      <div
        className={`sidebar-backdrop${mobileOpen ? ' visible' : ''}`}
        onClick={onMobileClose}
        aria-hidden="true"
      />
      <aside className={`sidebar ${isOpen ? 'open' : 'closed'} ${mobileOpen ? 'mobile-open' : ''}`}>
      <div className="sidebar-header">
        {isOpen && (
          <div className="sidebar-brand">
            <FiShield className="brand-icon" />
            <span className="sidebar-title">CCCD Access</span>
          </div>
        )}
        <button className="toggle-btn" onClick={() => setIsOpen(!isOpen)} aria-label="Toggle sidebar">
          {isOpen ? <FiX size={18} /> : <FiMenu size={18} />}
        </button>
      </div>

      <nav className="sidebar-nav">
        <ul className="menu-list">
          {getMenuItems(role).map((item) => {
            const Icon = item.icon;
            return (
              <li key={item.path}>
                <Link
                  to={item.path}
                  className={`menu-item ${isActive(item.path) ? 'active' : ''}`}
                  title={!isOpen ? item.label : undefined}
                  onClick={handleNavClick}
                >
                  <Icon className="menu-icon" size={18} />
                  {isOpen && <span className="menu-label">{item.label}</span>}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="sidebar-footer">
        {isOpen ? (
          <div className="user-info">
            <div className="user-avatar">{initials}</div>
            <div className="user-details">
              <p className="user-name">{user.fullName || user.username || 'Admin'}</p>
              <p className="user-role">{{
                viewer: 'Chỉ xem',
                hr_manager: 'Quản lý HR',
                operator: 'Vận hành',
                admin: 'Quản trị',
                super_admin: 'Super Admin',
              }[role] || user.role || 'Người dùng'}</p>
            </div>
          </div>
        ) : (
          <div className="user-avatar" style={{ margin: '0 auto' }}>{initials}</div>
        )}
        <button
          className="logout-btn"
          onClick={authService.logout}
          title="Đăng xuất"
        >
          <FiLogOut size={16} />
          {isOpen && <span>Đăng xuất</span>}
        </button>
      </div>
    </aside>
    </>
  );
}

export default Sidebar;
