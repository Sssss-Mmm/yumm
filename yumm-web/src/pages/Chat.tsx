import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError, api, getMatchStatus, myUserId } from "../api";
import { connectChat, type ChatMessage } from "../ws";
import { btn, btnGhost, h1, input } from "../ui";

const time = (sentAt: string) => {
  const d = new Date(sentAt);
  return isNaN(d.getTime()) ? "" : d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
};

// 이 방이 더 이상 내 방이 아닐 때, 무슨 일이 있었고 다음에 뭘 하면 되는지 (FR-C-03 / FR-C-04)
type Gone =
  | { kind: "queued" }                   // 해체 → 대기열 자동 복귀
  | { kind: "ended" }                    // 지난 끼니라 종료 / 내가 나감 / 신청 이력 없음
  | { kind: "moved"; groupId: string };  // 이미 다음 그룹에 편성됨

const GONE = {
  queued: {
    title: "그룹이 해체됐어요",
    body: "인원이 최소 인원보다 적어져 그룹이 해체됐습니다. 대기열로 자동 복귀했으니 다음 편성에서 다시 묶어드려요.",
    action: "대기 현황 보기",
  },
  ended: {
    title: "종료된 채팅방이에요",
    body: "이 그룹은 종료되어 더 이상 메시지를 보낼 수 없습니다. 지난 끼니이거나 신청이 끝나 대기열로는 돌아가지 않았어요. 새로 신청하면 다시 찾아드려요.",
    action: "새로 신청하기",
  },
  moved: {
    title: "새 그룹으로 편성됐어요",
    body: "이 그룹은 끝났고 이미 다른 그룹에 편성됐습니다. 새 채팅방에서 이어가 주세요.",
    action: "새 채팅방으로",
  },
} as const;

const Chat = () => {
  const { groupId = "" } = useParams();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [historyError, setHistoryError] = useState("");
  const [state, setState] = useState<"connecting" | "open" | "error">("connecting");
  const [error, setError] = useState("");
  const [gone, setGone] = useState<Gone | null>(null);
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
    setGone(null);

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

  // 해체는 채팅 소켓으로 오지 않는다(보내봐야 ERROR 프레임만 온다). 매칭 화면과 같은
  // GET /api/match를 같은 주기로 폴링하되, 여기서는 "서버가 말하는 내 groupId가 이 방인가"만
  // 본다 — 전이를 기억할 필요가 없어 새로고침으로 바로 들어와도 판정이 선다.
  useEffect(() => {
    if (!groupId || gone) return;   // gone이 정해지면 이 효과가 다시 돌며 폴링을 멈춘다
    let stop = false;
    const check = () =>
      getMatchStatus()
        .then((s) => {
          if (stop || s.groupId === groupId) return;
          socket.current?.close();  // close()는 onError를 삼킨다 → "연결이 끊겼습니다" 대신 아래 배너가 뜬다
          setGone(
            s.status === "WAITING" ? { kind: "queued" }
              : s.status === "MATCHED" && s.groupId ? { kind: "moved", groupId: s.groupId }
                : { kind: "ended" }
          );
        })
        // 서버가 응답한 실패(404 = 신청 이력 없음)만 종료로 본다. 네트워크 오류는 다음 폴링에 맡긴다.
        .catch((e: unknown) => { if (!stop && e instanceof ApiError) setGone({ kind: "ended" }); });
    check();
    const id = setInterval(check, 10_000);
    return () => { stop = true; clearInterval(id); };
  }, [groupId, gone]);

  useEffect(() => { bottom.current?.scrollIntoView({ block: "end" }); }, [messages]);

  const send = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const field = e.currentTarget.elements.namedItem("content") as HTMLInputElement;
    const content = field.value.trim();
    if (!content || gone || state !== "open") return;
    socket.current?.send(content);
    field.value = "";
  };

  return (
    <div className="flex h-[calc(100vh-9rem)] flex-col gap-3">
      <div className="flex items-center gap-2">
        <span className={`h-2 w-2 rounded-full ${
          gone ? "bg-amber-500"
            : state === "open" ? "bg-emerald-500"
              : state === "error" ? "bg-red-500" : "animate-pulse bg-stone-300"
        }`} />
        <h1 className={h1}>그룹 채팅</h1>
      </div>

      {gone && (
        <div className="flex flex-col gap-2 rounded-2xl border border-amber-300 bg-amber-50 p-3.5">
          <p className="text-sm font-semibold text-amber-900">{GONE[gone.kind].title}</p>
          <p className="text-sm text-amber-800">{GONE[gone.kind].body}</p>
          <Link
            to={gone.kind === "moved" ? `/chat/${gone.groupId}` : "/match"}
            className={`${btnGhost} text-sm`}
          >
            {GONE[gone.kind].action}
          </Link>
        </div>
      )}

      {state === "error" && !gone ? (
        <div className="flex-1 flex flex-col items-center justify-center gap-3 text-center">
          <p className="text-sm text-red-600">{error}</p>
          <button onClick={() => setAttempt((n) => n + 1)} className={btnGhost}>
            다시 연결
          </button>
        </div>
      ) : (
        <ul className="flex flex-1 flex-col gap-3 overflow-y-auto rounded-2xl border border-stone-200 bg-white p-3.5 shadow-sm">
          {historyError && (
            <li className="text-center text-xs text-red-600">
              지난 대화를 불러오지 못했습니다. {historyError}
            </li>
          )}
          {loading && <li className="m-auto text-sm text-stone-400">지난 대화를 불러오는 중…</li>}
          {!loading && !historyError && messages.length === 0 && (
            <li className="m-auto text-center text-sm text-stone-400">
              {gone ? (
                "대화 없이 끝난 방입니다."
              ) : (
                <>
                  아직 대화가 없습니다.
                  <br />
                  먼저 인사를 건네보세요.
                </>
              )}
            </li>
          )}
          {messages.map((msg, i) => {
            const mine = msg.senderId === me;
            return (
              <li key={i} className={`flex flex-col ${mine ? "items-end" : "items-start"}`}>
                {!mine && <span className="mb-0.5 ml-1 text-xs text-stone-500">{msg.sender}</span>}
                <div className="flex items-end gap-1 max-w-[80%]">
                  {mine && <span className="text-[10px] text-stone-400">{time(msg.sentAt)}</span>}
                  <p className={`break-words whitespace-pre-wrap px-3.5 py-2 text-sm ${
                    mine
                      ? "rounded-2xl rounded-br-md bg-orange-600 text-white"
                      : "rounded-2xl rounded-bl-md bg-stone-100 text-stone-800"
                  }`}>
                    {msg.content}
                  </p>
                  {!mine && <span className="text-[10px] text-stone-400">{time(msg.sentAt)}</span>}
                </div>
              </li>
            );
          })}
          {!loading && !gone && state === "connecting" && (
            <li className="text-center text-xs text-stone-400">연결 중…</li>
          )}
          <li ref={bottom} />
        </ul>
      )}

      <form onSubmit={send} className="flex gap-2">
        <input
          name="content"
          autoComplete="off"
          disabled={gone !== null || state !== "open"}
          placeholder={
            gone ? "종료된 대화입니다" : state === "open" ? "메시지를 입력하세요" : "연결되면 입력할 수 있어요"
          }
          className={`${input} min-w-0 flex-1`}
        />
        <button disabled={gone !== null || state !== "open"} className={btn}>
          전송
        </button>
      </form>
    </div>
  );
};

export default Chat;
