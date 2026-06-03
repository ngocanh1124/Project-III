import React, { useState } from 'react';
import { testDataService } from '../services/apiService';
import { FiPlay, FiTrash2 } from 'react-icons/fi';

function TestData() {
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState(null);
  const [stats, setStats] = useState(null);

  const handlePopulateAll = async () => {
    if (!window.confirm('This will populate the database with test data. Continue?')) return;

    try {
      setLoading(true);
      await testDataService.populateAll();
      setStatus({ type: 'success', message: 'Test data populated successfully!' });
      fetchStats();
    } catch (error) {
      setStatus({ type: 'error', message: 'Failed to populate test data' });
    } finally {
      setLoading(false);
    }
  };

  const handleClearAll = async () => {
    if (!window.confirm('This will DELETE ALL data. This cannot be undone. Continue?')) return;

    try {
      setLoading(true);
      await testDataService.clearAll();
      setStatus({ type: 'success', message: 'All test data cleared!' });
      fetchStats();
    } catch (error) {
      setStatus({ type: 'error', message: 'Failed to clear test data' });
    } finally {
      setLoading(false);
    }
  };

  const fetchStats = async () => {
    try {
      const response = await testDataService.getStats();
      setStats(response.data.data);
    } catch (error) {
      console.error('Error fetching stats:', error);
    }
  };

  React.useEffect(() => {
    fetchStats();
  }, []);

  return (
    <div className="page">
      <h1>Test Data Management</h1>

      {status && (
        <div className={`alert alert-${status.type}`}>{status.message}</div>
      )}

      <div className="card">
        <h2>Database Statistics</h2>
        <div className="stats-grid">
          <div className="stat">
            <div className="stat-value">{stats?.employees || 0}</div>
            <div className="stat-label">Employees</div>
          </div>
          <div className="stat">
            <div className="stat-value">{stats?.devices || 0}</div>
            <div className="stat-label">Devices</div>
          </div>
          <div className="stat">
            <div className="stat-value">{stats?.permissions || 0}</div>
            <div className="stat-label">Permissions</div>
          </div>
          <div className="stat">
            <div className="stat-value">{stats?.attendanceRecords || 0}</div>
            <div className="stat-label">Attendance Records</div>
          </div>
        </div>
      </div>

      <div className="card">
        <h2>Actions</h2>
        <div className="button-group">
          <button
            className="btn btn-primary"
            onClick={handlePopulateAll}
            disabled={loading}
          >
            <FiPlay /> Populate Test Data
          </button>

          <button
            className="btn btn-danger"
            onClick={handleClearAll}
            disabled={loading}
          >
            <FiTrash2 /> Clear All Data
          </button>
        </div>
      </div>

      <div className="card info">
        <h3>Test Data Includes:</h3>
        <ul>
          <li>50 test employees with sample data</li>
          <li>6 devices (doors/gates)</li>
          <li>Access permissions for employees to devices</li>
          <li>200 sample attendance records</li>
        </ul>
      </div>

      <style>{`
        .stats-grid {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
          gap: 16px;
          margin-top: 16px;
        }

        .stat {
          background: #f8f9fa;
          padding: 16px;
          border-radius: 6px;
          text-align: center;
        }

        .stat-value {
          font-size: 28px;
          font-weight: 700;
          color: #667eea;
        }

        .stat-label {
          font-size: 12px;
          color: #666;
          margin-top: 8px;
        }

        .button-group {
          display: flex;
          gap: 12px;
          margin-top: 16px;
          flex-wrap: wrap;
        }

        .card.info {
          background: #e7f3ff;
          border-left: 4px solid #0099ff;
        }

        .card.info ul {
          margin-left: 20px;
        }

        .card.info li {
          margin: 8px 0;
          color: #333;
        }
      `}</style>
    </div>
  );
}

export default TestData;
