import { RevenueByChannelItem } from '../../types/dashboard';
import { formatVND } from '../../utils/formatCurrency';
import { Store, TrendingUp } from 'lucide-react';
import { chartColors } from '../../utils/chartColors';

interface RevenueByChannelTableProps {
  title: string;
  data: RevenueByChannelItem[];
  apiSources: string[];
}

export default function RevenueByChannelTable({
  title,
  data,
  apiSources
}: RevenueByChannelTableProps) {
  const theme = chartColors.green;

  const getPlatformBadge = (platform: string) => {
    switch (platform) {
      case 'haravan':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-blue-600/10 text-blue-400 border border-blue-500/20 uppercase">
            Haravan
          </span>
        );
      case 'shopee':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-[#ff5722]/10 text-orange-400 border border-[#ff5722]/20 uppercase">
            Shopee
          </span>
        );
      case 'tiktok-shop':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-purple-500/10 text-purple-400 border border-purple-500/20 uppercase">
            TikTok Shop
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-slate-500/10 text-slate-400 border border-slate-500/20 uppercase">
            {platform}
          </span>
        );
    }
  };

  return (
    <div className={`relative group overflow-hidden rounded-xl border ${theme.border} bg-gradient-to-b from-slate-900/90 to-slate-950/95 p-5 2xl:px-4 2xl:py-3 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full`}>
      {/* Glow Effect */}
      <div className={`absolute top-0 left-0 right-0 h-1/3 bg-gradient-to-b ${theme.glow} to-transparent opacity-40 group-hover:opacity-75 transition-opacity duration-300 pointer-events-none`} />

      <div className="z-10 flex-1 flex flex-col justify-between">
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 2xl:pb-1.5 mb-3 2xl:mb-2">
          <div className="flex items-center gap-2">
            <Store size={15} className="text-emerald-400" />
            <div>
              <h3 className="text-[10px] 2xl:text-base font-black tracking-widest uppercase text-slate-400 font-display">
                {title}
              </h3>
              <span className="text-[8px] 2xl:text-base text-slate-500 font-semibold uppercase tracking-wider">DOANH SỐ HÔM NAY</span>
            </div>
          </div>
          <div className="relative">
            <span className="text-[9px] 2xl:text-sm text-slate-500 font-mono hover:text-slate-300 cursor-pointer">
               API SOURCES
             </span>
            <div className="pointer-events-none absolute bottom-full right-0 mb-2 w-52 p-2 bg-slate-950 border border-slate-800 rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 z-50 text-[9px] text-slate-300 space-y-1">
              <div className="font-bold text-white border-b border-slate-800 pb-1 mb-1">API Sources:</div>
              {apiSources.map((src, idx) => (
                <div key={idx} className="flex items-start gap-1">
                  <span className="text-blue-400">•</span>
                  <span>{src}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="overflow-auto max-h-[145px] 2xl:max-h-none flex-1">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-800">
                <th className="pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider">KÊNH</th>
                <th className="pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider text-right">DOANH THU</th>
                <th className="pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider text-right">TỶ TRỌNG</th>
                <th className="pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider text-right">TĂNG TRƯỞNG</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/25">
              {data.map((row) => (
                <tr key={row.platform} className="hover:bg-slate-800/10 transition-colors">
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-semibold text-slate-200 flex items-center gap-1.5 2xl:gap-2.5">
                    {getPlatformBadge(row.platform)}
                    <span className="truncate max-w-[90px] 2xl:max-w-[180px]">{row.label}</span>
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono font-bold text-slate-100 text-right">
                    {formatVND(row.revenue)}
                  </td>
                  <td className="py-2.5 2xl:py-4 text-right">
                    <div className="inline-flex items-center gap-1.5 justify-end w-full">
                      <span className="text-[11px] 2xl:text-[18px] font-mono text-slate-300 font-bold">{row.share}%</span>
                      <div className="w-12 2xl:w-24 bg-slate-800 rounded-full h-1.5 overflow-hidden hidden sm:block">
                        <div 
                          className={`h-full ${
                            row.platform === 'haravan' ? 'bg-blue-500' : row.platform === 'shopee' ? 'bg-orange-500' : 'bg-purple-500'
                          }`}
                          style={{ width: `${row.share}%` }}
                        />
                      </div>
                    </div>
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono font-bold text-emerald-400 text-right">
                    <span className="inline-flex items-center gap-0.5 text-[9px] 2xl:text-[14px] bg-emerald-500/10 text-emerald-400 px-1.5 py-0.5 2xl:px-2.5 2xl:py-1 rounded font-mono font-extrabold">
                      <TrendingUp size={12} className="2xl:w-4 2xl:h-4" />
                      +{row.changePercent}%
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
