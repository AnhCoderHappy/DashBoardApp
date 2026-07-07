import { TopProductByChannelItem } from '../../types/dashboard';
import { formatVND } from '../../utils/formatCurrency';
import { ShoppingBag } from 'lucide-react';
import { chartColors } from '../../utils/chartColors';

interface TopProductsByChannelTableProps {
  title: string;
  data: TopProductByChannelItem[];
}

export default function TopProductsByChannelTable({
  title,
  data
}: TopProductsByChannelTableProps) {
  const theme = chartColors.blue;

  const getRankBadge = (rank: number) => {
    switch (rank) {
      case 1:
        return (
          <span className="inline-flex items-center justify-center w-4 h-4 2xl:w-7 2xl:h-7 rounded-full text-[9px] 2xl:text-base font-black bg-amber-500/20 text-amber-400 border border-amber-500/30">
            1
          </span>
        );
      case 2:
        return (
          <span className="inline-flex items-center justify-center w-4 h-4 2xl:w-7 2xl:h-7 rounded-full text-[9px] 2xl:text-base font-black bg-slate-400/20 text-slate-300 border border-slate-400/30">
            2
          </span>
        );
      case 3:
        return (
          <span className="inline-flex items-center justify-center w-4 h-4 2xl:w-7 2xl:h-7 rounded-full text-[9px] 2xl:text-base font-black bg-amber-700/20 text-orange-400 border border-amber-700/30">
            3
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center justify-center w-4 h-4 2xl:w-7 2xl:h-7 rounded-full text-[9px] 2xl:text-base font-bold bg-slate-800 text-slate-400">
            {rank}
          </span>
        );
    }
  };

  const getChannelBadge = (platform: string) => {
    switch (platform) {
      case 'facebook':
        return (
          <span className="px-1.5 py-0.5 rounded text-[8px] 2xl:text-xs font-bold bg-blue-500/10 text-blue-400 border border-blue-500/20">
            FB
          </span>
        );
      case 'instagram':
        return (
          <span className="px-1.5 py-0.5 rounded text-[8px] 2xl:text-xs font-bold bg-pink-500/10 text-pink-400 border border-pink-500/20">
            IG
          </span>
        );
      case 'shopee':
        return (
          <span className="px-1.5 py-0.5 rounded text-[8px] 2xl:text-xs font-bold bg-orange-500/10 text-orange-400 border border-orange-500/20">
            Shopee
          </span>
        );
      case 'tiktok-shop':
        return (
          <span className="px-1.5 py-0.5 rounded text-[8px] 2xl:text-xs font-bold bg-red-500/10 text-red-400 border border-red-500/20">
            TikTok
          </span>
        );
      case 'haravan':
        return (
          <span className="px-1.5 py-0.5 rounded text-[8px] 2xl:text-xs font-bold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
            Web
          </span>
        );
      default:
        return (
          <span className="px-1.5 py-0.5 rounded text-[8px] 2xl:text-xs font-bold bg-slate-800 text-slate-400 border border-slate-700/50">
            POS
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
            <ShoppingBag size={15} className="text-blue-400" />
            <div>
              <h3 className="text-[10px] 2xl:text-base font-black tracking-widest uppercase text-slate-400 font-display">
                {title}
              </h3>
              <span className="text-[8px] 2xl:text-base text-slate-500 font-semibold uppercase tracking-wider">HÔM NAY</span>
            </div>
          </div>
        </div>

        <div className="overflow-auto max-h-[145px] 2xl:max-h-none flex-1">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-800">
                <th className="pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider w-12 text-center">HẠNG</th>
                <th className="pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider">SẢN PHẨM / KÊNH</th>
                <th className="pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider text-right">LƯỢT BÁN</th>
                <th className="pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider text-right">DOANH SỐ</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/25">
              {data.map((row, idx) => (
                <tr key={`${row.productName}-${row.platform}-${idx}`} className="hover:bg-slate-800/10 transition-colors">
                  <td className="py-2.5 2xl:py-4 text-center">
                    {getRankBadge(row.rank)}
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-semibold text-slate-200">
                    <div className="flex items-center gap-1.5">
                      {getChannelBadge(row.platform)}
                      <span className="truncate max-w-[80px] 2xl:max-w-[150px]" title={row.productName}>
                        {row.productName}
                      </span>
                    </div>
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono font-bold text-slate-300 text-right">
                    {row.orders}
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono font-bold text-slate-100 text-right">
                    {formatVND(row.revenue)}
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
