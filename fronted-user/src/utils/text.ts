export function formatOverview(text: string) {
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  return escaped.replace(/\*\*(.+?)\*\*/g, '<strong class="overview-strong">$1</strong>')
}

export function unescapeUrl(url: string) {
  if (!url) return ''
  return url.replace(/&amp;/g, '&')
}
