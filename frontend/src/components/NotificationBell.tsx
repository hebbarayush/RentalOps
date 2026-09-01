import { useCallback, useEffect, useRef, useState } from "react";
import { Bell } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { notificationsApi } from "../lib/resources";
import { ApiError } from "../lib/api";
import type { NotificationResponse } from "../types";

export function NotificationBell() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [count, setCount] = useState(0);
  const [items, setItems] = useState<NotificationResponse[]>([]);
  const ref = useRef<HTMLDivElement>(null);

  const refreshCount = useCallback(async () => {
    try {
      const r = await notificationsApi.unreadCount();
      setCount(r.count);
    } catch (err) {
      if (!(err instanceof ApiError)) throw err;
    }
  }, []);

  useEffect(() => {
    refreshCount();
    const id = setInterval(refreshCount, 30_000);
    return () => clearInterval(id);
  }, [refreshCount]);

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  async function toggle() {
    const next = !open;
    setOpen(next);
    if (next) {
      const page = await notificationsApi.list({ size: 10 });
      setItems(page.content);
    }
  }

  async function markAll() {
    await notificationsApi.markAllRead();
    setCount(0);
    setItems((xs) => xs.map((x) => ({ ...x, read: true })));
  }

  async function openItem(n: NotificationResponse) {
    if (!n.read) {
      await notificationsApi.markRead(n.id);
      setCount((c) => Math.max(0, c - 1));
      setItems((xs) => xs.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
    }
    if (n.linkType) {
      navigate(`/${n.linkType === "payments" ? "payments" : n.linkType}`);
      setOpen(false);
    }
  }

  return (
    <div className="bell" ref={ref}>
      <button className="btn btn-ghost" onClick={toggle} title="Notifications" aria-label="Notifications">
        <Bell size={16} />
        {count > 0 && <span className="bell-badge">{count > 9 ? "9+" : count}</span>}
      </button>
      {open && (
        <div className="bell-panel">
          <div className="bell-panel-head">
            <strong>Notifications</strong>
            {count > 0 && (
              <button className="btn btn-ghost" onClick={markAll}>
                Mark all read
              </button>
            )}
          </div>
          {items.length === 0 ? (
            <div className="bell-empty">You're all caught up.</div>
          ) : (
            <ul>
              {items.map((n) => (
                <li key={n.id} className={n.read ? "" : "unread"}>
                  <button onClick={() => openItem(n)}>
                    <span className="bell-title">{n.title}</span>
                    <span className="bell-msg">{n.message}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
