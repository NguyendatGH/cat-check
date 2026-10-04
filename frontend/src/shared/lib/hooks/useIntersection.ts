import { useEffect, useRef, useState, type RefObject } from "react";

/** Theo dõi 1 phần tử có đang trong viewport không (dùng cho lazy-load / infinite scroll). */
export function useIntersection<T extends Element>(options?: IntersectionObserverInit): [RefObject<T | null>, boolean] {
  const ref = useRef<T | null>(null);
  const [isIntersecting, setIsIntersecting] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    const observer = new IntersectionObserver((entries) => {
      // Quan sát đúng 1 node (observer.observe(node) bên dưới) nên entries[0] luôn tồn tại.
      setIsIntersecting(entries[0].isIntersecting);
    }, options);

    observer.observe(node);
    return () => {
      observer.disconnect();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- options là object literal thường xuyên đổi identity ở call-site; chỉ theo dõi lại khi node đổi.
  }, [ref.current]);

  return [ref, isIntersecting];
}
