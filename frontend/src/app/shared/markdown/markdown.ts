/**
 * A small, deliberately-bounded Markdown + LaTeX renderer for problem text.
 *
 * <b>Why hand-written rather than a library.</b> The safety property here is structural: the source
 * is authored by setters and rendered as HTML, so nothing the author writes may reach the browser as
 * markup. This renderer escapes the source FIRST and then emits a closed set of tags of its own, so
 * author HTML cannot survive by construction. A general library does the opposite — it passes raw
 * HTML through — which then needs a sanitizer bolted on, and Angular's own sanitizer strips the
 * inline styles KaTeX's output depends on, so that route breaks the maths instead.
 *
 * <b>Supported</b>: `#`/`##`/`###` headings · `**bold**` · `*italic*` / `_italic_` · `` `code` `` ·
 * fenced ``` blocks · `-`/`*` bullet lists · `1.` ordered lists · `> quote` · `[text](url)` for
 * http/https/mailto/root-relative URLs only · `$inline maths$` · `$$display maths$$`.
 * A single newline inside a paragraph is a line break — problem statements are written in a plain
 * textarea, where that is what people mean.
 *
 * Everything else is literal text, including any HTML the author typed.
 */

/** Renders TeX to HTML. Supplied by the caller so this file stays free of the KaTeX import. */
export type MathRenderer = (tex: string, display: boolean) => string;

/**
 * Private-use characters: they survive escaping and carry no Markdown meaning. Two pairs, because a
 * placeholder standing for a BLOCK element (a fenced code block, a display formula) must not end up
 * wrapped in a `<p>` — `<pre>` inside `<p>` is invalid, and the browser silently restructures it.
 */
const MARK_OPEN = '\uE000';
const MARK_CLOSE = '\uE001';
const BLOCK_OPEN = '\uE002';
const BLOCK_CLOSE = '\uE003';

const BLOCK_SLOT = new RegExp(`^${BLOCK_OPEN}\\d+${BLOCK_CLOSE}$`);

