import { TopAdCampaign } from '../../types/dashboard';
import { formatVND } from '../../utils/formatCurrency';
import { Layers } from 'lucide-react';
import { chartColors } from '../../utils/chartColors';

interface Column {
  label: string;
  field: string;
}

interface TopAdCampaignsTableProps {
  title: string;
  subtitle: string;
  columns: Column[];
  data: TopAdCampaign[];
  apiSources: string[];
  note?: string;
}

export default function TopAdCampaignsTable({
  title,
  subtitle,
  columns,
  data,
  apiSources,
  note
}: TopAdCampaignsTableProps) {
  const theme = chartColors.purple;

  const getPlatformBadge = (platform: string) => {
    switch (platform) {
      case 'facebook':
      case 'facebook-ads':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-[#1877f2]/10 text-[#1877f2] border border-[#1877f2]/20 uppercase">
            Meta Ads
          </span>
        );
      case 'tiktok':
      case 'tiktok-ads':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 uppercase">
            TikTok Ads
          </span>
        );
      case 'shopee':
      case 'shopee-ads':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 2xl:px-2 2xl:py-0.5 rounded text-[8px] 2xl:text-[12px] font-bold bg-[#ff5722]/10 text-orange-400 border border-[#ff5722]/20 uppercase">
            Shopee Ads
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

  const renderCampaignName = (name: string) => {
    if (!name) return '';
    const parts = name.split('_');
    if (parts.length > 1) {
      const objective = parts[0];
      const rest = parts.slice(1).join('_');
      return (
        <span className="truncate block" title={name}>
          <span className="text-purple-400 font-bold hover:text-purple-300 transition-colors">{objective}</span>
          <span className="text-slate-400 font-normal">_{rest}</span>
        </span>
      );
    }
    return <span className="truncate block" title={name}>{name}</span>;
  };

  return (
    <div className={`relative group overflow-hidden rounded-xl border ${theme.border} bg-gradient-to-b from-slate-900/90 to-slate-950/95 p-5 2xl:px-4 2xl:py-3 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full`}>
      {/* Glow Effect */}
      <div className={`absolute top-0 left-0 right-0 h-1/3 bg-gradient-to-b ${theme.glow} to-transparent opacity-40 group-hover:opacity-75 transition-opacity duration-300 pointer-events-none`} />

      <div className="z-10 flex-1 flex flex-col justify-between">
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 2xl:pb-1.5 mb-3 2xl:mb-2">
          <div className="flex items-center gap-2">
            <Layers size={15} className="text-purple-400" />
            <div>
              <h3 className="text-[10px] 2xl:text-base font-black tracking-widest uppercase text-slate-400 font-display">
                {title}
              </h3>
              <span className="text-[8px] 2xl:text-base text-slate-500 font-semibold uppercase tracking-wider">{subtitle}</span>
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
                  <th key={col.field} className={`pb-2 text-[10px] 2xl:text-base font-bold text-slate-500 uppercase tracking-wider ${col.field === 'attributedOrders' || col.field === 'roas' || col.field === 'spend' ? 'text-right' : ''}`}>
                    {col.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/25">
              {data.map((row) => (
                <tr key={row.campaignName} className="hover:bg-slate-800/10 transition-colors">
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] text-slate-200 font-semibold max-w-[120px] 2xl:max-w-[220px]">
                    {renderCampaignName(row.campaignName)}
                  </td>
                  <td className="py-2.5 2xl:py-4">
                    {getPlatformBadge(row.platform)}
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono text-slate-300 text-right">
                    {formatVND(row.spend)}
                  </td>
                  <td className="py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono text-slate-300 text-right">
                    {row.attributedOrders}
                  </td>
                  <td className={`py-2.5 2xl:py-4 text-[11px] 2xl:text-[18px] font-mono font-extrabold text-right ${row.roas >= 4.0 ? 'text-emerald-400' : 'text-slate-400'}`}>
                    {row.roas.toFixed(2)}x
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {note && (
        <div className="text-[7px] 2xl:text-[12px] text-slate-500 italic mt-2 text-right">
          * {note}
        </div>
      )}
    </div>
  );
}
