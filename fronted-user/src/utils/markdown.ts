import MarkdownIt from 'markdown-it'
import mila from 'markdown-it-katex'
import hljs from 'highlight.js'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight: (code, language) => {
    const validLang = !!(language && hljs.getLanguage(language))
    const highlighted = validLang
      ? hljs.highlight(code, { language }).value
      : hljs.highlightAuto(code).value
    return `<pre><code class="hljs ${validLang ? 'language-' + language : ''}">${highlighted}</code></pre>`
  }
})

md.use(mila)

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
