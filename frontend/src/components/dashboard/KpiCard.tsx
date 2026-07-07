import { AreaChart, Area, ResponsiveContainer, Tooltip } from 'recharts';
import { formatVND } from '../../utils/formatCurrency';
import { formatNumber } from '../../utils/formatNumber';
import { chartColors, ChartColorKey } from '../../utils/chartColors';

interface KpiSparkPoint {
  label: string;
  value: number;
  previousValue?: number;
}

interface KpiCardProps {
  title: string;
  value: number;
  unit?: string;
  color: ChartColorKey;
  changePercent?: number;
  compareLabel?: string;
  trend?: 'up' | 'down' | 'neutral';
  sparklineData?: KpiSparkPoint[];
  isCurrency?: boolean;
  isCompact?: boolean;
}

export default function KpiCard({
  title,
  value,
  unit,
  color,
  changePercent,
  compareLabel,
  trend = 'neutral',
  sparklineData = [],
  isCurrency = false,
  isCompact = false
}: KpiCardProps) {
  const theme = chartColors[color] || chartColors.blue;

  // Format main display value
  const formattedValue = isCurrency
    ? formatVND(value).replace(/[\s\u00A0]*₫/g, '') // strip unit since we render it separately
    : formatNumber(value);

  // Determine trend text styling
  const isUp = trend === 'up';
  const isDown = trend === 'down';
  const trendColorClass = isUp 
    ? 'text-emerald-400 font-extrabold' 
    : isDown 
      ? 'text-rose-400 font-extrabold' 
      : 'text-slate-400';

  const trendIcon = isUp ? '▲' : isDown ? '▼' : '•';

  return (
    <div className={`relative group overflow-hidden rounded-xl border ${theme.border} bg-gradient-to-b from-slate-900/90 to-slate-950/95 flex flex-col justify-between shadow-lg shadow-slate-950/30 transition-all duration-300 ${isCompact ? 'h-[140px] 2xl:h-[175px]' : 'h-[175px] 2xl:h-[220px]'} hover:border-slate-700/80`}>
      {/* Background glow matching the theme color */}
      <div className={`absolute top-0 left-0 right-0 h-1/2 bg-gradient-to-b ${theme.glow} to-transparent opacity-60 group-hover:opacity-100 transition-opacity duration-300 pointer-events-none`} />

      {/* Top Section: Title & Trend Info */}
      <div className="p-4 2xl:px-5 2xl:py-4 pb-0 z-10 flex-1 flex flex-col justify-between">
        <div className="flex flex-col gap-1">
          <span className={`text-[9px] 2xl:text-[15px] font-black tracking-widest uppercase ${theme.text}`}>
            {title}
          </span>
          <div className="flex items-baseline gap-1 mt-1">
            <span className="text-3xl sm:text-4xl 2xl:text-5xl font-black text-white font-display tracking-tight leading-none">
              {formattedValue}
            </span>
            {unit && (
              <span className="text-sm 2xl:text-xl font-extrabold text-slate-400 align-bottom leading-none select-none">
                {unit}
              </span>
            )}
          </div>
        </div>

        {/* Comparison row */}
        {changePercent !== undefined && (
          <div className="text-[10px] 2xl:text-[15px] text-slate-400 flex items-center gap-1 mt-1.5 mb-2 2xl:mb-3">
            <span className={trendColorClass}>
              {trendIcon} {Math.abs(changePercent)}%
            </span>
            <span className="text-slate-500 font-semibold">{compareLabel}</span>
          </div>
        )}
      </div>

      {/* Mini AreaChart at the bottom */}
      {sparklineData && sparklineData.length > 0 && (
        <div className="h-[52px] 2xl:h-[75px] w-full mt-auto relative z-10 overflow-hidden rounded-b-xl">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart
              data={sparklineData}
              margin={{ top: 5, right: 0, left: 0, bottom: 0 }}
            >
              <defs>
                <linearGradient id={`gradient-${color}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={theme.stroke} stopOpacity={0.25} />
                  <stop offset="100%" stopColor={theme.stroke} stopOpacity={0.0} />
                </linearGradient>
              </defs>
              <Tooltip
                contentStyle={{
                  backgroundColor: '#020617',
                  border: `1px solid ${theme.stroke}`,
                  borderRadius: '6px',
                  fontSize: '9px',
                  fontFamily: 'Inter, sans-serif',
                  padding: '4px 8px',
                  color: '#fff'
                }}
                labelFormatter={(label) => `Khung giờ: ${label}`}
                itemStyle={{ color: '#fff', fontWeight: 'bold' }}
                formatter={(val: number) => [
                  isCurrency ? formatVND(val) : formatNumber(val),
                  title.split(' ')[0]
                ]}
              />
              <Area
                type="monotone"
                dataKey="value"
                stroke={theme.stroke}
                strokeWidth={2}
                fillOpacity={1}
                fill={`url(#gradient-${color})`}
                dot={false}
                activeDot={{ r: 3, strokeWidth: 0, fill: theme.stroke }}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}
export type { KpiSparkPoint };
