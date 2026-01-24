import MarkdownIt from 'markdown-it'
import mila from 'markdown-it-katex'
import hljs from 'highlight.js/lib/common'

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

// Custom image rendering for styling and anti-hotlinking
md.renderer.rules.image = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const src = token.attrGet('src') || ''
  const alt = token.content
  const title = token.attrGet('title') || ''
  
  // You could add URL rewriting logic here if needed
  
  return `<img src="${src}" alt="${alt}" title="${title}" loading="lazy" referrerpolicy="no-referrer" class="rounded-lg shadow-sm max-w-full h-auto my-4 mx-auto block" />`
}

export function renderMarkdown(content: string) {
  const normalized = content.replace(/\*\*(.+?)\*\*/g, '**\u200b$1\u200b**')
  return md.render(normalized)
}
