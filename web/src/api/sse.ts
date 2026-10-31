import { authHeaders, buildUrl, toHttpError } from './client';
import { handleUnauthorized } from './auth';
import type { AppliedRule, Conditions, DocumentVersion, DonePayload, ToolStep } from './types';

// POST + Authorization 헤더가 필요해 EventSource 대신 fetch + ReadableStream 으로 SSE 를 읽는다.

export type StreamEvent =
  | { event: 'meta'; data: { userTurnId: string; conversationId: string } }
  | { event: 'context'; data: { rules: AppliedRule[] } }
  | { event: 'thinking'; data: { text: string } }
  | { event: 'tool'; data: ToolStep }
  | { event: 'text'; data: { text: string } }
  | { event: 'document'; data: DocumentVersion }
  | { event: 'done'; data: DonePayload }
  | { event: 'error'; data: { code: string; message: string } };

/** SSE 텍스트 조각을 이벤트 단위로 자른다. 줄바꿈은 \n, \r\n 모두 허용, ':' 로 시작하는 주석(ping)은 버린다. */
export class SseParser {
  private buffer = '';

  push(chunk: string): { event: string; data: string }[] {
    this.buffer += chunk.replace(/\r\n/g, '\n');
    const out: { event: string; data: string }[] = [];
    let idx: number;
    while ((idx = this.buffer.indexOf('\n\n')) >= 0) {
      const raw = this.buffer.slice(0, idx);
      this.buffer = this.buffer.slice(idx + 2);
      let event = 'message';
      const data: string[] = [];
      for (const line of raw.split('\n')) {
        if (!line || line.startsWith(':')) continue;
        const colon = line.indexOf(':');
        const field = colon < 0 ? line : line.slice(0, colon);
        let value = colon < 0 ? '' : line.slice(colon + 1);
        if (value.startsWith(' ')) value = value.slice(1);
        if (field === 'event') event = value;
        else if (field === 'data') data.push(value);
      }
      if (data.length) out.push({ event, data: data.join('\n') });
    }
    return out;
  }
}

export async function streamMessage(
  conversationId: string,
  body: { text: string; conditions: Conditions },
  onEvent: (e: StreamEvent) => void,
  signal: AbortSignal,
): Promise<void> {
  const res = await fetch(buildUrl(`/conversations/${conversationId}/messages`), {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json', Accept: 'text/event-stream' }),
    body: JSON.stringify(body),
    signal,
  });
  if (res.status === 401) {
    handleUnauthorized();
    throw await toHttpError(res);
  }
  if (!res.ok) throw await toHttpError(res);
  if (!res.body) throw new Error('스트리밍 응답을 받을 수 없습니다');

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  const parser = new SseParser();
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      for (const { event, data } of parser.push(decoder.decode(value, { stream: true }))) {
        let parsed: unknown;
        try {
          parsed = JSON.parse(data);
        } catch {
          continue;
        }
        onEvent({ event, data: parsed } as StreamEvent);
      }
    }
  } finally {
    reader.releaseLock();
  }
}
