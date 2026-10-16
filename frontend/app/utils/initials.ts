/** "JM" for "Javier Melero", for people without an avatar (FR-8.2). */
export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return '?'
  const letters =
    words.length === 1
      ? Array.from(words[0]!).slice(0, 2)
      : [words[0]!, words[1]!].map((w) => Array.from(w)[0]!)
  return letters.join('').toUpperCase()
}
