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
    const children = Array.from(node.childNodes, (child, index) => render(child, `${key}-${index}`, depth + 1))
    if (!formattingTags.has(tag)) return children
    return tag === 'br' ? createElement('br', { key }) : createElement(tag, { key }, children)
  }
  return Array.from(template.content.childNodes, (node, index) => render(node, String(index)))
}

export default function QuoteContent({ quote, ...props }) {
  const content = useMemo(() => formattedQuote(String(quote ?? '')), [quote])
  return <blockquote {...props} className={`quote-content ${props.className || ''}`.trim()}>{content}</blockquote>
}
