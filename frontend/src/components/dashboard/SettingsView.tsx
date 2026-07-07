import { useState, useEffect } from 'react';
import { Save, Store, AlertCircle, CheckCircle2, MoreVertical, Edit2, Trash2, Activity, Plus, X } from 'lucide-react';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

interface Connection {
  id: string;
  platform: string;
  shopId: string;
  shopName: string;
  status: string;
  lastErrorMessage?: string;
}

export default function SettingsView() {
  const [connections, setConnections] = useState<Connection[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  
  // Form states
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [shopName, setShopName] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [statusMsg, setStatusMsg] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Actions state
  const [testingId, setTestingId] = useState<string | null>(null);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);

  useEffect(() => {
    fetchConnections();
  }, []);

  const fetchConnections = async () => {
    setIsLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/connections`);
      if (response.ok) {
        const data = await response.json();
        setConnections(data);
      }
    } catch (err) {
      console.error('Failed to fetch connections', err);
    } finally {
      setIsLoading(false);
    }
  };

  const openAddForm = () => {
    setEditingId(null);
    setShopName('');
    setApiKey('');
    setStatusMsg(null);
    setShowForm(true);
    setOpenMenuId(null);
  };

  const openEditForm = (conn: Connection) => {
    setEditingId(conn.id);
    setShopName(conn.shopName || '');
    setApiKey(''); // Don't fetch API key for security, require re-entry if changing
    setStatusMsg(null);
    setShowForm(true);
    setOpenMenuId(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!apiKey && !editingId) {
      setStatusMsg({ type: 'error', message: 'Vui lòng điền API Key' });
      return;
    }

    setIsSubmitting(true);
    setStatusMsg(null);

    const url = editingId 
      ? `${API_BASE_URL}/api/connections/${editingId}`
      : `${API_BASE_URL}/api/connections`;
      
    const method = editingId ? 'PUT' : 'POST';
    
    const payload: any = { platform: 'pancake' };
    if (shopName) payload.shopName = shopName;
    if (apiKey) payload.apiKey = apiKey;

    try {
      const response = await fetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });

      if (!response.ok) {
        const errData = await response.json().catch(() => null);
        throw new Error(errData?.message || 'Lỗi khi lưu kết nối');
      }

      setStatusMsg({ type: 'success', message: 'Lưu kết nối thành công!' });
      setTimeout(() => {
        setShowForm(false);
        fetchConnections();
      }, 1500);
    } catch (error: any) {
      setStatusMsg({ type: 'error', message: error.message });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Bạn có chắc chắn muốn xóa Shop này? Toàn bộ dữ liệu đồng bộ sẽ ngừng.')) return;
    setOpenMenuId(null);
    
    try {
      const response = await fetch(`${API_BASE_URL}/api/connections/${id}`, { method: 'DELETE' });
      if (response.ok) {
        fetchConnections();
      } else {
        alert('Lỗi khi xóa kết nối');
      }
    } catch (err) {
      alert('Không thể kết nối đến máy chủ');
    }
  };

  const handleTestConnection = async (id: string) => {
    setTestingId(id);
    setOpenMenuId(null);
    try {
      const response = await fetch(`${API_BASE_URL}/api/connections/${id}/test`, { method: 'POST' });
      const data = await response.json();
      
      if (response.ok) {
        alert('Kiểm tra thành công! API Key vẫn đang hoạt động tốt.');
      } else {
        alert(`Kiểm tra thất bại: ${data.message || 'Lỗi không xác định'}`);
      }
      fetchConnections(); // Refresh to get updated status
    } catch (err) {
      alert('Không thể kết nối đến máy chủ để test');
    } finally {
      setTestingId(null);
    }
  };

  return (
    <div className="flex-1 flex flex-col gap-6 p-4 overflow-y-auto">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-black tracking-tight text-white font-display mb-1">Quản lý Kết Nối</h2>
          <p className="text-sm text-slate-400">Xem và quản lý các API Key tích hợp nền tảng (Pancake, v.v.).</p>
        </div>
        {!showForm && (
          <button
            onClick={openAddForm}
            className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg font-bold text-sm transition-colors"
          >
            <Plus size={16} />
            Thêm Shop Mới
          </button>
        )}
      </div>

      {showForm ? (
        <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-6 shadow-lg backdrop-blur-sm max-w-2xl">
          <div className="flex items-center justify-between mb-6 pb-6 border-b border-slate-800/80">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-orange-500/10 border border-orange-500/20 flex items-center justify-center text-orange-400">
                <Store size={20} />
              </div>
              <div>
                <h3 className="text-lg font-bold text-white">{editingId ? 'Chỉnh sửa Cấu hình' : 'Thêm Shop Pancake Mới'}</h3>
                <p className="text-xs text-slate-400 mt-0.5">Điền thông tin để hệ thống có thể đồng bộ dữ liệu.</p>
              </div>
            </div>
            <button onClick={() => setShowForm(false)} className="text-slate-400 hover:text-white p-2">
              <X size={20} />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col gap-5">
            {statusMsg && (
              <div className={`p-4 rounded-xl flex items-start gap-3 border ${
                statusMsg.type === 'success' 
                  ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400' 
                  : 'bg-rose-500/10 border-rose-500/20 text-rose-400'
              }`}>
                {statusMsg.type === 'success' ? <CheckCircle2 size={18} className="mt-0.5" /> : <AlertCircle size={18} className="mt-0.5" />}
                <div className="text-sm font-medium">{statusMsg.message}</div>
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <label htmlFor="shopName" className="text-sm font-semibold text-slate-300">
                Tên Shop (Tùy chọn)
              </label>
              <input
                id="shopName"
                type="text"
                value={shopName}
                onChange={(e) => setShopName(e.target.value)}
                placeholder="VD: Cửa hàng Quần Áo Nam"
                className="w-full bg-slate-950/50 border border-slate-800 rounded-xl py-2.5 px-4 text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500/50 transition-all placeholder:text-slate-600"
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="apiKey" className="text-sm font-semibold text-slate-300">
                API Key {editingId && <span className="text-slate-500 font-normal">(Bỏ trống nếu không đổi)</span>} {!editingId && <span className="text-rose-500">*</span>}
              </label>
              <input
                id="apiKey"
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder={editingId ? "Nhập API Key mới..." : "Nhập API Key sinh từ Pancake POS"}
                className="w-full bg-slate-950/50 border border-slate-800 rounded-xl py-2.5 px-4 text-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500/50 transition-all placeholder:text-slate-600"
              />
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="mt-2 flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-500 text-white font-semibold py-2.5 px-6 rounded-xl transition-colors duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <Save size={18} />
              <span>{isSubmitting ? 'Đang lưu...' : 'Lưu Cấu Hình'}</span>
            </button>
          </form>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {isLoading ? (
            <div className="col-span-full text-center py-10 text-slate-500">Đang tải dữ liệu...</div>
          ) : connections.length === 0 ? (
            <div className="col-span-full bg-slate-900/50 border border-slate-800/50 border-dashed rounded-2xl p-10 flex flex-col items-center justify-center text-center">
              <Store size={40} className="text-slate-600 mb-4" />
              <h3 className="text-lg font-bold text-slate-300 mb-1">Chưa có kết nối nào</h3>
              <p className="text-sm text-slate-500 mb-4">Bạn chưa cấu hình shop nào. Hãy thêm một shop mới để bắt đầu đồng bộ.</p>
              <button onClick={openAddForm} className="px-4 py-2 bg-blue-600/20 text-blue-400 border border-blue-500/30 rounded-lg font-semibold hover:bg-blue-600/30 transition-colors">
                Thêm Shop Ngay
              </button>
            </div>
          ) : (
            connections.map(conn => (
              <div key={conn.id} className="bg-slate-900/60 border border-slate-800/80 rounded-xl p-5 shadow-lg relative">
                
                {/* 3 dots menu toggle */}
                <div className="absolute top-4 right-4">
                  <button 
                    onClick={() => setOpenMenuId(openMenuId === conn.id ? null : conn.id)}
                    className="p-1.5 text-slate-400 hover:text-white rounded-md hover:bg-slate-800 transition"
                  >
                    <MoreVertical size={18} />
                  </button>
                  
                  {/* Dropdown */}
                  {openMenuId === conn.id && (
                    <div className="absolute right-0 mt-1 w-48 bg-slate-800 border border-slate-700 rounded-lg shadow-xl z-10 py-1 overflow-hidden">
                      <button 
                        onClick={() => handleTestConnection(conn.id)}
                        disabled={testingId === conn.id}
                        className="w-full text-left px-4 py-2 text-sm text-slate-300 hover:bg-slate-700 hover:text-white flex items-center gap-2"
                      >
                        <Activity size={14} className={testingId === conn.id ? "animate-spin" : ""} />
                        Test Connection
                      </button>
                      <button 
                        onClick={() => openEditForm(conn)}
                        className="w-full text-left px-4 py-2 text-sm text-slate-300 hover:bg-slate-700 hover:text-white flex items-center gap-2"
                      >
                        <Edit2 size={14} />
                        Sửa Cấu Hình
                      </button>
                      <button 
                        onClick={() => handleDelete(conn.id)}
                        className="w-full text-left px-4 py-2 text-sm text-rose-400 hover:bg-rose-500/10 flex items-center gap-2"
                      >
                        <Trash2 size={14} />
                        Xóa Shop
                      </button>
                    </div>
                  )}
                </div>

                <div className="flex items-start gap-4 mb-4">
                  <div className="w-12 h-12 rounded-full bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400 flex-shrink-0">
                    <Store size={24} />
                  </div>
                  <div className="pr-6">
                    <h3 className="font-bold text-white text-lg line-clamp-1" title={conn.shopName}>{conn.shopName || `Shop ${conn.shopId}`}</h3>
                    <div className="text-xs font-mono text-slate-500 mt-1 flex items-center gap-1.5">
                      <span className="uppercase font-semibold tracking-wider text-slate-400 bg-slate-800 px-1.5 py-0.5 rounded">{conn.platform}</span>
                      ID: {conn.shopId}
                    </div>
                  </div>
                </div>
                
                <div className="pt-4 border-t border-slate-800/80">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-slate-400 font-medium uppercase tracking-wider">Trạng thái</span>
                    <span className={`px-2 py-0.5 text-[10px] font-bold rounded-full uppercase tracking-wider ${
                      conn.status === 'active' 
                        ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' 
                        : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                    }`}>
                      {conn.status}
                    </span>
                  </div>
                  {conn.status === 'error' && conn.lastErrorMessage && (
                    <div className="mt-2 text-[11px] text-rose-400/80 leading-snug line-clamp-2" title={conn.lastErrorMessage}>
                      {conn.lastErrorMessage}
                    </div>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
