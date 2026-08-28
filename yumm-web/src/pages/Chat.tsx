import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api";
import { connectChat, myUserId, type ChatMessage } from "../ws";

const time = (sentAt: string) => {
  const d = new Date(sentAt);
  return isNaN(d.getTime()) ? "" : d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
};

const Chat = () => {
  const { groupId = "" } = useParams();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [historyError, setHistoryError] = useState("");
  const [state, setState] = useState<"connecting" | "open" | "error">("connecting");
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0); // 재시도 버튼이 올리면 다시 연결한다
  const socket = useRef<ReturnType<typeof connectChat> | null>(null);
  const bottom = useRef<HTMLLIElement>(null);
  const me = myUserId();

  // 이력을 먼저 채우고 그 다음 구독한다. 재연결도 이 효과를 다시 타므로 messages를 비워
  // 이력이 두 번 쌓이지 않게 한다.
  // ponytail: 이력 조회와 구독 사이(수백 ms)에 오간 메시지는 놓칠 수 있다.
  // 실제로 새면 구독을 먼저 걸고 받은 걸 버퍼링했다가 이력 뒤에 붙이면 된다.
  useEffect(() => {
    if (!groupId) return;
    let cancelled = false;
    let s: ReturnType<typeof connectChat> | null = null;
    setMessages([]);
    setLoading(true);
    setHistoryError("");
    setState("connecting");
    setError("");

    api<ChatMessage[]>(`/chat/rooms/${groupId}/messages`)
      .then((history) => { if (!cancelled) setMessages(history ?? []); })
      .catch((e: unknown) => {
        if (!cancelled) setHistoryError(e instanceof Error ? e.message : "지난 대화를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (cancelled) return;
        setLoading(false);
        s = connectChat(groupId, {
          onOpen: () => setState("open"),
          onMessage: (msg) => setMessages((prev) => [...prev, msg]),
          onError: (message) => { setError(message); setState("error"); },
        });
        socket.current = s;
      });

    return () => { cancelled = true; socket.current = null; s?.close(); };
  }, [groupId, attempt]);

  useEffect(() => { bottom.current?.scrollIntoView({ block: "end" }); }, [messages]);

  const send = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const input = e.currentTarget.elements.namedItem("content") as HTMLInputElement;
    const content = input.value.trim();
    if (!content || state !== "open") return;
    socket.current?.send(content);
    input.value = "";
  };

  return (
    <div className="max-w-sm mx-auto flex flex-col gap-2 h-[calc(100vh-8rem)]">
      <h1 className="text-2xl font-bold">그룹 채팅</h1>

      {state === "error" ? (
        <div className="flex-1 flex flex-col items-center justify-center gap-3 text-center">
          <p className="text-red-600 text-sm">{error}</p>
          <button onClick={() => setAttempt((n) => n + 1)} className="border rounded p-2 px-4">
            다시 연결
          </button>
        </div>
      ) : (
        <ul className="flex-1 overflow-y-auto flex flex-col gap-2 border rounded p-2">
          {historyError && (
            <li className="text-center text-xs text-red-600">
              지난 대화를 불러오지 못했습니다. {historyError}
            </li>
          )}
          {loading && <li className="m-auto text-gray-500 text-sm">지난 대화를 불러오는 중…</li>}
          {!loading && !historyError && messages.length === 0 && (
            <li className="m-auto text-gray-500 text-sm text-center">
              아직 대화가 없습니다.
              <br />
              먼저 인사를 건네보세요.
            </li>
          )}
          {messages.map((msg, i) => {
            const mine = msg.senderId === me;
            return (
              <li key={i} className={`flex flex-col ${mine ? "items-end" : "items-start"}`}>
                {!mine && <span className="text-xs text-gray-500">{msg.sender}</span>}
                <div className="flex items-end gap-1 max-w-[80%]">
                  {mine && <span className="text-[10px] text-gray-400">{time(msg.sentAt)}</span>}
                  <p className={`rounded p-2 text-sm break-words whitespace-pre-wrap ${
                    mine ? "bg-blue-600 text-white" : "bg-gray-100"
                  }`}>
                    {msg.content}
                  </p>
                  {!mine && <span className="text-[10px] text-gray-400">{time(msg.sentAt)}</span>}
                </div>
              </li>
            );
          })}
          {!loading && state === "connecting" && (
            <li className="text-center text-xs text-gray-400">연결 중…</li>
          )}
          <li ref={bottom} />
        </ul>
      )}

      <form onSubmit={send} className="flex gap-2">
        <input
          name="content"
          autoComplete="off"
          disabled={state !== "open"}
          placeholder={state === "open" ? "메시지를 입력하세요" : "연결되면 입력할 수 있어요"}
          className="border rounded p-2 flex-1 min-w-0 disabled:bg-gray-100"
        />
        <button disabled={state !== "open"} className="bg-blue-600 text-white rounded p-2 px-4 disabled:bg-gray-300">
          전송
        </button>
      </form>
    </div>
  );
};

export default Chat;
