import katex from 'katex';
import 'katex/dist/katex.min.css';
import clsx from 'clsx';
import { useMemo } from 'react';

// 마크다운 일부(문단, 줄바꿈, **굵게**, 글머리 목록) + $인라인$ / $$블록$$ LaTeX를 HTML로 바꿉니다.
// 모든 사용자·모델 텍스트는 먼저 이스케이프한 뒤, 수식만 KaTeX 출력으로 다시 끼워 넣습니다.

export function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderTex(tex: string, display: boolean): string {
  return katex.renderToString(tex, { displayMode: display, throwOnError: false, strict: 'ignore', output: 'html' });
}

type Segment = { kind: 'text'; value: string } | { kind: 'math'; value: string; display: boolean };

/** $$…$$, $…$ 을 분리. \$ 는 문자 그대로. 닫히지 않은 $ 는 텍스트로 둡니다(스트리밍 중 반쪽 수식). */
export function splitMath(src: string): Segment[] {
  const out: Segment[] = [];
  let buf = '';
  let i = 0;
  while (i < src.length) {
    const ch = src[i];
    if (ch === '\\' && src[i + 1] === '$') {
      buf += '$';
      i += 2;
      continue;
    }
    if (ch === '$') {
      const display = src[i + 1] === '$';
      const open = display ? 2 : 1;
      const closeToken = display ? '$$' : '$';
      let j = i + open;
      let end = -1;
      while (j < src.length) {
        if (src[j] === '\\') {
          j += 2;
          continue;
        }
        if (src.startsWith(closeToken, j)) {
          end = j;
          break;
        }
        if (!display && src[j] === '\n' && src[j + 1] === '\n') break;
        j++;
      }
      const body = end >= 0 ? src.slice(i + open, end) : '';
      if (end >= 0 && body.trim()) {
        if (buf) out.push({ kind: 'text', value: buf });
        buf = '';
        out.push({ kind: 'math', value: body, display });
        i = end + closeToken.length;
        continue;
      }
      buf += ch;
      i++;
      continue;
    }
    buf += ch;
    i++;
  }
  if (buf) out.push({ kind: 'text', value: buf });
  return out;
}

const MATH_TOKEN = '\u0000M';

/** 한 블록(문단·목록 항목) 안의 인라인 서식: 이스케이프 → **굵게** → `코드` */
function inlineFormat(escaped: string): string {
  return escaped
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`\n]+)`/g, '<code class="px-1 rounded bg-slate-100 text-[0.92em]">$1</code>');
}

export function renderRichText(src: string): string {
  if (!src) return '';
  const segments = splitMath(src);
  const rendered: string[] = [];
  let plain = '';
  for (const seg of segments) {
    if (seg.kind === 'text') {
      plain += seg.value;
    } else {
      plain += `${MATH_TOKEN}${rendered.length}\u0000`;
      rendered.push(
        seg.display
          ? `<span class="block my-2 overflow-x-auto">${renderTex(seg.value, true)}</span>`
          : renderTex(seg.value, false),
      );
    }
  }
  const restore = (html: string) =>
    html.replace(/\u0000M(\d+)\u0000/g, (_, n: string) => rendered[Number(n)] ?? '');

  const blocks = plain.split(/\n{2,}/);
  const html = blocks
    .map((block) => {
      const lines = block.split('\n');
      const isList = lines.every((l) => /^\s*([-*•]|\d+[.)])\s+/.test(l) || !l.trim());
      if (isList && lines.some((l) => l.trim())) {
        const ordered = lines.some((l) => /^\s*\d+[.)]\s+/.test(l));
        const items = lines
          .filter((l) => l.trim())
          .map((l) => `<li>${inlineFormat(escapeHtml(l.replace(/^\s*([-*•]|\d+[.)])\s+/, '')))}</li>`)
          .join('');
        return ordered
          ? `<ol class="list-decimal pl-5 space-y-0.5">${items}</ol>`
          : `<ul class="list-disc pl-5 space-y-0.5">${items}</ul>`;
      }
      const heading = /^(#{1,4})\s+(.*)$/.exec(block.trim());
      if (heading && lines.length === 1) {
        return `<p class="font-semibold text-slate-800">${inlineFormat(escapeHtml(heading[2]))}</p>`;
      }
      return `<p>${lines.map((l) => inlineFormat(escapeHtml(l))).join('<br/>')}</p>`;
    })
    .join('');
  return restore(html);
}

export function RichText({ text, className }: { text: string; className?: string }) {
  const html = useMemo(() => renderRichText(text), [text]);
  return <div className={clsx('rich-text', className)} dangerouslySetInnerHTML={{ __html: html }} />;
}
