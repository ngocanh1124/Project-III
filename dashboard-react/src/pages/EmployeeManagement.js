import React, { useState, useEffect, useCallback, useRef } from 'react';
import { authService, employeeService, organizationService, exportToExcel } from '../services/apiService';
import { FiPlus, FiEdit2, FiTrash2, FiDownload, FiUpload, FiRefreshCw, FiSearch, FiCheckCircle, FiAlertCircle } from 'react-icons/fi';
import { fmtCccd } from '../utils/cccdUtils';

const EMPTY_FORM = { cccd: '', fullName: '', birthday: '', gender: '', department: '', position: '', email: '', phone: '' };

export default function EmployeeManagement() {
  const [employees, setEmployees] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editingCccd, setEditingCccd] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const user = authService.getUser();
  const role = (user.role || '').toLowerCase();
  const isAdmin = ['admin', 'super_admin', 'hr_manager'].includes(role);
  const [totalPages, setTotalPages] = useState(0);
  const [msg, setMsg] = useState(null);
  const importRef = useRef();
  const [importResult, setImportResult] = useState(null); 
  const [importing, setImporting] = useState(false);

  const toast = (text, type = 'success') => {
    setMsg({ text, type });
    setTimeout(() => setMsg(null), 3500);
  };

  const fetchEmployees = useCallback(async () => {
    setLoading(true);
    try {
      const res = await employeeService.getAll({ page, size: 15, search: search || undefined });
      const d = res.data?.data || res.data || {};
      setEmployees(d.content || (Array.isArray(d) ? d : []));
      setTotalPages(d.totalPages || 0);
    } catch { setEmployees([]); }
    finally { setLoading(false); }
  }, [page, search]);

  useEffect(() => { fetchEmployees(); }, [fetchEmployees]);

  const set = (k, v) => setForm((f) => ({ ...f, [k]: v }));

  const openAdd = () => { setForm(EMPTY_FORM); setEditingCccd(null); setShowModal(true); };
  const openEdit = (emp) => {
    setForm({ cccd: emp.cccd, fullName: emp.fullName || '', birthday: emp.birthday || '', gender: emp.gender || '', department: emp.department || '', position: emp.position || '', email: emp.email || '', phone: emp.phone || '' });
    setEditingCccd(emp.cccd);
    setShowModal(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    try {
      const payload = { ...form };
      if (editingCccd) await employeeService.update(editingCccd, payload);
      else await employeeService.create(payload);
      setShowModal(false);
      fetchEmployees();
      toast(editingCccd ? 'Cập nhật nhân viên thành công!' : 'Thêm nhân viên thành công!');
    } catch (err) {
      toast(err?.response?.data?.message || err?.response?.data?.error || 'Lỗi lưu nhân viên', 'error');
    }
  };

  const handleDelete = async (cccd, name) => {
    if (!window.confirm(`Xoá nhân viên "${name}" (${cccd})?`)) return;
    try { await employeeService.delete(cccd); fetchEmployees(); toast('Đã xoá nhân viên'); }
    catch { toast('Lỗi xoá nhân viên', 'error'); }
  };

  
  const handleExport = () => {
    const rows = employees.map((e) => ({
      'Số CCCD': e.cccd,
      'Họ tên': e.fullName,
      'Ngày sinh': e.birthday || '',
      'Giới tính': e.gender || '',
      'Phòng ban': e.department || '',
      'Chức vụ': e.position || '',
      'Email': e.email || '',
      'Điện thoại': e.phone || '',
      'Trạng thái': e.isActive ? 'Hoạt động' : 'Ngừng',
    }));
    exportToExcel(rows, `nhan-vien-${new Date().toISOString().slice(0, 10)}.xlsx`, 'Nhân viên');
    toast('Xuất file Excel thành công!');
  };

  
  const handleImport = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    e.target.value = '';
    setImporting(true);
    try {
      
      let orgId = 1;
      try {
        const orgRes = await organizationService.getAll();
        const orgs = orgRes.data?.data || orgRes.data || [];
        if (Array.isArray(orgs) && orgs.length > 0) orgId = orgs[0].id;
      } catch {  }

      const res = await employeeService.importServerExcel(file, orgId);
      const result = res.data?.data || res.data || {};
      fetchEmployees();
      setImportResult(result);
    } catch (err) {
      toast('Lỗi nhập Excel: ' + (err?.response?.data?.message || err.message), 'error');
    } finally {
      setImporting(false);
    }
  };

  
  const downloadTemplate = () => {
    const template = [{ 'Số CCCD': '034091012345', 'Họ tên': 'Nguyễn Văn A', 'Ngày sinh': '1990-01-01', 'Giới tính': 'Nam', 'Phòng ban': 'Kỹ thuật', 'Chức vụ': 'Kỹ sư', 'Email': 'a@example.com', 'Điện thoại': '0901234567', 'Mã cửa (cách nhau bằng dấu phẩy)': 'GATE_A,GATE_B', 'Giờ vào từ (HH:mm)': '08:00', 'Giờ vào đến (HH:mm)': '17:30', 'Ngày bắt đầu (yyyy-MM-dd)': '', 'Ngày kết thúc (yyyy-MM-dd)': '' }];
    exportToExcel(template, 'mau-nhap-nhan-vien.xlsx', 'Mẫu');
  };

  const displayList = employees;

  return (
    <div className="page">
      <div className="page-header">
        <h1>Quản lý nhân viên ({employees.length})</h1>
        <div className="page-header-actions">
          {isAdmin && (
            <>
              <button className="btn btn-secondary btn-sm" onClick={downloadTemplate} title="Tải mẫu Excel">
                <FiDownload size={13} /> Mẫu Excel
              </button>
              <label className="btn btn-secondary btn-sm" style={{ cursor: importing ? 'not-allowed' : 'pointer', opacity: importing ? 0.6 : 1 }}>
                <FiUpload size={13} /> {importing ? 'Đang nhập...' : 'Nhập Excel'}
                <input type="file" accept=".xlsx,.xls" ref={importRef} onChange={handleImport} style={{ display: 'none' }} disabled={importing} />
              </label>
            </>
          )}
          <button className="btn btn-secondary btn-sm" onClick={handleExport}>
            <FiDownload size={13} /> Xuất Excel
          </button>
          <button className="btn btn-secondary btn-sm" onClick={fetchEmployees} disabled={loading}>
            <FiRefreshCw size={13} className={loading ? 'spin' : ''} />
          </button>
          {isAdmin && (
            <button className="btn btn-primary" onClick={openAdd}>
              <FiPlus size={14} /> Thêm nhân viên
            </button>
          )}
        </div>
      </div>

      {msg && (
        <div style={{ padding: '10px 14px', borderRadius: 8, marginBottom: 14, fontSize: 13, background: msg.type === 'error' ? '#fee2e2' : '#dcfce7', color: msg.type === 'error' ? '#dc2626' : '#16a34a' }}>
          {msg.text}
        </div>
      )}

      <div className="card">
        {}
        <div style={{ display: 'flex', gap: 10, marginBottom: 14, alignItems: 'center' }}>
          <div style={{ position: 'relative', flex: 1, maxWidth: 320 }}>
            <FiSearch style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: '#9ca3af' }} />
            <input
              style={{ paddingLeft: 34, padding: '8px 12px 8px 34px', border: '1px solid #d1d5db', borderRadius: 6, fontSize: 13, width: '100%' }}
              placeholder="Tìm theo tên, CCCD, phòng ban..."
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0); }}
            />
          </div>
        </div>

        {loading ? (
          <div className="loading-container"><div className="spinner spinner-dark" /></div>
        ) : (
          <>
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th>Ảnh</th>
                    <th>Số CCCD</th>
                    <th>Họ tên</th>
                    <th>Ngày sinh</th>
                    <th>Phòng ban</th>
                    <th>Chức vụ</th>
                    <th>Email</th>
                    <th>Điện thoại</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {displayList.length === 0 ? (
                    <tr><td colSpan={10} className="empty-state">Không có nhân viên nào</td></tr>
                  ) : displayList.map((emp) => (
                    <tr key={emp.cccd}>
                      <td>
                        {emp.imageRawUrl ? (
                          <img
                            src={emp.imageRawUrl.startsWith('data:') ? emp.imageRawUrl : `data:image/jpeg;base64,${emp.imageRawUrl}`}
                            alt="backup"
                            style={{ width: 36, height: 36, objectFit: 'cover', borderRadius: '50%', border: '1px solid #e5e7eb' }}
                            onError={(e) => { e.target.style.display = 'none'; }}
                          />
                        ) : (
                          <div className="avatar">{(emp.fullName || '?').charAt(0)}</div>
                        )}
                      </td>
                      <td style={{ fontFamily: 'monospace', fontSize: 12 }}>{fmtCccd(emp.cccd)}</td>
                      <td style={{ fontWeight: 500 }}>{emp.fullName}</td>
                      <td style={{ fontSize: 12 }}>{emp.birthday}</td>
                      <td>{emp.department}</td>
                      <td>{emp.position}</td>
                      <td style={{ fontSize: 12 }}>{emp.email}</td>
                      <td style={{ fontSize: 12 }}>{emp.phone}</td>
                      <td>
                        <span className={`badge badge-${emp.isActive !== false ? 'success' : 'secondary'}`}>
                          {emp.isActive !== false ? 'Hoạt động' : 'Ngừng'}
                        </span>
                      </td>
                      <td>
                        <div className="table-actions">
                          {isAdmin ? (
                            <>
                              <button className="btn-icon" onClick={() => openEdit(emp)} title="Chỉnh sửa">
                                <FiEdit2 size={14} />
                              </button>
                              <button className="btn-icon" onClick={() => handleDelete(emp.cccd, emp.fullName)} title="Xoá" style={{ color: '#dc2626' }}>
                                <FiTrash2 size={14} />
                              </button>
                            </>
                          ) : <span style={{ color: '#6b7280', fontSize: 13 }}>Chỉ xem</span>}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div className="pagination">
                <button className="page-btn" onClick={() => setPage(0)} disabled={page === 0}>«</button>
                <button className="page-btn" onClick={() => setPage((p) => p - 1)} disabled={page === 0}>‹</button>
                <span style={{ fontSize: 13, color: '#6b7280', padding: '0 8px' }}>Trang {page + 1} / {totalPages}</span>
                <button className="page-btn" onClick={() => setPage((p) => p + 1)} disabled={page >= totalPages - 1}>›</button>
                <button className="page-btn" onClick={() => setPage(totalPages - 1)} disabled={page >= totalPages - 1}>»</button>
              </div>
            )}
          </>
        )}
      </div>

      {}
      {showModal && (
        <div className="modal-overlay" onClick={() => setShowModal(false)}>
          <div className="modal" style={{ maxWidth: 600 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>{editingCccd ? `Chỉnh sửa: ${editingCccd}` : 'Thêm nhân viên mới'}</h3>
              <button className="modal-close" onClick={() => setShowModal(false)}>×</button>
            </div>
            <form onSubmit={handleSave}>
              <div className="modal-body">
                <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start' }}>
                  <div style={{ flex: 1 }}>
                    <div className="form-row">
                      <div className="form-group">
                        <label>Số CCCD <span style={{ color: 'red' }}>*</span></label>
                        <input placeholder="12 chữ số" value={form.cccd} onChange={(e) => set('cccd', e.target.value)} required maxLength={12} disabled={!!editingCccd} />
                      </div>
                      <div className="form-group">
                        <label>Họ và tên <span style={{ color: 'red' }}>*</span></label>
                        <input placeholder="Nguyễn Văn A" value={form.fullName} onChange={(e) => set('fullName', e.target.value)} required />
                      </div>
                    </div>
                    <div className="form-row">
                      <div className="form-group">
                        <label>Ngày sinh</label>
                        <input type="date" value={form.birthday} onChange={(e) => set('birthday', e.target.value)} />
                      </div>
                      <div className="form-group">
                        <label>Giới tính</label>
                        <select value={form.gender} onChange={(e) => set('gender', e.target.value)}>
                          <option value="">-- Chọn --</option>
                          <option value="Nam">Nam</option>
                          <option value="Nữ">Nữ</option>
                        </select>
                      </div>
                    </div>
                    <div className="form-row">
                      <div className="form-group">
                        <label>Phòng ban</label>
                        <input placeholder="Kỹ thuật, Kế toán..." value={form.department} onChange={(e) => set('department', e.target.value)} />
                      </div>
                      <div className="form-group">
                        <label>Chức vụ</label>
                        <input placeholder="Nhân viên, Trưởng nhóm..." value={form.position} onChange={(e) => set('position', e.target.value)} />
                      </div>
                    </div>
                    <div className="form-row">
                      <div className="form-group">
                        <label>Email</label>
                        <input type="email" placeholder="email@company.com" value={form.email} onChange={(e) => set('email', e.target.value)} />
                      </div>
                      <div className="form-group">
                        <label>Điện thoại</label>
                        <input placeholder="0901234567" value={form.phone} onChange={(e) => set('phone', e.target.value)} />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div className="modal-footer">
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Huỷ</button>
                <button type="submit" className="btn btn-primary">{editingCccd ? 'Cập nhật' : 'Thêm mới'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {}
      {importResult && (
        <div className="modal-overlay" onClick={() => setImportResult(null)}>
          <div className="modal" style={{ maxWidth: 560 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Kết quả nhập Excel</h3>
              <button className="modal-close" onClick={() => setImportResult(null)}>×</button>
            </div>
            <div className="modal-body">
              <div style={{ display: 'flex', gap: 16, marginBottom: 16 }}>
                <div style={{ flex: 1, background: '#dcfce7', borderRadius: 8, padding: '12px 16px', textAlign: 'center' }}>
                  <FiCheckCircle size={20} color="#16a34a" />
                  <div style={{ fontSize: 22, fontWeight: 700, color: '#16a34a' }}>{importResult.successCount ?? 0}</div>
                  <div style={{ fontSize: 12, color: '#16a34a' }}>Thành công</div>
                </div>
                <div style={{ flex: 1, background: '#fee2e2', borderRadius: 8, padding: '12px 16px', textAlign: 'center' }}>
                  <FiAlertCircle size={20} color="#dc2626" />
                  <div style={{ fontSize: 22, fontWeight: 700, color: '#dc2626' }}>{importResult.failureCount ?? 0}</div>
                  <div style={{ fontSize: 12, color: '#dc2626' }}>Thất bại</div>
                </div>
              </div>
              {importResult.successList?.length > 0 && (
                <div style={{ marginBottom: 12 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: '#16a34a', marginBottom: 6 }}>✓ Đã xử lý thành công:</div>
                  <div style={{ maxHeight: 140, overflowY: 'auto', background: '#f8fafc', borderRadius: 6, padding: '8px 10px' }}>
                    {importResult.successList.map((s, i) => (
                      <div key={i} style={{ fontSize: 12, color: '#374151', padding: '2px 0', borderBottom: '1px solid #e5e7eb' }}>{s}</div>
                    ))}
                  </div>
                </div>
              )}
              {importResult.failureList?.length > 0 && (
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: '#dc2626', marginBottom: 6 }}>✗ Lỗi:</div>
                  <div style={{ maxHeight: 120, overflowY: 'auto', background: '#fff5f5', borderRadius: 6, padding: '8px 10px' }}>
                    {importResult.failureList.map((s, i) => (
                      <div key={i} style={{ fontSize: 12, color: '#dc2626', padding: '2px 0', borderBottom: '1px solid #fecaca' }}>{s}</div>
                    ))}
                  </div>
                </div>
              )}
            </div>
            <div className="modal-footer">
              <button className="btn btn-primary" onClick={() => setImportResult(null)}>Đóng</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

