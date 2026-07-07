import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, Legend } from 'recharts';
import { formatVND } from '../../utils/formatCurrency';
import { chartColors } from '../../utils/chartColors';

interface Series {
  label: string;
  valueField: string;
  colorName: string;
}

interface HourlyAdCostLineChartProps {
  title: string;
  series: Series[];
  data: any[];
  apiSources: string[];
  note?: string;
}

export default function HourlyAdCostLineChart({
  title,
  series,
  data,
  apiSources,
  note
}: HourlyAdCostLineChartProps) {
  const theme = chartColors.cyan;

  const colorMap: Record<string, string> = {
    blue: '#3b82f6',   // Meta Ads
    cyan: '#06b6d4',   // TikTok Ads
    orange: '#f97316', // Shopee Ads
  };

  return (
    <div className={`relative group overflow-hidden rounded-xl border ${theme.border} bg-gradient-to-b from-slate-900/90 to-slate-950/95 p-5 2xl:p-3 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full`}>
      {/* Glow Effect */}
      <div className={`absolute top-0 left-0 right-0 h-1/3 bg-gradient-to-b ${theme.glow} to-transparent opacity-40 group-hover:opacity-75 transition-opacity duration-300 pointer-events-none`} />

      <div className="z-10 flex-1 flex flex-col justify-between">
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-3">
          <div>
            <h3 className="text-[10px] 2xl:text-base font-black tracking-widest uppercase text-slate-400 font-display">{title}</h3>
            <span className="text-[8px] 2xl:text-sm text-slate-500 font-semibold uppercase tracking-wider">CHI PHÍ THEO GIỜ</span>
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

        <div className="h-[145px] 2xl:h-auto 2xl:flex-1 2xl:min-h-0 w-full text-[8px] font-mono mt-2 flex-1">
          {data && data.length > 0 ? (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={data} margin={{ top: 5, right: 5, left: -25, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(30, 41, 59, 0.25)" />
                <XAxis dataKey="hour" stroke="#64748b" style={{ fontSize: '8px' }} tickLine={false} />
                <YAxis 
                  stroke="#64748b" 
                  style={{ fontSize: '8px' }}
                  tickLine={false} 
                  axisLine={false}
                  tickFormatter={(val) => val >= 1000 ? `${(val / 1000).toFixed(0)}k` : String(val)}
                />
                <Tooltip 
                  formatter={(val: number) => [formatVND(val), 'Chi phí']}
                  contentStyle={{ backgroundColor: '#020617', borderColor: 'rgba(51, 65, 85, 0.4)', borderRadius: 6, fontSize: 9 }}
                />
                <Legend 
                  verticalAlign="top"
                  height={20}
                  iconType="circle"
                  iconSize={5}
                  wrapperStyle={{ fontSize: 8, fontFamily: 'Outfit, Inter, sans-serif', paddingBottom: '5px' }}
                />
                {series.map((s) => (
                  <Line
                    key={s.valueField}
                    type="monotone"
                    dataKey={s.valueField}
                    stroke={colorMap[s.colorName] || '#94a3b8'}
                    strokeWidth={2.5}
                    dot={false}
                    activeDot={{ r: 3, strokeWidth: 0, fill: colorMap[s.colorName] }}
                    name={s.label}
                  />
                ))}
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-full text-[10px] text-slate-500 italic">
              Không có dữ liệu chi phí theo giờ
            </div>
          )}
        </div>
      </div>

      {note && (
        <div className="text-[7px] 2xl:text-xs text-slate-500 italic mt-2 text-right">
          * {note}
        </div>
      )}
    </div>
  );
}
