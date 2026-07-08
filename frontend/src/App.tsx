import { useState, useEffect, useRef } from 'react';
import DashboardPage from './components/dashboard/DashboardPage';
import DashboardSkeleton from './components/dashboard/DashboardSkeleton';
import { DashboardData } from './types/dashboard';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const POLLING_INTERVAL_MS = Number(import.meta.env.VITE_POLLING_INTERVAL_MS || 30000);

export default function App() {
  const getTodayString = () => {
    const d = new Date();
    const utc = d.getTime() + d.getTimezoneOffset() * 60000;
    const vnTime = new Date(utc + 3600000 * 7);
    return vnTime.toISOString().split('T')[0];
  };

  const getInitialShopId = () => {
    const params = new URLSearchParams(window.location.search);
    return params.get('shopId') || 'all';
  };

  const getInitialDate = () => {
    const params = new URLSearchParams(window.location.search);
    const dateParam = params.get('date');
    if (dateParam && /^\d{4}-\d{2}-\d{2}$/.test(dateParam)) {
      return dateParam;
    }
    return getTodayString();
  };

  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  const [selectedShopId, setSelectedShopId] = useState<string>(getInitialShopId());
  const [selectedDate, setSelectedDate] = useState<string>(getInitialDate());
  const lastValidDataRef = useRef<DashboardData | null>(null);

  // We use refs for selectedShopId and selectedDate so async fetches always check if they match current active target
  const shopIdRef = useRef(selectedShopId);
  const dateRef = useRef(selectedDate);

  const updateUrlParams = (shopId: string, date: string) => {
    const params = new URLSearchParams(window.location.search);
    params.set('shopId', shopId);
    params.set('date', date);
    const newRelativePathQuery = window.location.pathname + '?' + params.toString();
    window.history.replaceState(null, '', newRelativePathQuery);
  };

  // Fetch metrics handler
  const fetchMetrics = async (
    forceRefreshIndicator = false,
    targetShopId = selectedShopId,
    targetDate = selectedDate,
    refreshFromServer = forceRefreshIndicator
  ) => {
    if (forceRefreshIndicator) setIsRefreshing(true);
    
    // API Mode
    try {
      const response = await fetch(`${API_BASE_URL}/api/dashboard/live?shopId=${targetShopId}&date=${targetDate}${refreshFromServer ? '&refresh=true' : ''}`);
      if (!response.ok) {
        throw new Error(`API returned HTTP ${response.status}`);
      }
      const json: DashboardData = await response.json();
      
      // Only commit data if this fetch matches current user selection
      if (targetShopId === shopIdRef.current && targetDate === dateRef.current) {
        setData(json);
        lastValidDataRef.current = json;
        setError(null);
      }
    } catch (err: any) {
      console.warn('API connection error:', err.message);
      
      if (targetShopId === shopIdRef.current && targetDate === dateRef.current) {
        if (lastValidDataRef.current) {
          // Fallback: keep previous valid data but indicate connection status warning
          setError('Kết nối API gián đoạn. Đang hiển thị dữ liệu cũ.');
          setData(lastValidDataRef.current);
        } else {
          // No cached data exists yet: display connection error block on skeleton
          setError(`Đang chờ dữ liệu từ API. Không thể kết nối tới backend: ${err.message}`);
          setData(null);
        }
      }
    } finally {
      if (targetShopId === shopIdRef.current && targetDate === dateRef.current) {
        setLoading(false);
        setIsRefreshing(false);
      }
    }
  };

  const handleShopChange = (shopId: string) => {
    setSelectedShopId(shopId);
    shopIdRef.current = shopId; // Update synchronously
    updateUrlParams(shopId, selectedDate);
    setLoading(true);
    setData(null);
    fetchMetrics(false, shopId, selectedDate);
  };

  const handleDateChange = (date: string) => {
    setSelectedDate(date);
    dateRef.current = date; // Update synchronously
    updateUrlParams(selectedShopId, date);
    setLoading(true);
    setData(null);
    fetchMetrics(false, selectedShopId, date);
  };

  // Keep refs in sync with state changes (e.g. from polling or initial mount)
  useEffect(() => {
    shopIdRef.current = selectedShopId;
  }, [selectedShopId]);

  useEffect(() => {
    dateRef.current = selectedDate;
  }, [selectedDate]);

  useEffect(() => {
    updateUrlParams(shopIdRef.current, dateRef.current);
    fetchMetrics(false, shopIdRef.current, dateRef.current);
    const interval = setInterval(() => fetchMetrics(false, shopIdRef.current, dateRef.current), POLLING_INTERVAL_MS);

    // Set up Server-Sent Events (SSE) for realtime updates
    const sseUrl = `${API_BASE_URL}/api/dashboard/realtime-stream`;
    const eventSource = new EventSource(sseUrl);
    
    const refreshFromSse = (event: MessageEvent) => {
      console.log('[SSE] Real-time order update received. Refreshing metrics...', event.data);
      fetchMetrics(false, shopIdRef.current, dateRef.current, true);
    };

    const refreshAfterReceive = (event: MessageEvent) => {
      console.log('[SSE] Order received. Refreshing after backend projection...', event.data);
      setTimeout(() => fetchMetrics(false, shopIdRef.current, dateRef.current, true), 1500);
    };

    eventSource.addEventListener('ORDER_RECEIVED', refreshAfterReceive);
    eventSource.addEventListener('ORDER_CONFIRMED', refreshFromSse);
    eventSource.addEventListener('DASHBOARD_DELTA', refreshFromSse);
    eventSource.addEventListener('order-update', refreshFromSse);

    eventSource.onerror = (err) => {
      console.warn('[SSE] EventSource connection encountered an error, will retry automatically:', err);
    };

    return () => {
      clearInterval(interval);
      eventSource.close();
    };
  }, []);

  // Show skeleton loading if we are fetching for the first time and have no data
  if (loading && !data) {
    return <DashboardSkeleton />;
  }

  // If there's an error and NO cached data to render
  if (error && !data) {
    return (
      <div className="relative min-h-screen bg-slate-950 flex flex-col justify-center">
        <DashboardSkeleton />
        <div className="absolute inset-0 bg-slate-950/85 backdrop-blur-[3px] flex flex-col items-center justify-center p-6 text-center z-50">
          <div className="bg-slate-900/90 border border-rose-500/25 p-7 rounded-2xl max-w-md shadow-2xl backdrop-blur-md">
            <div className="w-12 h-12 rounded-full bg-rose-500/10 flex items-center justify-center text-rose-400 mx-auto mb-4 border border-rose-500/20 font-black font-display text-lg">
              !
            </div>
            <h3 className="text-rose-400 font-extrabold text-sm tracking-wider uppercase font-display mb-2">
              Lỗi Kết Nối API
            </h3>
            <p className="text-slate-400 text-[11px] leading-relaxed mb-5 font-medium">
              {error}
            </p>
            <button
              onClick={() => fetchMetrics(true, selectedShopId)}
              disabled={isRefreshing}
              className="px-5 py-2.5 bg-rose-600 hover:bg-rose-500 text-white rounded-lg text-xs font-bold uppercase tracking-wider transition-colors duration-200 disabled:opacity-50 flex items-center gap-1.5 mx-auto"
            >
              Thử kết nối lại
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Render the dashboard normally
  return (
    <DashboardPage
      data={data!}
      isRefreshing={isRefreshing}
      onRefresh={() => fetchMetrics(true, selectedShopId, selectedDate)}
      error={error}
      selectedShopId={selectedShopId}
      onShopChange={handleShopChange}
      selectedDate={selectedDate}
      onDateChange={handleDateChange}
    />
  );
}
