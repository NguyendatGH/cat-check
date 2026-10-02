type Listener<T> = (payload: T) => void;

/**
 * Event bus tối giản, kiểu-an-toàn, dùng cho giao tiếp chéo feature không qua props/store
 * (ví dụ: sw.ts báo "có bản cập nhật mới" cho app/pwa/useServiceWorkerUpdate.ts).
 */
export class EventBus<Events extends Record<string, unknown>> {
  private listeners: { [K in keyof Events]?: Set<Listener<Events[K]>> } = {};

  on<K extends keyof Events>(event: K, listener: Listener<Events[K]>): () => void {
    const set = this.listeners[event] ?? new Set<Listener<Events[K]>>();
    set.add(listener);
    this.listeners[event] = set;
    return () => {
      set.delete(listener);
    };
  }

  emit<K extends keyof Events>(event: K, payload: Events[K]): void {
    this.listeners[event]?.forEach((listener) => {
      listener(payload);
    });
  }
}
