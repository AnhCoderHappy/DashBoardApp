import { PlatformHealthDetails } from '../../types/dashboard';
import { formatTime } from '../../utils/time';
import { RefreshCw } from 'lucide-react';

interface DataSourceFooterProps {
  platformHealth: {
    pancakePos: PlatformHealthDetails;
    pancakeAds: PlatformHealthDetails;
  };
  lastUpdatedAt: string;
  isRefreshing: boolean;
  onRefresh: () => void;
}

export default function DataSourceFooter({
  platformHealth,
  lastUpdatedAt,
  isRefreshing,
  onRefresh
}: DataSourceFooterProps) {
  const getStatusDot = (status: string) => {
    switch (status) {
      case 'ok':
        return 'bg-emerald-500';
      case 'warning':
        return 'bg-amber-500';
      case 'error':
        return 'bg-rose-500';
      default:
        return 'bg-slate-500';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'ok':
        return 'text-emerald-400';
      case 'warning':
        return 'text-amber-400';
      case 'error':
        return 'text-rose-400';
      default:
        return 'text-slate-400';
    }
  };

  return (
    <footer className="mt-2 border border-slate-800/80 bg-slate-900/60 backdrop-blur-md rounded-xl p-3.5 2xl:p-5 flex flex-col md:flex-row items-center justify-between gap-3 text-[10px] 2xl:text-xs text-slate-400">
      {/* Platform Health Status Grid */}
      <div className="flex flex-wrap items-center justify-center md:justify-start gap-4">
        <span className="font-display font-black text-slate-500 tracking-wider text-[9px] 2xl:text-xs uppercase mr-1">
          NGUỒN DỮ LIỆU:
        </span>
        
        {/* Pancake POS */}
        <div className="flex items-center gap-1.5 bg-slate-950/40 border border-slate-800/40 px-2 py-1 2xl:px-3 2xl:py-1.5 rounded">
          <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(platformHealth.pancakePos.status)}`} />
          <span className="font-semibold uppercase text-slate-300">Pancake POS:</span>
          <span className={`font-mono font-bold ${getStatusText(platformHealth.pancakePos.status)}`}>
            {platformHealth.pancakePos.label}
          </span>
        </div>

        {/* Pancake Ads */}
        <div className="flex items-center gap-1.5 bg-slate-950/40 border border-slate-800/40 px-2 py-1 2xl:px-3 2xl:py-1.5 rounded">
          <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(platformHealth.pancakeAds.status)}`} />
          <span className="font-semibold uppercase text-slate-300">Pancake Ads:</span>
          <span className={`font-mono font-bold ${getStatusText(platformHealth.pancakeAds.status)}`}>
            {platformHealth.pancakeAds.label}
          </span>
        </div>
      </div>

      {/* Sync State & Action Button */}
      <div className="flex items-center gap-3">
        <span className="font-mono text-slate-500 font-medium">
          CẬP NHẬT LẦN CUỐI: <strong className="text-slate-300">{formatTime(lastUpdatedAt)}</strong>
        </span>
        <button
          onClick={onRefresh}
          disabled={isRefreshing}
          className="flex items-center gap-1 px-2.5 py-1 2xl:px-3.5 2xl:py-1.5 rounded border border-slate-800 bg-slate-950/50 hover:bg-slate-800 hover:text-white transition duration-200 font-semibold font-display select-none disabled:opacity-50"
        >
          <RefreshCw className={`w-3 h-3 ${isRefreshing ? 'animate-spin text-blue-500' : ''}`} />
          LÀM MỚI
        </button>
      </div>
    </footer>
  );
}
