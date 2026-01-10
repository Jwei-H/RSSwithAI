export interface UrlRewriteRule {
  /**
   * 匹配的域名或 URL 模式 (支持字符串或正则)
   */
  match: string | RegExp;
  
  /**
   * 替换后的字符串
   * 如果 match 是正则，此处可以使用 $1, $2 等引用
   */
  replacement: string;

  /**
   * 是否只匹配图片 URL (默认 true)
   */
  imageOnly?: boolean;
}

/**
 * URL 重写配置列表
 */
export const urlRewriteRules: UrlRewriteRule[] = [
  {
    // 少数派 (sspai) CDN 替换
    // cdnfile.sspai.com -> img.430503.xyz
    match: 'https://cdnfile.sspai.com',
    replacement: 'https://img.430503.xyz/',
    imageOnly: true
  }
];

/**
 * 重写 URL 函数
 * @param url 原始 URL
 * @param isImage 是否是图片链接
 * @returns 重写后的 URL
 */
export function rewriteUrl(url: string, isImage: boolean = true): string {
  if (!url) return url;

  for (const rule of urlRewriteRules) {
    if (rule.imageOnly !== false && !isImage) {
      continue;
    }

    if (rule.match instanceof RegExp) {
      if (rule.match.test(url)) {
        return url.replace(rule.match, rule.replacement);
      }
    } else {
      if (url.includes(rule.match)) {
        return url.replace(rule.match, rule.replacement);
      }
    }
  }

  return url;
}
