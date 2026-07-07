import { formatVND } from '../../utils/formatCurrency';
import { formatNumber } from '../../utils/formatNumber';

interface MiniCard {
  cardId: string;
  name: string;
  title: string;
  note?: string;
  availabilityType: string;
  valueField: string;
  unit: string;
}

interface MiniMetricGridProps {
  cards: MiniCard[];
  data: Record<string, any>;
}

export default function MiniMetricGrid({ cards, data }: MiniMetricGridProps) {
  const getAvailabilityClass = (type: string) => {
    switch (type) {
      case 'direct_api':
        return 'text-emerald-400 border-emerald-500/20 bg-emerald-500/5';
      case 'partial_api':
        return 'text-sky-400 border-sky-500/20 bg-sky-500/5';
      case 'requires_tracking':
        return 'text-purple-400 border-purple-500/20 bg-purple-500/5';
      case 'requires_internal_mapping':
        return 'text-amber-400 border-amber-500/20 bg-amber-500/5';
      default:
        return 'text-slate-400 border-slate-500/20 bg-slate-500/5';
    }
  };

  return (
    <div className="grid grid-cols-2 grid-rows-2 gap-2 h-full">
      {cards.map((card) => {
        const rawValue = data[card.valueField] ?? 0;
        const isMer = card.title.toLowerCase().includes('mer');
        const isRoas = card.title.toLowerCase().includes('roas');
        const isMultiplier = isRoas || isMer || card.unit === 'x';
        const isPercent = card.unit === '%';
        const isCurrency = card.unit === 'đ' || card.title.toLowerCase().includes('lợi nhuận');

        const formatted = isCurrency
          ? formatVND(rawValue)
          : isMultiplier
            ? `${Number(rawValue).toFixed(2)}x`
            : isPercent
              ? `${rawValue}%`
              : formatNumber(rawValue);

        return (
          <div
            key={card.cardId}
            className="rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-3.5 2xl:p-5 flex flex-col justify-between hover:border-slate-700/80 transition duration-300 shadow-md shadow-slate-950/10"
          >
            <div className="flex justify-between items-start gap-1">
              <span className="text-[9px] 2xl:text-xs font-extrabold text-slate-400 tracking-wider uppercase font-display">
                {card.title} {card.note && <span className="text-[7px] 2xl:text-[10px] text-slate-500 font-bold font-body">• {card.note}</span>}
              </span>
            </div>

            <div className="my-2">
              <span className="text-xl 2xl:text-3xl font-extrabold tracking-tight text-white font-display">
                {formatted}
              </span>
            </div>

            <div className="flex justify-between items-center text-[7px] 2xl:text-[10px] text-slate-500 font-mono">
              <span className={`px-1 py-0.5 rounded border uppercase ${getAvailabilityClass(card.availabilityType)}`}>
                {card.availabilityType.split('_')[0]} API
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