export function renderRichText(source: string | null | undefined, math?: MathRenderer): string {
  if (!source || !source.trim()) {
    return '';
  }

  const slots: string[] = [];
  let text = source.replace(/\r\n?/g, '\n');

  // Order matters. Code comes out before maths, so a `$` inside code stays a dollar sign; maths
  // comes out before escaping, because KaTeX needs the raw TeX (`$a < b$` must not arrive as
  // `&lt;`), and before the inline pass, so `*` in TeX is not read as emphasis.
  text = extract(text, /```[ \t]*([\w+-]*)[ \t]*\n([\s\S]*?)```/g, slots,
      (m) => `<pre class="rt-code"><code>${escapeHtml(m[2].replace(/\n$/, ''))}</code></pre>`, true);
  text = extract(text, /`([^`\n]+)`/g, slots, (m) => `<code class="rt-inline-code">${escapeHtml(m[1])}</code>`);
  text = extract(text, /\$\$([\s\S]+?)\$\$/g, slots, (m) => renderMath(m[1], true, math), true);
  text = extract(text, /\$([^$\n]+?)\$/g, slots, (m) => renderMath(m[1], false, math));

  text = escapeHtml(text);

  const html = text
      .split(/\n{2,}/)
      .map((block) => renderBlock(block.trim()))
      .filter((block) => block.length > 0)
      .join('\n');

  return fill(html, slots);
}

// --- blocks ---------------------------------------------------------------

function renderBlock(block: string): string {
  if (!block) {
    return '';
  }

  const heading = /^(#{1,3})\s+(.*)$/.exec(block);
  if (heading && !block.includes('\n')) {
    const level = heading[1].length + 2; // '#' is h3 — the page owns h1/h2
    return `<h${level} class="rt-h">${inline(heading[2])}</h${level}>`;
  }

  const lines = block.split('\n');

  if (lines.every((l) => /^\s*[-*]\s+/.test(l))) {
    const items = lines.map((l) => `<li>${inline(l.replace(/^\s*[-*]\s+/, ''))}</li>`).join('');
    return `<ul class="rt-list">${items}</ul>`;
  }

  if (lines.every((l) => /^\s*\d+[.)]\s+/.test(l))) {
    const items = lines.map((l) => `<li>${inline(l.replace(/^\s*\d+[.)]\s+/, ''))}</li>`).join('');
    return `<ol class="rt-list">${items}</ol>`;
  }

  if (lines.every((l) => /^\s*&gt;\s?/.test(l))) {
    const body = lines.map((l) => inline(l.replace(/^\s*&gt;\s?/, ''))).join('<br>');
    return `<blockquote class="rt-quote">${body}</blockquote>`;
  }

  // A paragraph may still contain a block placeholder on its own line (a fence written directly
  // under its intro line, with no blank line between). Emit those bare and wrap only the prose runs.
  const out: string[] = [];
  let paragraph: string[] = [];
  const flush = () => {
    if (paragraph.length > 0) {
      out.push(`<p class="rt-p">${paragraph.map(inline).join('<br>')}</p>`);
      paragraph = [];
    }
  };
  for (const line of lines) {
    if (BLOCK_SLOT.test(line.trim())) {
      flush();
      out.push(line.trim());
    } else {
      paragraph.push(line);
    }
  }
  flush();
  return out.join('\n');
}

// --- inline ---------------------------------------------------------------

function inline(text: string): string {
  let out = text;
  // Links: the URL is checked against a scheme allow-list, and a rejected one degrades to its label
  // rather than to a live `javascript:` href.
  out = out.replace(/\[([^\]\n]+)\]\(([^)\s]+)\)/g, (_m, label: string, url: string) =>
      isSafeUrl(url)
          ? `<a class="rt-link" href="${url}" target="_blank" rel="noopener noreferrer">${label}</a>`
          // Unsupported scheme: show the author their own markup verbatim, so it reads as "this did
          // not become a link" rather than as mangled prose.
          : `[${label}](${url})`);
  out = out.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>');
  out = out.replace(/(^|[^\w*])\*([^*\n]+)\*(?=[^\w*]|$)/g, '$1<em>$2</em>');
  out = out.replace(/(^|[^\w_])_([^_\n]+)_(?=[^\w_]|$)/g, '$1<em>$2</em>');
  return out;
}

/**
 * Escaping has already run, so a URL arrives with `&` as `&amp;` (correct inside an attribute) and
 * quotes as entities (so it cannot close the attribute). Only the scheme still needs checking.
 */
function isSafeUrl(url: string): boolean {
  return /^(https?:\/\/|mailto:|\/)/i.test(url);
}

// --- helpers --------------------------------------------------------------

function escapeHtml(text: string): string {
  return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
}

/**
 * Replace every match with a placeholder, parking the rendered HTML in `slots`. `block` marks
 * placeholders whose HTML is a block element, so the block parser can leave them unwrapped.
 */
function extract(text: string, pattern: RegExp, slots: string[],
                 render: (m: RegExpExecArray) => string, block = false): string {
  return text.replace(pattern, (...args) => {
    const m = args.slice(0, -2) as unknown as RegExpExecArray;
    slots.push(render(m));
    const i = slots.length - 1;
    return block ? `${BLOCK_OPEN}${i}${BLOCK_CLOSE}` : `${MARK_OPEN}${i}${MARK_CLOSE}`;
  });
}

function fill(html: string, slots: string[]): string {
  return html.replace(
      new RegExp(`[${MARK_OPEN}${BLOCK_OPEN}](\\d+)[${MARK_CLOSE}${BLOCK_CLOSE}]`, 'g'),
      (_m, i: string) => slots[Number(i)] ?? '');
}

/**
 * With KaTeX loaded, real maths. Without it — the chunk is still in flight, or failed to load — the
 * TeX shows as its own source, which is readable, rather than as an empty gap.
 */
function renderMath(tex: string, display: boolean, math?: MathRenderer): string {
  if (!math) {
    const literal = escapeHtml(display ? `$$${tex}$$` : `$${tex}$`);
    return `<code class="rt-math-raw">${literal}</code>`;
  }
  return math(tex, display);
}
