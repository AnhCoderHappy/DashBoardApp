import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { formatVND } from '../../utils/formatCurrency';
import { Activity } from 'lucide-react';
import { RevenueByChannelItem } from '../../types/dashboard';
import { chartColors } from '../../utils/chartColors';

interface RevenueShareDonutCardProps {
  title: string;
  data: RevenueByChannelItem[];
}

export default function RevenueShareDonutCard({
  title,
  data
}: RevenueShareDonutCardProps) {
  const theme = chartColors.green;

  // Map platform to colors matching our overall design system
  const getColor = (platform: string) => {
    switch (platform) {
      case 'haravan':
        return '#3b82f6'; // Blue
      case 'shopee':
        return '#f97316'; // Orange
      case 'tiktok-shop':
        return '#a855f7'; // Purple
      case 'facebook':
        return '#06b6d4'; // Cyan/Blue
      case 'instagram':
        return '#ec4899'; // Pink
      case 'pos':
        return '#10b981'; // Emerald Green
      default:
        return '#64748b'; // Muted grey
    }
  };

  const getLabel = (platform: string) => {
    switch (platform) {
      case 'haravan':
        return 'Haravan';
      case 'shopee':
        return 'Shopee';
      case 'tiktok-shop':
        return 'TikTok Shop';
      case 'facebook':
        return 'Facebook';
      case 'instagram':
        return 'Instagram';
      case 'pos':
        return 'Pancake POS';
      default:
        return platform;
    }
  };

  const chartData = data.map((item) => ({
    name: getLabel(item.platform),
    value: item.revenue,
    share: item.share,
    color: getColor(item.platform)
  }));

  const totalRevenue = data.reduce((acc, curr) => acc + curr.revenue, 0);

  return (
    <div className={`relative group overflow-hidden rounded-xl border ${theme.border} bg-gradient-to-b from-slate-900/90 to-slate-950/95 p-5 2xl:px-4 2xl:py-3 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full`}>
      {/* Glow Effect */}
      <div className={`absolute top-0 left-0 right-0 h-1/3 bg-gradient-to-b ${theme.glow} to-transparent opacity-40 group-hover:opacity-75 transition-opacity duration-300 pointer-events-none`} />

      <div className="z-10 flex-1 flex flex-col justify-between">
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 2xl:pb-1.5 mb-3 2xl:mb-2">
          <div className="flex items-center gap-2">
            <Activity size={15} className="text-emerald-400" />
            <div>
              <h3 className="text-[10px] 2xl:text-base font-black tracking-widest uppercase text-slate-400 font-display">
                {title}
              </h3>
              <span className="text-[8px] 2xl:text-base text-slate-500 font-semibold uppercase tracking-wider">TỶ TRỌNG DOANH SỐ</span>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between gap-4 mt-2 2xl:mt-1 flex-1">
          {/* Chart Wrapper */}
          <div className="relative w-1/2 2xl:w-[260px] h-[130px] 2xl:h-[260px] flex items-center justify-center shrink-0">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={chartData}
                  cx="50%"
                  cy="50%"
                  innerRadius="55%"
                  outerRadius="78%"
                  paddingAngle={4}
                  dataKey="value"
                >
                  {chartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#020617',
                    border: '1px solid rgba(51, 65, 85, 0.4)',
                    borderRadius: '6px',
                    fontSize: '9px',
                    fontFamily: 'Inter, sans-serif'
                  }}
                  formatter={(value: number) => [formatVND(value), 'Doanh thu']}
                />
              </PieChart>
            </ResponsiveContainer>

            {/* Inner Center Text */}
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none select-none text-center">
              <span className="text-[7px] 2xl:text-base text-slate-500 font-bold uppercase tracking-wider">TỔNG SALES</span>
              <span className="text-[10px] 2xl:text-3xl font-mono font-black text-slate-100 mt-1.5 2xl:mt-2.5">
                {totalRevenue >= 1000000 
                  ? `${(totalRevenue / 1000000).toFixed(2)}M` 
                  : `${(totalRevenue / 1000).toFixed(0)}k`
                }
              </span>
            </div>
          </div>

          {/* Legends */}
          <div className="flex-1 flex flex-col gap-2.5 2xl:gap-4 pl-2 2xl:pl-6">
            {chartData.map((item, idx) => (
              <div key={idx} className="flex flex-col">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <span className="w-1.5 h-1.5 2xl:w-2.5 2xl:h-2.5 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
                    <span className="text-[9px] 2xl:text-[18px] font-black text-slate-200 truncate uppercase">{item.name}</span>
                  </div>
                  <span className="text-[9px] 2xl:text-[18px] font-mono font-bold text-white shrink-0">{item.share}%</span>
                </div>
                <span className="text-[9px] 2xl:text-[18px] font-mono text-slate-400 pl-3 2xl:pl-4 mt-0.5">{formatVND(item.value)}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
