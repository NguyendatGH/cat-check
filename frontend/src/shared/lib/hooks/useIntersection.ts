import { useEffect, useRef, useState, type RefObject } from "react";

export function useIntersection<T extends Element>(options?: IntersectionObserverInit): [RefObject<T | null>, boolean] {
  const ref = useRef<T | null>(null);
  const [isIntersecting, setIsIntersecting] = useState(false);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    const observer = new IntersectionObserver((entries) => {
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
