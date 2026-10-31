# edufactory-web

EDU FACTORY 웹 (React 18 + TypeScript + Vite 5 + Tailwind 3 + react-query 5 + KaTeX). API 계약은 `../docs/API.md`.

```bash
npm install
npm run dev                      # http://localhost:3010 (API 기본 http://localhost:18600/api)
VITE_API_BASE_URL=https://.../api npm run build   # tsc && vite build → build/
```

- 로그인 토큰은 localStorage(`edufactory.token`)에 두고 `Authorization: Bearer` 로 보낸다. 401 이면 로그인 화면으로.
- 대화 응답은 SSE(`POST /conversations/{id}/messages`)를 fetch 스트림으로 읽는다 (`src/api/sse.ts`).
- PDF 는 인쇄 화면(`/print/documents/:id?version=&doc=student|answer|all`)에서 브라우저 "PDF로 저장".
