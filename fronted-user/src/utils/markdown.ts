import MarkdownIt from 'markdown-it'
import mila from 'markdown-it-katex'
import hljs from 'highlight.js/lib/common'
import { rewriteUrl } from './url-rewrites'
import { unescapeUrl } from './text'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true
})

md.use(mila)

const renderCodeBlock = (code: string, language?: string) => {
  const lang = (language || '').trim()
  const hasLang = !!lang
  const validLang = !!(lang && hljs.getLanguage(lang))
  const highlighted = validLang ? hljs.highlight(code, { language: lang }).value : hljs.highlightAuto(code).value
  const encoded = encodeURIComponent(code)
  return `
    <div class="md-codeblock">
      <div class="md-codeblock__header" style="margin: 0; padding: 0px 6px;">
        ${hasLang ? `<span class="md-codeblock__lang">${lang}</span>` : '<span></span>'}
        <button
          type="button"
          class="md-codeblock__copy"
          data-code="${encoded}"
          onclick="navigator.clipboard?.writeText(decodeURIComponent(this.dataset.code || ''))"
        >复制</button>
      </div>
      <pre><code class="hljs ${validLang ? 'language-' + lang : ''}">${highlighted}</code></pre>
    </div>
  `.trim()
}

md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const info = token.info ? token.info.trim().split(/\s+/)[0] : ''
  return renderCodeBlock(token.content, info)
}

md.renderer.rules.code_block = (tokens, idx) => {
  const token = tokens[idx]
  return renderCodeBlock(token.content)
}

const defaultLinkOpen = md.renderer.rules.link_open || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  token.attrSet('class', 'md-link text-primary underline underline-offset-4')
  token.attrSet('target', '_blank')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

const createSlugger = () => {
  const counts = new Map<string, number>()
  return (value: string) => {
    const base = value
      .trim()
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^\w\u4e00-\u9fa5-]/g, '')
    const slug = base || 'section'
    const count = counts.get(slug) ?? 0
    counts.set(slug, count + 1)
    return count ? `${slug}-${count}` : slug
  }
}

const defaultHeadingOpen =
  md.renderer.rules.heading_open || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const inlineToken = tokens[idx + 1]
  const text = inlineToken?.type === 'inline' ? inlineToken.content : ''
  const slugger = (env as { slugger?: (value: string) => string })?.slugger ?? createSlugger()
  token.attrSet('id', slugger(text))
  return defaultHeadingOpen(tokens, idx, options, env, self)
}

// Custom image rendering for styling and anti-hotlinking
md.renderer.rules.image = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const src = token.attrGet('src') || ''
  const alt = token.content
  const title = token.attrGet('title') || ''

  const normalizedSrc = rewriteUrl(unescapeUrl(src), true)

  return `<img src="${normalizedSrc}" alt="${alt}" title="${title}" loading="lazy" referrerpolicy="no-referrer" class="rounded-lg shadow-sm max-w-full h-auto my-4 mx-auto block" />`
}

export function renderMarkdown(content: string) {
  const normalized = content.replace(/\*\*(.+?)\*\*/g, '**\u200b$1\u200b**')
  const env = { slugger: createSlugger() }
  return md.render(normalized, env)
}

export type MarkdownHeading = {
  id: string
  text: string
  level: number
}

export function extractHeadings(content: string): MarkdownHeading[] {
  const normalized = content.replace(/\*\*(.+?)\*\*/g, '**\u200b$1\u200b**')
  const slugger = createSlugger()
  const tokens = md.parse(normalized, {})
  const headings: MarkdownHeading[] = []
  for (let i = 0; i < tokens.length; i += 1) {
    const token = tokens[i]
    if (token.type === 'heading_open') {
      const inlineToken = tokens[i + 1]
      const text = inlineToken?.type === 'inline' ? inlineToken.content : ''
      const level = Number(token.tag.replace('h', ''))
      if (text) {
        headings.push({
          id: slugger(text),
          text,
          level
        })
      }
    }
  }
  return headings
}
