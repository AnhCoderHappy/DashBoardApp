export default function DashboardSkeleton() {
  return (
    <div className="min-h-screen 2xl:h-screen 2xl:overflow-hidden bg-gradient-to-br from-slate-950 via-[#0a0f1d] to-[#030712] p-4 flex flex-col gap-2.5 select-none text-slate-500">
      {/* Header Skeleton */}
      <div className="flex items-center justify-between border border-slate-800/80 bg-slate-900/60 rounded-xl p-3.5 h-[62px] 2xl:h-[76px]">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-slate-800 rounded-lg" />
          <div className="flex flex-col gap-1">
            <div className="w-28 h-3.5 bg-slate-800 rounded" />
            <div className="w-48 h-2 bg-slate-800 rounded" />
          </div>
        </div>
        <div className="flex items-center gap-4">
          <div className="w-20 h-4 bg-slate-800 rounded" />
          <div className="w-32 h-3 bg-slate-800 rounded" />
          <div className="w-6 h-6 bg-slate-800 rounded" />
        </div>
      </div>

      {/* Row 1: KPI Skeleton Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2.5">
        {[...Array(5)].map((_, i) => (
          <div key={i} className="h-[175px] 2xl:h-[220px] rounded-xl border border-slate-800/60 bg-slate-900/40 p-5 flex flex-col justify-between">
            <div className="flex justify-between items-start">
              <div className="w-24 h-2.5 bg-slate-800 rounded" />
              <div className="w-12 h-4 bg-slate-800 rounded" />
            </div>
            <div className="w-28 h-7 bg-slate-800 rounded my-2" />
            <div className="w-20 h-3 bg-slate-800 rounded" />
          </div>
        ))}
      </div>

      {/* Row 2: Sales Channels, Ads and Mini Metrics */}
      <div className="grid grid-cols-1 lg:grid-cols-[33%_33%_34%] gap-2.5 2xl:flex-1 2xl:min-h-0">
        {/* Table Card Skeleton */}
        <div className="h-[210px] 2xl:h-auto rounded-xl border border-slate-800/60 bg-slate-900/40 p-5 flex flex-col justify-between">
          <div className="w-36 h-3 bg-slate-800 rounded mb-4" />
          <div className="space-y-3 flex-1">
            <div className="w-full h-2 bg-slate-800 rounded" />
            <div className="w-full h-8 bg-slate-800 rounded" />
            <div className="w-full h-8 bg-slate-800 rounded" />
          </div>
        </div>
        {/* Donut Card Skeleton */}
        <div className="h-[210px] 2xl:h-auto rounded-xl border border-slate-800/60 bg-slate-900/40 p-5 flex flex-col justify-between">
          <div className="w-32 h-3 bg-slate-800 rounded mb-4" />
          <div className="flex items-center justify-between gap-4 flex-1">
            <div className="w-[110px] h-[110px] rounded-full border-8 border-slate-800 flex items-center justify-center shrink-0" />
            <div className="flex-1 space-y-2">
              <div className="w-full h-3 bg-slate-800 rounded" />
              <div className="w-2/3 h-3 bg-slate-800 rounded" />
              <div className="w-1/2 h-3 bg-slate-800 rounded" />
            </div>
          </div>
        </div>
        {/* Mini Grid Skeleton */}
        <div className="grid grid-cols-2 grid-rows-2 gap-2 h-[210px] 2xl:h-auto">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="rounded-xl border border-slate-800/60 bg-slate-900/40 p-3.5 flex flex-col justify-between">
              <div className="w-16 h-2 bg-slate-800 rounded" />
              <div className="w-20 h-5 bg-slate-800 rounded my-2" />
              <div className="w-12 h-3 bg-slate-800 rounded" />
            </div>
          ))}
        </div>
      </div>

      {/* Row 3: Hourly Revenue, Realtime Orders and Hourly Ad Cost */}
      <div className="grid grid-cols-1 lg:grid-cols-[33%_33%_34%] gap-2.5 2xl:flex-1 2xl:min-h-0">
        {[...Array(3)].map((_, i) => (
          <div key={i} className="h-[210px] 2xl:h-auto rounded-xl border border-slate-800/60 bg-slate-900/40 p-5 flex flex-col justify-between">
            <div className="w-36 h-3 bg-slate-800 rounded mb-4" />
            <div className="w-full h-24 bg-slate-800 rounded flex-1" />
          </div>
        ))}
      </div>

      {/* Row 4: Top Products, Campaign Table and Revenue Share Donut */}
      <div className="grid grid-cols-1 lg:grid-cols-[33%_33%_34%] gap-2.5 2xl:flex-1 2xl:min-h-0">
        {[...Array(3)].map((_, i) => (
          <div key={i} className="h-[210px] 2xl:h-auto rounded-xl border border-slate-800/60 bg-slate-900/40 p-5 flex flex-col justify-between">
            <div className="w-36 h-3 bg-slate-800 rounded mb-4" />
            <div className="w-full h-24 bg-slate-800/50 rounded flex-1" />
          </div>
        ))}
      </div>

      {/* Footer Skeleton */}
      <div className="border border-slate-800/80 bg-slate-900/60 rounded-xl p-3.5 flex flex-col md:flex-row items-center justify-between gap-3 h-[50px] 2xl:h-[60px]">
        <div className="flex gap-4">
          <div className="w-24 h-3 bg-slate-800 rounded" />
          <div className="w-24 h-3 bg-slate-800 rounded" />
          <div className="w-24 h-3 bg-slate-800 rounded" />
        </div>
        <div className="w-48 h-3 bg-slate-800 rounded" />
      </div>
    </div>
  );
}
