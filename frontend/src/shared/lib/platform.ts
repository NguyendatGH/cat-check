/** Phát hiện platform tối thiểu — dùng để chọn layout FullscreenLayout mobile vs APL desktop cho /scan. */
export function isStandalonePwa(): boolean {
  return window.matchMedia("(display-mode: standalone)").matches;
}

export function isTouchDevice(): boolean {
  return "ontouchstart" in window || navigator.maxTouchPoints > 0;
}
