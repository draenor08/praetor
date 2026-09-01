import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, ViewEncapsulation, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { renderRichText } from '../../markdown/markdown';

/** Loaded once, on first use, and shared by every instance. */
let katexLib: any | null = null;
let katexLoading: Promise<any> | null = null;

function loadKatex(): Promise<any> {
  if (katexLib) {
    return Promise.resolve(katexLib);
  }
  if (!katexLoading) {
    // Dynamic import: KaTeX is ~280 kB and only pages showing problem text need it, so it ships as
    // its own lazy chunk instead of on the critical path of every login.
    katexLoading = import('katex').then((mod) => {
      katexLib = (mod as any).default ?? mod;
      return katexLib;
    });
  }
  return katexLoading;
}

/**
 * Renders problem text: Markdown plus LaTeX maths. Used for statements, constraints and editorials.
 *
 * <p>The HTML is produced by our own renderer (see markdown.ts), which escapes the author's text
 * before emitting its own closed tag set — so `bypassSecurityTrustHtml` here is trusting code we
 * wrote, not input we received. It has to be bypassed rather than run through Angular's sanitizer
 * because that sanitizer strips the inline styles KaTeX's output is built from, which would leave
 * every formula visibly broken.
 *
 * <p>Encapsulation is off on purpose: the content is inserted with `[innerHTML]`, so it never gets
 * the scoping attribute that component styles select on. Every selector below is prefixed
 * `.rich-text` to keep it from leaking into anything else.
 */
@Component({
  selector: 'app-rich-text',
  standalone: true,
  imports: [CommonModule],
  encapsulation: ViewEncapsulation.None,
  template: `<div class="rich-text" [innerHTML]="html"></div>`,
  styles: [`
    .rich-text > *:first-child { margin-top: 0; }
    .rich-text > *:last-child { margin-bottom: 0; }

    .rich-text .rt-p {
      margin: 0 0 0.8rem;
      line-height: 1.6;
    }

    .rich-text .rt-h {
      margin: 1.1rem 0 0.5rem;
      font-size: 1rem;
      font-weight: 600;
    }

    .rich-text .rt-list {
      margin: 0 0 0.8rem;
      padding-left: 1.4rem;
      line-height: 1.6;
    }

    .rich-text .rt-quote {
      margin: 0 0 0.8rem;
      padding: 0.4rem 0.9rem;
      border-left: 3px solid var(--border-color);
      color: var(--text-soft);
    }

    .rich-text .rt-inline-code,
    .rich-text .rt-math-raw {
      padding: 0.1rem 0.3rem;
      border-radius: 4px;
      background: rgba(148, 163, 184, 0.14);
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.9em;
    }

    .rich-text .rt-code {
      margin: 0 0 0.8rem;
      padding: 0.7rem 0.9rem;
      border: 1px solid var(--border-color);
      border-radius: 6px;
      background: var(--background-color);
      overflow-x: auto;
    }

    .rich-text .rt-code code {
      font-family: 'Consolas', 'Monaco', monospace;
      font-size: 0.85rem;
    }

    .rich-text .rt-link {
      text-decoration: underline;
    }

    /* Display maths can be wider than the pane; scroll it rather than the page. */
    .rich-text .katex-display {
      overflow-x: auto;
      overflow-y: hidden;
      padding: 0.2rem 0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RichTextComponent implements OnChanges {
  private sanitizer = inject(DomSanitizer);

  @Input() source: string | null = '';

  html: SafeHtml = '';

  private cdr = inject(ChangeDetectorRef);

  ngOnChanges(): void {
    // Render immediately with maths as literal TeX, so text appears without waiting on a chunk...
    this.render();
    if (!katexLib && this.hasMath()) {
      // ...then again, with real formulas, once KaTeX arrives.
      // Resolves outside anything Angular tracks, so an OnPush view would keep the pre-maths
      // render forever without being told to look again.
      loadKatex().then(() => {
        this.render();
        this.cdr.markForCheck();
      }).catch(() => undefined);
    }
  }

  private hasMath(): boolean {
    return !!this.source && this.source.includes('$');
  }

  private render(): void {
    const math = katexLib
        ? (tex: string, display: boolean) => this.renderTex(tex, display)
        : undefined;
    this.html = this.sanitizer.bypassSecurityTrustHtml(renderRichText(this.source, math));
  }

  /** A malformed formula shows itself in red instead of taking the page down. */
  private renderTex(tex: string, display: boolean): string {
    return katexLib.renderToString(tex, {
      displayMode: display,
      throwOnError: false,
      output: 'html'
    });
  }
}
