export const chartColors = {
  green: {
    text: 'text-emerald-400',
    border: 'border-emerald-500/25',
    stroke: '#10b981',
    fill: '#10b981',
    glow: 'from-emerald-500/10'
  },
  blue: {
    text: 'text-blue-400',
    border: 'border-blue-500/25',
    stroke: '#3b82f6',
    fill: '#3b82f6',
    glow: 'from-blue-500/10'
  },
  purple: {
    text: 'text-purple-400',
    border: 'border-purple-500/25',
    stroke: '#a855f7',
    fill: '#a855f7',
    glow: 'from-purple-500/10'
  },
  red: {
    text: 'text-rose-400',
    border: 'border-rose-500/25',
    stroke: '#f43f5e',
    fill: '#f43f5e',
    glow: 'from-rose-500/10'
  },
  orange: {
    text: 'text-orange-400',
    border: 'border-orange-500/25',
    stroke: '#f97316',
    fill: '#f97316',
    glow: 'from-orange-500/10'
  },
  cyan: {
    text: 'text-cyan-400',
    border: 'border-cyan-500/25',
    stroke: '#06b6d4',
    fill: '#06b6d4',
    glow: 'from-cyan-500/10'
  }
};
export type ChartColorKey = keyof typeof chartColors;
