import { RealtimeOrder } from '../../types/dashboard';
import { formatVND } from '../../utils/formatCurrency';
import { formatTime } from '../../utils/time';
import { ShoppingBag } from 'lucide-react';
import { chartColors } from '../../utils/chartColors';

interface Column {
  label: string;
  field: string;
}

interface RealtimeOrdersTableProps {
  title: string;
  note: string;
  columns: Column[];
  data: RealtimeOrder[];
  apiSources: string[];
  logic: string;
}

export default function RealtimeOrdersTable({
  title,
  note,
  columns,
  data,
  apiSources
}: RealtimeOrdersTableProps) {
  const theme = chartColors.blue;

  const getPlatformBadge = (platform: string) => {
    switch (platform) {
      case 'pos':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 uppercase">
            Pancake POS
          </span>
        );
      case 'facebook':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-sky-500/10 text-sky-400 border border-sky-500/20 uppercase">
            Facebook
          </span>
        );
      case 'instagram':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-rose-500/10 text-rose-400 border border-rose-500/20 uppercase">
            Instagram
          </span>
        );
      case 'haravan':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-[#1877f2]/10 text-blue-400 border border-[#1877f2]/20 uppercase">
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
            <ShoppingBag size={15} className="text-blue-400" />
            <div>
              <h3 className="text-[10px] 2xl:text-base font-black tracking-widest uppercase text-slate-400 font-display">
                {title} <span className="text-[8px] 2xl:text-base text-rose-500 font-bold font-body animate-pulse">• {note}</span>
              </h3>
              <span className="text-[8px] 2xl:text-base text-slate-500 font-semibold uppercase tracking-wider">ĐƠN HÀNG PHÁT SINH</span>
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
                {columns.map((col) => (
                  <th key={col.field} className={`pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider ${col.field === 'orderValue' ? 'text-right' : ''}`}>
                    {col.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/25">
              {data.map((row) => (
                <tr key={row.id} className="hover:bg-slate-800/10 transition-colors">
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono text-slate-400">
                    {formatTime(row.createdAt)}
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono text-slate-200 font-semibold">
                    {row.orderCode}
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] text-slate-300 truncate max-w-[100px] 2xl:max-w-[180px]" title={row.customerDisplayName}>
                    {row.customerDisplayName}
                  </td>
                  <td className="py-2.5 2xl:py-4">
                    {getPlatformBadge(row.platform)}
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono text-white font-extrabold text-right">
                    {formatVND(row.orderValue)}
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
