import { createElement, useMemo } from 'react'

const formattingTags = new Set(['b', 'strong', 'i', 'em', 'u', 's', 'del', 'br', 'p', 'div', 'span', 'ul', 'ol', 'li', 'sub', 'sup', 'blockquote', 'code', 'pre'])
const discardedTags = new Set(['script', 'style', 'iframe', 'object', 'embed', 'svg', 'math', 'template', 'noscript'])

// Parse inertly and rebuild formatting only: never mount source HTML or copy attributes.
function formattedQuote(quote) {
  const template = document.createElement('template')
  template.innerHTML = quote
  function render(node, key, depth = 0) {
    if (node.nodeType === 3) return node.textContent
    if (node.nodeType !== 1) return null
    const tag = node.localName
    if (discardedTags.has(tag)) return null
    if (depth >= 40) return node.textContent
    if (tag === 'br') return createElement('br', { key })
    const children = renderChildren(node, key, depth + 1)
    if (!formattingTags.has(tag)) return children
    return createElement(tag, { key }, children)
  }

  function renderChildren(parent, key, depth) {
    // Imported quotes can mark the same line break with both HTML and a newline.
    for (const node of parent.childNodes) {
      if (node.nodeType !== 1 || node.localName !== 'br') continue
      const next = node.nextSibling
      const previous = node.previousSibling
      if (next?.nodeType === 3 && /^[\t ]*\n/.test(next.data)) {
        next.data = next.data.replace(/^[\t ]*\n/, '')
      } else if (previous?.nodeType === 3) {
        previous.data = previous.data.replace(/\n[\t ]*$/, '')
      }
    }
    return Array.from(parent.childNodes, (node, index) => render(node, `${key}-${index}`, depth))
  }
  return renderChildren(template.content, 'quote', 0)
}

export default function QuoteContent({ quote, ...props }) {
  const content = useMemo(() => formattedQuote(String(quote ?? '')), [quote])
  return <blockquote {...props} lang="pl" className={`quote-content ${props.className || ''}`.trim()}>{content}</blockquote>
}
