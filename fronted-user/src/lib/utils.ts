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
 * 从链接生成 unavatar.io 图标 URL
 * @param link - RSS 源的链接
 * @returns unavatar.io 图标 URL
 */
export function getIconUrl(link: string | null | undefined): string | null {
  if (!link) return null
  const domain = extractDomain(link)
  if (!domain) return null
  return `https://unavatar.io/${domain}`
}
