const VN_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export function formatTime(dateStr: string): string {
  const d = new Date(dateStr);
  return `${d.toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: VN_TIME_ZONE,
    hour12: false
  })} GMT+7`;
}

export function formatDate(date: Date): string {
  return date.toLocaleDateString('vi-VN', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    timeZone: VN_TIME_ZONE
  });
}

export function getMinutesDifference(isoString: string): number {
  const diffMs = Date.now() - new Date(isoString).getTime();
  return Math.floor(diffMs / (60 * 1000));
}
