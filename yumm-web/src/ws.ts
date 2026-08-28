import { getToken } from "./api";

/** 서버 ChatMessageResponse와 1:1 */
export type ChatMessage = {
  roomId: string;
  senderId: number;
  sender: string;
  content: string;
  type: string;
  sentAt: string;
};

/** STOMP 프레임 종료 문자 */
const NUL = "\u0000";

const frame = (command: string, headers: Record<string, string>, body = "") =>
  command + "\n" +
  Object.entries(headers).map(([k, v]) => `${k}:${v}`).join("\n") +
  "\n\n" + body + NUL;

/** "MESSAGE\nk:v\n\nbody" → { command, headers, body } */
function parse(raw: string) {
  const split = raw.indexOf("\n\n");
  const lines = raw.slice(0, split).split("\n");
  const headers: Record<string, string> = {};
  for (const line of lines.slice(1)) {
    const i = line.indexOf(":");
    if (i > 0) headers[line.slice(0, i)] = line.slice(i + 1);
  }
  return { command: lines[0].trim(), headers, body: raw.slice(split + 2) };
}

/** 로그인 토큰의 sub 클레임 = 내 userId. 내 메시지 구분용이고, 실제 검증은 서버가 한다. */
export function myUserId(): number | null {
  const payload = getToken()?.split(".")[1];
  if (!payload) return null;
  try {
    const id = Number(JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/"))).sub);
    return Number.isFinite(id) ? id : null;
  } catch {
    return null;
  }
}

type Handlers = {
  onOpen: () => void;
  onMessage: (msg: ChatMessage) => void;
  onError: (message: string) => void;
};

/**
 * groupId 채팅방에 붙는다. 브라우저는 WS 핸드셰이크에 헤더를 못 넣으므로
 * 인증은 STOMP CONNECT 프레임의 Authorization 헤더로만 보낸다.
 *
 * ponytail: 하트비트(0,0)도 자동 재연결도 없다. 끊기면 onError로 알리고 화면의 재시도 버튼에 맡긴다.
 * 끊김이 잦으면 그때 지수 백오프 재연결을 넣는다.
 */
export function connectChat(groupId: string, handlers: Handlers) {
  const scheme = location.protocol === "https:" ? "wss" : "ws";
  const ws = new WebSocket(`${scheme}://${location.host}/ws`);
  let done = false;

  const fail = (message: string) => {
    if (done) return;
    done = true;
    handlers.onError(message);
    ws.close();
  };

  ws.onopen = () => ws.send(frame("CONNECT", {
    "accept-version": "1.2",
    host: location.hostname,
    "heart-beat": "0,0",
    Authorization: `Bearer ${getToken() ?? ""}`,
  }));

  ws.onmessage = (e) => {
    for (const raw of String(e.data).split(NUL)) {
      if (!raw.trim()) continue;
      const { command, headers, body } = parse(raw);
      if (command === "CONNECTED") {
        ws.send(frame("SUBSCRIBE", { id: "sub-0", destination: `/sub/chat/room/${groupId}` }));
        handlers.onOpen();
      } else if (command === "MESSAGE") {
        handlers.onMessage(JSON.parse(body) as ChatMessage);
      } else if (command === "ERROR") {
        // 인증 실패·비구성원 구독은 서버가 ERROR 프레임을 주고 연결을 끊는다.
        fail(headers.message || "채팅방에 연결할 수 없습니다.");
      }
    }
  };

  ws.onerror = () => fail("채팅 서버에 연결하지 못했습니다.");
  ws.onclose = () => fail("연결이 끊겼습니다.");

  return {
    send(content: string) {
      ws.send(frame("SEND", {
        destination: `/pub/chatroom.${groupId}`,
        "content-type": "application/json",
      }, JSON.stringify({ content })));
    },
    close() {
      done = true;
      ws.close();
    },
  };
}
