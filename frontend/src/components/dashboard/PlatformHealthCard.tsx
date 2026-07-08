import StatusBadge from './StatusBadge';
import { formatTime } from '../../utils/time';

interface PlatformHealthCardProps {
  title: string;
  availabilityType: string;
  status?: 'ok' | 'warning' | 'error' | 'unknown';
  lastSuccessAt?: string | null;
  lastErrorAt?: string | null;
  
  // Custom properties for ads_health
  isAdsHealth?: boolean;
  metaStatus?: 'ok' | 'warning' | 'error' | 'unknown';
  tiktokAdsStatus?: 'ok' | 'warning' | 'error' | 'unknown';
  lastUpdatedAt?: string | null;
}

export default function PlatformHealthCard({
  title,
  availabilityType,
  status = 'unknown',
  lastSuccessAt,
  lastErrorAt,
  isAdsHealth = false,
  metaStatus = 'unknown',
  tiktokAdsStatus = 'unknown',
  lastUpdatedAt
}: PlatformHealthCardProps) {
  const formatDateTimeShort = (isoString?: string | null) => {
    if (!isoString) return 'Chưa đồng bộ';
    const d = new Date(isoString);
    return `${d.toLocaleDateString('vi-VN', { month: '2-digit', day: '2-digit', timeZone: 'Asia/Ho_Chi_Minh' })} ${formatTime(isoString)}`;
  };


  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-4 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300">
      <div className="flex items-center justify-between border-b border-slate-800/40 pb-2 mb-3">
        <span className="text-[10px] tracking-wider font-extrabold text-slate-400 uppercase font-display">
          {title}
        </span>
        <span className="text-[7px] font-mono text-slate-600 uppercase tracking-widest">
          {availabilityType.replace('_', ' ')}
        </span>
      </div>

      {!isAdsHealth ? (
        // Standard Channel Health Details
        <div className="flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-slate-400">Trạng thái:</span>
            <StatusBadge status={status} size={12} />
          </div>
          
          <div className="space-y-1">
            <div className="flex justify-between items-center text-[9px] text-slate-500">
              <span>Đồng bộ cuối:</span>
              <span className="font-mono text-slate-300">{formatDateTimeShort(lastSuccessAt)}</span>
            </div>
            {status !== 'ok' && lastErrorAt && (
              <div className="flex justify-between items-center text-[9px] text-rose-500 bg-rose-500/5 px-1 py-0.5 rounded border border-rose-500/10">
                <span>Lỗi cuối:</span>
                <span className="font-mono font-medium">{formatDateTimeShort(lastErrorAt)}</span>
              </div>
            )}
          </div>
        </div>
      ) : (
        // Custom Advertising Accounts Health (Meta + TikTok)
        <div className="flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#1877f2]" />
              <span className="text-[9px] text-slate-300 font-bold uppercase font-display">Meta Ads</span>
            </div>
            <StatusBadge status={metaStatus} size={10} />
          </div>

          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400" />
              <span className="text-[9px] text-slate-300 font-bold uppercase font-display">TikTok Ads</span>
            </div>
            <StatusBadge status={tiktokAdsStatus} size={10} />
          </div>

          <div className="flex justify-between items-center text-[9px] text-slate-500 border-t border-slate-800/40 pt-1.5 mt-0.5">
            <span>Cập nhật lúc:</span>
            <span className="font-mono text-slate-300">{formatDateTimeShort(lastUpdatedAt)}</span>
          </div>
        </div>
      )}
    </div>
  );
}
