import type { ClassValue } from "clsx"
import { clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * 从 URL 中提取域名
 * @param url - 完整的 URL，如 https://example.com/path
 * @returns 域名，如 example.com
 */
export function extractDomain(url: string): string {
  try {
    const urlObj = new URL(url)
    return urlObj.hostname
  } catch {
    return ''
  }
}

/**
 * 从链接生成网站图标 URL（取第一个候选地址）
 * @param link - RSS 源的链接
 * @returns favicon URL，或 null
 */
export function getIconUrl(link: string | null | undefined): string | null {
  const candidates = getFaviconCandidates(link)
  return candidates[0] ?? null
}

/**
 * 生成多个候选 favicon URL，按成功率从高到低排列
 * 供前端逐一尝试，全部失败时降级到文字占位
 *
 * 候选顺序：
 *   1. /favicon.ico           — 最通用
 *   2. /favicon.png           — 部分站点使用 PNG
 *   3. /apple-touch-icon.png  — 分辨率高，现代站点常见
 *   4. www.{domain}/favicon.ico — 裸域名 404 时尝试带 www
 */
export function getFaviconCandidates(link: string | null | undefined): string[] {
  if (!link) return []
  const domain = extractDomain(link)
  if (!domain) return []

  const candidates: string[] = [
    `https://${domain}/favicon.ico`,
    `https://${domain}/favicon.png`,
    `https://${domain}/apple-touch-icon.png`,
  ]

  // 若域名未带 www，额外补充 www 变体
  if (!domain.startsWith('www.')) {
    candidates.push(`https://www.${domain}/favicon.ico`)
  }

  return candidates
}

/**
 * 根据域名生成文字占位图标的样式（用于 favicon 加载失败时的 fallback）
 * @param domain - 域名
 * @returns { letter, bgColor } 首字母和背景色
 */
export function getFaviconFallback(domain: string): { letter: string; bgColor: string } {
  const letter = domain.replace(/^www\./, '').charAt(0).toUpperCase() || '?'
  // 用域名哈希生成稳定的颜色，避免每次渲染颜色都变
  const colors = [
    '#4f46e5', '#7c3aed', '#db2777', '#dc2626',
    '#d97706', '#16a34a', '#0891b2', '#0284c7',
  ]
  let hash = 0
  for (let i = 0; i < domain.length; i++) hash = domain.charCodeAt(i) + ((hash << 5) - hash)
  const bgColor = colors[Math.abs(hash) % colors.length]!
  return { letter, bgColor }
}
