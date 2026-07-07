import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, Legend } from 'recharts';
import { formatVND } from '../../utils/formatCurrency';
import { BarChart2 } from 'lucide-react';
import { HourlyRevenueItem } from '../../types/dashboard';
import { chartColors } from '../../utils/chartColors';

interface HourlyRevenueLineChartProps {
  title: string;
  data: HourlyRevenueItem[];
  apiSources: string[];
}

export default function HourlyRevenueLineChart({
  title,
  data,
  apiSources
}: HourlyRevenueLineChartProps) {
  const theme = chartColors.green;

  const formatYAxis = (value: number) => {
    if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`;
    if (value >= 1000) return `${(value / 1000).toFixed(0)}k`;
    return String(value);
  };

  return (
    <div className={`relative group overflow-hidden rounded-xl border ${theme.border} bg-gradient-to-b from-slate-900/90 to-slate-950/95 p-5 2xl:p-3 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full`}>
      {/* Glow Effect */}
      <div className={`absolute top-0 left-0 right-0 h-1/3 bg-gradient-to-b ${theme.glow} to-transparent opacity-40 group-hover:opacity-75 transition-opacity duration-300 pointer-events-none`} />

      <div className="z-10 2xl:flex-1 2xl:flex 2xl:flex-col 2xl:min-h-0">
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-3">
          <div className="flex items-center gap-2">
            <BarChart2 size={15} className="text-emerald-400" />
            <div>
              <h3 className="text-[10px] 2xl:text-base font-black tracking-widest uppercase text-slate-400 font-display">
                {title}
              </h3>
              <span className="text-[8px] 2xl:text-sm text-slate-500 font-semibold uppercase tracking-wider">DOANH SỐ THEO GIỜ</span>
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

        <div className="h-[145px] 2xl:h-auto 2xl:flex-1 2xl:min-h-0 w-full text-[8px] font-mono mt-2">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart
              data={data}
              margin={{ top: 5, right: 5, left: -25, bottom: 0 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(30, 41, 59, 0.25)" vertical={false} />
              <XAxis 
                dataKey="hour" 
                stroke="#64748b" 
                tickLine={false} 
                axisLine={false}
                tickMargin={6}
                style={{ fontSize: '8px' }}
              />
              <YAxis 
                stroke="#64748b" 
                tickLine={false} 
                axisLine={false}
                tickFormatter={formatYAxis}
                tickMargin={6}
                style={{ fontSize: '8px' }}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#020617',
                  border: '1px solid rgba(51, 65, 85, 0.4)',
                  borderRadius: '6px',
                  fontSize: '9px',
                  fontFamily: 'Inter, sans-serif'
                }}
                formatter={(value: number) => [formatVND(value), 'Doanh thu']}
                labelFormatter={(label) => `Thời gian: ${label}`}
              />
              <Legend 
                verticalAlign="top" 
                height={20}
                iconType="circle"
                iconSize={5}
                wrapperStyle={{
                  fontSize: '8px',
                  fontFamily: 'Outfit, Inter, sans-serif',
                  paddingBottom: '5px'
                }}
              />
              <Line 
                name="Hôm nay"
                type="monotone" 
                dataKey="todayRevenue" 
                stroke={theme.stroke} 
                strokeWidth={2.5}
                dot={false}
                activeDot={{ r: 3, strokeWidth: 0, fill: theme.stroke }}
              />
              <Line 
                name="Hôm qua"
                type="monotone" 
                dataKey="yesterdayRevenue" 
                stroke="#64748b" 
                strokeWidth={1.5}
                strokeDasharray="4 4"
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
