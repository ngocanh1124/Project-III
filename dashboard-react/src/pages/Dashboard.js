import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Doughnut } from 'react-chartjs-2';
import {
  Chart as ChartJS, CategoryScale, LinearScale, PointElement, ArcElement,
  Title, Tooltip, Legend,
} from 'chart.js';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { attendanceService } from '../services/apiService';
import { FiRefreshCw, FiUsers, FiHardDrive, FiTrendingUp } from 'react-icons/fi';
import { Link } from 'react-router-dom';
import './Dashboard.css';

ChartJS.register(CategoryScale, LinearScale, PointElement, ArcElement, Title, Tooltip, Legend);

const fmt = (d) => new Date(d).toLocaleString('vi-VN');
const fmtScore = (s) => s != null ? `${(s * 100).toFixed(1)}%` : '-';

function Dashboard() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const stompClientRef = useRef(null);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const statsRes = await attendanceService.getOverallStats();
      setStats(statsRes.data?.data || statsRes.data || null);
    } catch (err) {
      setError('Không thể tải dữ liệu. Kiểm tra kết nối server.');
    } finally {
      setLoading(false);
    }
  }, []);

  
  useEffect(() => {
    const rawBase = process.env.REACT_APP_API_URL || 'http://localhost:8080';
    const WS_BASE = rawBase.startsWith('http') ? new URL(rawBase).origin : rawBase;
    const client = new Client({
      webSocketFactory: () => new SockJS(`${WS_BASE}/ws-attendance`),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/topic/attendance', (message) => {
          try {
            attendanceService.getDashboardStats()
              .then((res) => setStats(res.data?.data || res.data || null))
              .catch(() => {});
          } catch (_) {}
        });
      },
    });
    client.activate();
    stompClientRef.current = client;
    return () => { client.deactivate(); };
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const statCards = [
    { label: 'Thiết bị hoạt động', value: stats?.onlineDevices ?? '-', icon: FiHardDrive, color: '', link: '/devices' },
    { label: 'Nhân viên hoạt động', value: stats?.activeEmployees ?? '-', icon: FiUsers, color: '', link: '/employees' },
    { label: 'Tỉ lệ xác thực', value: stats?.successRate != null ? `${stats.successRate.toFixed(1)}%` : '-', icon: FiTrendingUp, color: '' },
  ];

  const doughnutData = {
    labels: ['Thành công', 'Từ chối'],
    datasets: [{
      data: [stats?.successfulAttempts || 0, stats?.deniedAttempts || 0],
      backgroundColor: ['#16a34a', '#dc2626'],
      borderWidth: 0,
    }],
  };

  const chartOpts = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { font: { size: 12 } } } },
  };

  return (
    <div className="page">
      <div className="page-header">
        <h1>Tổng quan hệ thống</h1>
        <button className="btn btn-secondary" onClick={fetchData} disabled={loading}>
          <FiRefreshCw size={14} className={loading ? 'spin' : ''} /> Làm mới
        </button>
      </div>

      {error && <div style={{ padding: '12px 16px', background: '#fee2e2', color: '#dc2626', borderRadius: 8, marginBottom: 16, fontSize: 13 }}>{error}</div>}

      {}
      <div className="stats-grid">
        {statCards.map((c) => {
          const Icon = c.icon;
          const card = (
            <div key={c.label} className={`stat-card ${c.color}${c.link ? ' stat-card-link' : ''}`}>
              <Icon style={{ float: 'right', fontSize: 24, opacity: 0.2, marginTop: 2 }} />
              <div className="value">{c.value}</div>
              <div className="label">{c.label}</div>
            </div>
          );
          return c.link ? (
            <Link key={c.label} to={c.link} style={{ textDecoration: 'none' }}>{card}</Link>
          ) : card;
        })}
      </div>

      {}
      <div className="charts-grid">
        <div className="chart-card">
          <h3>Phân bố kết quả điểm danh</h3>
          <div style={{ height: 300 }}>
            <Doughnut data={doughnutData} options={{ ...chartOpts, maintainAspectRatio: false }} />
          </div>
        </div>
      </div>
    </div>
  );
}

export default Dashboard;

