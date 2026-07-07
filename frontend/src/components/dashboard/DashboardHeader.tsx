import { useState, useEffect } from 'react';
import FullscreenButton from './FullscreenButton';
import { formatTime, getMinutesDifference } from '../../utils/time';
import { AlertTriangle, RefreshCw, Globe, ChevronDown, Store } from 'lucide-react';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

interface DashboardHeaderProps {
  lastUpdatedAt: string;
  isRefreshing: boolean;
  onRefresh: () => void;
  error?: string | null;
  activeTab: 'dashboard' | 'settings';
  onTabChange: (tab: 'dashboard' | 'settings') => void;
  selectedShopId: string;
  onShopChange: (shopId: string) => void;
  selectedDate: string;
  onDateChange: (date: string) => void;
  orderStatusCounts?: Record<string, number>;
}

interface Shop {
  id: string;
  shopId: string;
  shopName: string;
}

export default function DashboardHeader({
  lastUpdatedAt,
  isRefreshing,
  onRefresh,
  error,
  activeTab,
  onTabChange,
  selectedShopId,
  onShopChange,
  selectedDate,
  onDateChange,
  orderStatusCounts
}: DashboardHeaderProps) {
  const [time, setTime] = useState(new Date());
  const [shops, setShops] = useState<Shop[]>([]);
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  useEffect(() => {
    // Fetch shops for selector
    const fetchShops = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/connections`);
        if (response.ok) {
          const data = await response.json();
          setShops(data);
          if (data.length > 0 && selectedShopId === 'all') {
            onShopChange(data[0].shopId);
          }
        }
      } catch (err) {
        console.error('Failed to fetch shops:', err);
      }
    };
    fetchShops();
  }, []);

  useEffect(() => {
    const timer = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  const diffMin = getMinutesDifference(lastUpdatedAt);
  const isStale = diffMin >= 15;

  return (
    <header className="relative z-50 w-full rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md px-5 py-3 2xl:px-5 2xl:py-3 flex flex-col md:flex-row md:items-center justify-between gap-3 shadow-lg shadow-slate-950/20">
      {/* Navigation Pill */}
      <div className="flex items-center bg-[#1a1b1e] rounded-full p-1.5 shadow-inner">
        {/* Planet Icon */}
        <div className="w-8 h-8 rounded-full bg-white text-black flex items-center justify-center shadow-sm">
          <Globe size={18} strokeWidth={2.5} />
        </div>
        
        {/* Nav Links */}
        <div className="flex items-center gap-1 px-4">
          <button 
            onClick={() => onTabChange('dashboard')}
            className={`px-3 py-1.5 rounded-full text-sm font-semibold transition-colors duration-200 ${activeTab === 'dashboard' ? 'text-white' : 'text-slate-400 hover:text-slate-200'}`}
          >
            Dashboard
          </button>
          <button 
            onClick={() => onTabChange('settings')}
            className={`px-3 py-1.5 rounded-full text-sm font-semibold transition-colors duration-200 ${activeTab === 'settings' ? 'text-white' : 'text-slate-400 hover:text-slate-200'}`}
          >
            Cài đặt
          </button>
        </div>

        {/* User Pill */}
        <div className="ml-2 bg-white text-black px-4 py-1.5 rounded-full text-sm font-bold shadow-sm hidden sm:block">
          admin@mdata.io
        </div>
      </div>

      {/* Warnings / Alerts */}
      <div className="flex flex-col md:flex-row items-center gap-2">
        {error && (
          <div className="flex items-center gap-1.5 px-3 py-1 rounded-lg text-[10px] 2xl:text-sm font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <AlertTriangle size={12} className="animate-pulse" />
            <span>Mất kết nối API</span>
          </div>
        )}
        
        {isStale && (
          <div className="flex items-center gap-1.5 px-3 py-1 rounded-lg text-[10px] 2xl:text-sm font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20 animate-pulse">
            <AlertTriangle size={12} />
            <span>Dữ liệu chậm ({diffMin} phút trước)</span>
          </div>
        )}

        <div className="flex items-center gap-4">
          {/* Order Status Indicators (to the left of Shop Selector) */}
          {orderStatusCounts && (
            <div className="flex items-center gap-2 px-3 py-1.5 bg-slate-800/30 border border-slate-700/50 rounded-lg text-[10px] sm:text-xs">
              <span className="text-slate-400 font-medium hidden lg:inline">Trạng thái đơn:</span>
              <span className="text-amber-400 font-bold" title="Chờ xử lý">Chờ: {orderStatusCounts.pending}</span>
              <span className="text-slate-700">|</span>
              <span className="text-blue-400 font-bold" title="Đang giao">Giao: {orderStatusCounts.processing}</span>
              <span className="text-slate-700">|</span>
              <span className="text-emerald-400 font-bold" title="Hoàn thành">Xong: {orderStatusCounts.completed}</span>
              <span className="text-slate-700">|</span>
              <span className="text-rose-400/80 font-medium" title="Đã hủy">Hủy: {orderStatusCounts.cancelled}</span>
              <span className="text-slate-700">|</span>
              <span className="text-purple-400/80 font-medium" title="Đã hoàn">Hoàn: {orderStatusCounts.refunded}</span>
              {orderStatusCounts.pending === 0 && orderStatusCounts.processing === 0 && (
                <span className="ml-1 px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 font-bold text-[9px] flex items-center gap-1">
                  <span className="w-1 h-1 rounded-full bg-emerald-400 animate-pulse" />
                  Chốt
                </span>
              )}
            </div>
          )}

          {/* Shop Selector */}
          <div className="relative">
            <button
              onClick={() => setIsDropdownOpen(!isDropdownOpen)}
              className="flex items-center justify-between gap-2 px-4 py-2 bg-slate-800 border border-slate-700 hover:bg-slate-700 hover:border-slate-600 text-white rounded-lg text-sm font-semibold transition-all duration-200 min-w-[160px] shadow-sm"
            >
              <div className="flex items-center gap-2 truncate">
                <Store size={16} className="text-indigo-400" />
                <span className="truncate max-w-[120px]">
                  {selectedShopId === 'all' 
                    ? 'Đang tải...' 
                    : shops.find(s => s.shopId === selectedShopId)?.shopName || selectedShopId}
                </span>
              </div>
              <ChevronDown size={14} className={`text-slate-400 transition-transform ${isDropdownOpen ? 'rotate-180' : ''}`} />
            </button>
            
            {isDropdownOpen && (
              <>
                <div 
                  className="fixed inset-0 z-40" 
                  onClick={() => setIsDropdownOpen(false)}
                />
                <div className="absolute top-full right-0 mt-2 w-[240px] bg-slate-800 border border-slate-700 rounded-xl shadow-2xl overflow-hidden z-50 py-1 origin-top-right animate-in fade-in slide-in-from-top-2">
                  <div className="max-h-[300px] overflow-y-auto custom-scrollbar">
                    {shops.length === 0 && (
                      <div className="px-4 py-3 text-sm text-slate-400 text-center">Chưa có shop nào</div>
                    )}
                    {shops.map(shop => (
                      <button
                        key={shop.id}
                        onClick={() => {
                          onShopChange(shop.shopId);
                          setIsDropdownOpen(false);
                        }}
                        className={`w-full text-left px-4 py-2.5 text-sm font-medium transition-colors flex items-center justify-between ${selectedShopId === shop.shopId ? 'bg-indigo-500/10 text-indigo-400' : 'text-slate-300 hover:bg-slate-700/50 hover:text-white'}`}
                      >
                        <span className="truncate">{shop.shopName || shop.shopId}</span>
                        {selectedShopId === shop.shopId && <div className="w-1.5 h-1.5 rounded-full bg-indigo-500" />}
                      </button>
                    ))}
                  </div>
                </div>
              </>
            )}
          </div>

          {/* Date Picker & Clock */}
          {(() => {
            const d = new Date();
            const utc = d.getTime() + d.getTimezoneOffset() * 60000;
            const vnTime = new Date(utc + 3600000 * 7);
            const todayStr = vnTime.toISOString().split('T')[0];

            return (
              <div className="flex items-center gap-3 bg-slate-800/50 border border-slate-700/80 px-3 py-1.5 rounded-lg shadow-sm">
                <input 
                  type="date"
                  value={selectedDate}
                  onChange={(e) => onDateChange(e.target.value)}
                  max={todayStr}
                  className="bg-transparent border-none text-white text-xs font-semibold focus:outline-none focus:ring-0 cursor-pointer [color-scheme:dark]"
                />
                {selectedDate === todayStr && (
                  <div className="border-l border-slate-700 pl-3 text-right">
                    <div className="text-xs font-bold text-white font-mono leading-none">
                      {formatTime(time.toISOString())}
                    </div>
                  </div>
                )}
              </div>
            );
          })()}

          <div className="flex items-center gap-1.5">
            <button
              onClick={onRefresh}
              disabled={isRefreshing}
              className="p-2 rounded-lg bg-slate-800/50 hover:bg-slate-700/50 border border-slate-700 text-slate-300 hover:text-white transition disabled:opacity-50"
              title="Cập nhật ngay"
            >
              <RefreshCw className={isRefreshing ? 'animate-spin' : ''} size={18} />
            </button>

            <FullscreenButton />
          </div>
        </div>
      </div>
    </header>
  );
}
