const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
]

/** "2 hours ago", "in 3 days", "just now" (NFR-7). English only. */
export function relativeTime(date: Date | string, now: Date = new Date()): string {
  const seconds = Math.round((new Date(date).getTime() - now.getTime()) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 45) return 'just now'
  const formatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
  for (const [unit, size] of UNITS) {
    if (abs >= size) return formatter.format(Math.round(seconds / size), unit)
  }
  return formatter.format(Math.round(seconds / 60), 'minute')
}

/**
 * The same instant everywhere, whoever renders it: used for the server render, and so the first
 * client render matches it. The browser then swaps in the visitor's own time zone.
 */
export function absoluteDateUtc(date: Date | string): string {
  const d = new Date(date)
  const month = MONTHS[d.getUTCMonth()]
  const hours = d.getUTCHours() % 12 || 12
  const minutes = String(d.getUTCMinutes()).padStart(2, '0')
  const period = d.getUTCHours() < 12 ? 'AM' : 'PM'
  // Built by hand, not with Intl: its output differs between engines in small ways (the space
  // before "PM" is a narrow no-break one in some), and the server and browser must agree.
  return `${month} ${d.getUTCDate()}, ${d.getUTCFullYear()}, ${hours}:${minutes} ${period} UTC`
}

/** The full date in the visitor's time zone, for the tooltip (NFR-7). */
export function fullDate(date: Date | string): string {
  return new Intl.DateTimeFormat('en-US', { dateStyle: 'long', timeStyle: 'short' }).format(
    new Date(date),
  )
}
