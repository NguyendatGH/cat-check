/**
 * Pháo hoa trên canvas bằng requestAnimationFrame — không phụ thuộc thư viện ngoài.
 * Mỗi quả: một vệt bay lên rồi nổ thành chùm hạt rơi theo trọng lực, mờ dần.
 */

/** Token màu thương hiệu (app/styles/index.css); giá trị thứ hai là dự phòng khi chưa nạp CSS. */
const PALETTE_TOKENS: readonly (readonly [string, string])[] = [
  ["--color-primary", "#2f4fb2"],
  ["--color-secondary", "#fdcf52"],
  ["--color-secondary-light", "#ffdf93"],
  ["--color-on-primary-muted", "#becaff"],
  ["--color-success-strong", "#4ae176"],
];

export function readBrandPalette(element: Element = document.documentElement): string[] {
  let styles: CSSStyleDeclaration | null = null;
  try {
    styles = window.getComputedStyle(element);
  } catch {
    styles = null;
  }
  return PALETTE_TOKENS.map(([token, fallback]) => {
    const value = styles?.getPropertyValue(token).trim();
    return value && value.length > 0 ? value : fallback;
  });
}

interface Rocket {
  x: number;
  y: number;
  vy: number;
  targetY: number;
  color: string;
}

interface Spark {
  x: number;
  y: number;
  vx: number;
  vy: number;
  life: number;
  decay: number;
  size: number;
  color: string;
}

export interface FireworksOptions {
  durationMs: number;
  colors: readonly string[];
  /** Gọi một lần khi hết hạt và hết thời lượng. */
  onDone?: () => void;
  /** Nguồn ngẫu nhiên (test có thể cố định). */
  random?: () => number;
}

const GRAVITY = 0.06;
const FRICTION = 0.985;
const SPARKS_PER_BURST = 84;
const LAUNCH_EVERY_MS = 280;

/** Chạy pháo hoa trên canvas; trả hàm dừng (huỷ rAF, xoá canvas). */
export function runFireworks(canvas: HTMLCanvasElement, options: FireworksOptions): () => void {
  const ctx = canvas.getContext("2d");
  const random = options.random ?? Math.random;
  const colors = options.colors.length > 0 ? options.colors : ["white"];
  let frame = 0;
  let stopped = false;
  let doneCalled = false;

  const finish = () => {
    if (doneCalled) return;
    doneCalled = true;
    options.onDone?.();
  };

  if (!ctx) {
    const timer = window.setTimeout(finish, options.durationMs);
    return () => {
      stopped = true;
      window.clearTimeout(timer);
    };
  }

  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  let width = 0;
  let height = 0;
  const resize = () => {
    width = canvas.clientWidth || window.innerWidth;
    height = canvas.clientHeight || window.innerHeight;
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  };
  resize();
  window.addEventListener("resize", resize);

  const pick = () => colors[Math.floor(random() * colors.length)] ?? "white";
  const rockets: Rocket[] = [];
  const sparks: Spark[] = [];
  // Ngừng phóng quả mới sớm hơn thời lượng để hạt kịp rơi và tắt trước khi overlay biến mất.
  const launchUntil = Math.max(0, options.durationMs - 1100);
  let startedAt: number | null = null;
  let lastLaunch = -Infinity;

  const launch = () => {
    rockets.push({
      x: width * (0.15 + random() * 0.7),
      y: height,
      vy: -(height * 0.012 + random() * height * 0.004),
      targetY: height * (0.15 + random() * 0.3),
      color: pick(),
    });
  };

  const burst = (x: number, y: number, color: string) => {
    for (let i = 0; i < SPARKS_PER_BURST; i += 1) {
      const angle = (Math.PI * 2 * i) / SPARKS_PER_BURST + random() * 0.2;
      const speed = 2 + random() * 4.2;
      sparks.push({
        x,
        y,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        life: 1,
        decay: 0.009 + random() * 0.01,
        size: 2.4 + random() * 2.4,
        color: random() < 0.7 ? color : pick(),
      });
    }
  };

  const tick = (now: number) => {
    if (stopped) return;
    if (startedAt === null) {
      startedAt = now;
      // Open with two bursts right away so the celebration is visible immediately.
      burst(width * 0.3, height * 0.28, pick());
      burst(width * 0.7, height * 0.22, pick());
    }
    const elapsed = now - startedAt;

    if (elapsed < launchUntil && now - lastLaunch >= LAUNCH_EVERY_MS) {
      launch();
      if (random() < 0.35) launch();
      lastLaunch = now;
    }

    ctx.clearRect(0, 0, width, height);

    for (let i = rockets.length - 1; i >= 0; i -= 1) {
      const rocket = rockets[i];
      rocket.y += rocket.vy;
      rocket.vy *= 0.99;
      ctx.globalAlpha = 0.9;
      ctx.strokeStyle = rocket.color;
      ctx.lineWidth = 2.5;
      ctx.lineCap = "round";
      ctx.beginPath();
      ctx.moveTo(rocket.x, rocket.y);
      ctx.lineTo(rocket.x, rocket.y - rocket.vy * 4);
      ctx.stroke();
      if (rocket.y <= rocket.targetY) {
        burst(rocket.x, rocket.y, rocket.color);
        rockets.splice(i, 1);
      }
    }

    for (let i = sparks.length - 1; i >= 0; i -= 1) {
      const spark = sparks[i];
      spark.vx *= FRICTION;
      spark.vy = spark.vy * FRICTION + GRAVITY;
      spark.x += spark.vx;
      spark.y += spark.vy;
      spark.life -= spark.decay;
      if (spark.life <= 0) {
        sparks.splice(i, 1);
        continue;
      }
      ctx.globalAlpha = Math.max(0, spark.life);
      ctx.fillStyle = spark.color;
      ctx.beginPath();
      ctx.arc(spark.x, spark.y, spark.size * (0.5 + spark.life / 2), 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalAlpha = 1;

    const idle = rockets.length === 0 && sparks.length === 0;
    if (elapsed >= options.durationMs || (elapsed >= launchUntil && idle)) {
      ctx.clearRect(0, 0, width, height);
      finish();
      return;
    }
    frame = window.requestAnimationFrame(tick);
  };

  frame = window.requestAnimationFrame(tick);

  return () => {
    stopped = true;
    window.cancelAnimationFrame(frame);
    window.removeEventListener("resize", resize);
    ctx.clearRect(0, 0, width, height);
  };
}
