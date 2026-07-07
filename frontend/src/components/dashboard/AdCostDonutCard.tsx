import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { formatVND } from '../../utils/formatCurrency';
import { chartColors } from '../../utils/chartColors';

interface Segment {
  label: string;
  valueField: string;
  colorName: string;
}

interface AdCostDonutCardProps {
  title: string;
  subtitle: string;
  value: number; // total spend
  segments: Segment[];
  data: Record<string, number>;
  apiSources: string[];
}

export default function AdCostDonutCard({
  title,
  subtitle,
  segments,
  data,
  apiSources
}: AdCostDonutCardProps) {
  const theme = chartColors.cyan;

  // Map color names to hex codes
  const colorMap: Record<string, string> = {
    blue: '#3b82f6',   // Meta Ads
    cyan: '#06b6d4',   // TikTok Ads
    orange: '#f97316', // Shopee Ads
  };

  const chartData = segments.map((seg) => {
    const val = data[seg.valueField] || 0;
    return {
      name: seg.label,
      value: val,
      color: colorMap[seg.colorName] || '#94a3b8',
    };
  });

  const totalValue = chartData.reduce((acc, curr) => acc + curr.value, 0);

  return (
    <div className={`relative group overflow-hidden rounded-xl border ${theme.border} bg-gradient-to-b from-slate-900/90 to-slate-950/95 p-5 2xl:px-4 2xl:py-3 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full`}>
      {/* Glow Effect */}
      <div className={`absolute top-0 left-0 right-0 h-1/3 bg-gradient-to-b ${theme.glow} to-transparent opacity-40 group-hover:opacity-75 transition-opacity duration-300 pointer-events-none`} />

      <div className="z-10 flex-1 flex flex-col justify-between">
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 2xl:pb-1.5 mb-4 2xl:mb-2">
          <div>
            <h3 className="text-[10px] 2xl:text-base font-black tracking-widest uppercase text-slate-400 font-display">{title}</h3>
            <span className="text-[8px] 2xl:text-base text-slate-500 font-semibold uppercase tracking-wider">{subtitle}</span>
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

        <div className="flex items-center justify-between gap-4 flex-1 mt-2 2xl:mt-1">
          {/* Donut Chart */}
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
                  formatter={(val: number) => [formatVND(val), 'Chi phí']}
                  contentStyle={{ backgroundColor: '#020617', borderColor: 'rgba(51, 65, 85, 0.4)', borderRadius: 6, fontSize: 9 }}
                />
              </PieChart>
            </ResponsiveContainer>
            
            {/* Central text overlay */}
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none text-center">
              <span className="text-[7px] 2xl:text-base font-bold text-slate-500 uppercase tracking-widest">TỔNG SPEND</span>
              <span className="text-[10px] 2xl:text-3xl font-mono font-black text-white leading-none mt-1 2xl:mt-2.5">
                {totalValue >= 1000000 
                  ? `${(totalValue / 1000000).toFixed(2)}M` 
                  : `${(totalValue / 1000).toFixed(0)}k`
                }
              </span>
            </div>
          </div>

          {/* Legend listing */}
          <div className="flex-1 flex flex-col gap-2.5 2xl:gap-4 pl-2 2xl:pl-6">
            {chartData.map((item, idx) => {
              const pct = totalValue > 0 ? (item.value / totalValue) * 100 : 0;
              return (
                <div key={idx} className="flex flex-col">
                  <div className="flex items-center gap-1.5 justify-between">
                    <div className="flex items-center gap-1.5 min-w-0">
                      <span className="w-1.5 h-1.5 2xl:w-2.5 2xl:h-2.5 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
                      <span className="text-[9px] 2xl:text-[18px] font-black text-slate-200 truncate uppercase">{item.name}</span>
                    </div>
                    <span className="text-[9px] 2xl:text-[18px] font-mono font-bold text-white shrink-0">{pct.toFixed(0)}%</span>
                  </div>
                  <span className="text-[9px] 2xl:text-[18px] font-mono text-slate-400 pl-3 2xl:pl-4 mt-0.5">{formatVND(item.value)}</span>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
