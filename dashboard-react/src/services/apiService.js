import axios from 'axios';
import * as XLSX from 'xlsx';

const DEFAULT_API_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export const getApiBaseUrl = () =>
  localStorage.getItem('apiUrl') || DEFAULT_API_URL;

['auth_token', 'auth_user', 'auth_basic'].forEach((k) => localStorage.removeItem(k));

export const apiClient = axios.create({
  baseURL: getApiBaseUrl(),
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use(
  (config) => {
    config.baseURL = getApiBaseUrl();
    const token = sessionStorage.getItem('auth_token');
    const basic = sessionStorage.getItem('auth_basic');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    } else if (basic) {
      config.headers.Authorization = `Basic ${basic}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('auth_token');
      sessionStorage.removeItem('auth_user');
      sessionStorage.removeItem('auth_basic');
      window.location.href = '/login';
      
      
      return new Promise(() => {});
    }
    return Promise.reject(error);
  }
);

export const attendanceService = {
  recordAttendance: (data) => apiClient.post('/api/v2/attendance/record', data),
  getHistory: (params) => apiClient.get('/api/v2/attendance/history', { params }),
  getDashboardStats: (date) =>
    apiClient.get('/api/v2/attendance/dashboard/stats', { params: { date } }),
  getOverallStats: () =>
    apiClient.get('/api/v2/attendance/dashboard/stats/overall'),
  getDeviceStats: (deviceCode) =>
    apiClient.get(`/api/v2/attendance/devices/${deviceCode}/stats`),
  remoteUnlock: (deviceCode, reason) =>
    apiClient.post(`/api/v2/attendance/remote-unlock/${deviceCode}`, { reason }),
  instantUnlock: (deviceCode, reason) =>
    apiClient.post(`/api/v2/attendance/instant-unlock/${deviceCode}`, { reason }),
  getEmployeeAttendance: (cccd, month) =>
    apiClient.get(`/api/v2/attendance/employee/${cccd}/summary`, { params: { month } }),
  getSpecialCases: (params) =>
    apiClient.get('/api/v2/attendance/special-cases', { params }),
  getViolations: (params) =>
    apiClient.get('/api/v2/attendance/violations', { params }),
  exportReport: (params) =>
    apiClient.get('/api/v2/attendance/export', { params, responseType: 'blob' }),
  exportByMonth: (month) =>
    apiClient.get(`/api/v2/attendance/export/${month}`, { responseType: 'blob' }),
  exportByDeviceMonth: (deviceCode, month) =>
    apiClient.get(`/api/v2/attendance/export/${deviceCode}/${month}`, { responseType: 'blob' }),
  approvePendingAccess: (logId) =>
    apiClient.post(`/api/v2/attendance/${logId}/approve`),
};

export const authService = {
  login: async (username, password) => {
    try {
      const resp = await apiClient.post('/api/auth/login', { username, password });
      const data = resp.data || {};
      const token = data.token || data.accessToken || data.access_token;
      if (token) {
        const user = data.user || { username };
        sessionStorage.setItem('auth_token', token);
        sessionStorage.setItem('auth_user', JSON.stringify(user));
        sessionStorage.removeItem('auth_basic');
        return { success: true, user };
      }
      throw new Error('No token returned from server');
    } catch (err) {
      sessionStorage.removeItem('auth_token');
      sessionStorage.removeItem('auth_user');
      throw err;
    }
  },

  logout: () => {
    sessionStorage.removeItem('auth_token');
    sessionStorage.removeItem('auth_basic');
    sessionStorage.removeItem('auth_user');
    window.location.href = '/login';
  },

  getUser: () => {
    try {
      const user = sessionStorage.getItem('auth_user');
      return user ? JSON.parse(user) : null;
    } catch {
      return null;
    }
  },

  
  isLoggedIn: () => {
    const token = sessionStorage.getItem('auth_token');
    const user = authService.getUser();
    return !!(token && user);
  },

  forgotPassword: (email) => apiClient.post('/api/auth/forgot-password', { email }),

  register: async (payload) => apiClient.post('/api/auth/register', payload),

  verifyOtp: async (email, otp) =>
    apiClient.post('/api/auth/verify-otp', { email, otpCode: otp }),

  changePassword: (data) => apiClient.post('/api/auth/change-password', data),

  changeMyPassword: (data) => apiClient.post('/api/auth/change-my-password', data),

  updateProfile: (data) => apiClient.put('/api/auth/profile', data),
};

export const employeeService = {
  getAll: (params) => apiClient.get('/api/v2/employees', { params }),

  getById: (cccd) => apiClient.get(`/api/v2/employees/${cccd}`),
  create: (data) => apiClient.post('/api/v2/employees', data),
  update: (cccd, data) => apiClient.put(`/api/v2/employees/${cccd}`, data),
  delete: (cccd) => apiClient.delete(`/api/v2/employees/${cccd}`),
  updateStatus: (cccd, isActive) =>
    apiClient.patch(`/api/v2/employees/${cccd}/status`, { isActive }),
  getByDepartment: (department) =>
    apiClient.get('/api/v2/employees/search/department', { params: { department } }),
  exportExcel: (params) =>
    apiClient.get('/api/v2/employees/export', { params, responseType: 'blob' }),

  
  uploadBackupPhoto: (cccd, imageBase64) =>
    apiClient.put(`/api/v2/employees/${cccd}/backup-photo`, { imageBase64 }),

  
  importServerExcel: (file, organizationId) => {
    const form = new FormData();
    form.append('file', file);
    return apiClient.post(`/api/v2/employees/import?organizationId=${organizationId}`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  
  importFromLocalExcel: async (file) => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const wb = XLSX.read(e.target.result, { type: 'binary' });
          const ws = wb.Sheets[wb.SheetNames[0]];
          const rows = XLSX.utils.sheet_to_json(ws, { defval: '' });
          resolve(rows);
        } catch (err) {
          reject(err);
        }
      };
      reader.readAsBinaryString(file);
    });
  },
};

export const deviceService = {
  getAll: (params) => apiClient.get('/api/v2/devices', { params }),
  getById: (id) => apiClient.get(`/api/v2/devices/${id}`),
  getByDeviceCode: (deviceCode) => apiClient.get('/api/v2/devices', { params: { search: deviceCode, size: 100, t: Date.now() } }),
  create: (data) => apiClient.post('/api/v2/devices', data),
  update: (id, data) => apiClient.put(`/api/v2/devices/${id}`, data),
  delete: (id) => apiClient.delete(`/api/v2/devices/${id}`),
  getStats: (deviceCode) => apiClient.get(`/api/v2/attendance/devices/${deviceCode}/stats`),
  remoteUnlock: (deviceCode, reason) =>
    apiClient.post(`/api/v2/attendance/remote-unlock/${deviceCode}`, { reason }),
  instantUnlock: (deviceCode, reason) =>
    apiClient.post(`/api/v2/attendance/instant-unlock/${deviceCode}`, { reason }),
  syncEmployees: (deviceCode) =>
    apiClient.post(`/api/v1/device/${deviceCode}/sync`),
};

export const permissionService = {
  getByEmployee: (employeeId) =>
    apiClient.get('/api/v2/permissions', { params: { employeeId } }),
  getByDevice: (deviceId) =>
    apiClient.get('/api/v2/permissions', { params: { deviceId } }),
  create: (data) => apiClient.post('/api/v2/permissions', data),
  update: (id, data) => apiClient.put(`/api/v2/permissions/${id}`, data),
  delete: (id) => apiClient.delete(`/api/v2/permissions/${id}`),
  syncAllDevices: () => apiClient.post('/api/v2/permissions/sync-all-devices'),
};

export const organizationService = {
  getAll: () => apiClient.get('/api/v2/organizations'),
  getById: (id) => apiClient.get(`/api/v2/organizations/${id}`),
  create: (data) => apiClient.post('/api/v2/organizations', data),
  update: (id, data) => apiClient.put(`/api/v2/organizations/${id}`, data),
};

export const testDataService = {
  populateAll: () => apiClient.post('/api/v2/test/populate-all'),
  populateEmployees: (count) =>
    apiClient.post('/api/v2/test/populate-employees', null, { params: { count } }),
  populateDevices: () => apiClient.post('/api/v2/test/populate-devices'),
  populatePermissions: () => apiClient.post('/api/v2/test/populate-permissions'),
  populateAttendance: (count) =>
    apiClient.post('/api/v2/test/populate-attendance', null, { params: { count } }),
  clearAll: () => apiClient.delete('/api/v2/test/clear-all'),
  getStats: () => apiClient.get('/api/v2/test/stats'),
};

export function exportToExcel(data, filename, sheetName = 'Sheet1') {
  const ws = XLSX.utils.json_to_sheet(data);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, sheetName);
  XLSX.writeFile(wb, filename);
}

export default apiClient;
