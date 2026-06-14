// Header + Hero + sticky PurchaseCard
const { Button, Eyebrow, Badge, Card, ProgressBar } = window.DesignSystem_55f34f;

function Header({ onEnroll }) {
  const links = [
    ["課程介紹", "#intro"],
    ["課程大綱", "#curriculum"],
    ["實戰專案", "#project"],
    ["講師介紹", "#instructor"],
    ["常見問題", "#faq"],
  ];
  return (
    <header style={{
      position: "sticky", top: 0, zIndex: 50,
      display: "flex", alignItems: "center", justifyContent: "space-between",
      gap: 24, padding: "14px clamp(16px, 4vw, 56px)",
      background: "color-mix(in oklab, var(--surface) 88%, transparent)",
      borderBottom: "1px solid var(--border)",
      backdropFilter: "var(--glass-blur)", WebkitBackdropFilter: "var(--glass-blur)",
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, fontWeight: 900, fontSize: "1.15rem" }}>
        <img src="assets/logo.png" alt="凱文大叔" style={{ width: 40, height: 40, borderRadius: "var(--r-pill)", flex: "0 0 auto" }} />
        <span style={{ color: "var(--accent)" }}>凱文</span>大叔
        <span style={{ fontSize: "0.72rem", fontWeight: 800, color: "var(--muted-2)", letterSpacing: "0.16em", textTransform: "uppercase", marginLeft: 4 }}>Uncle Kevin</span>
      </div>
      <nav style={{ display: "flex", gap: 4, alignItems: "center" }} className="hc-nav">
        {links.map(([label, href]) => (
          <a key={href} href={href} style={{
            padding: "8px 14px", borderRadius: "var(--r-pill)", textDecoration: "none",
            color: "var(--muted)", fontWeight: 700, fontSize: "0.94rem",
          }}
            onMouseEnter={(e) => { e.currentTarget.style.background = "var(--surface-2)"; e.currentTarget.style.color = "var(--fg)"; }}
            onMouseLeave={(e) => { e.currentTarget.style.background = "transparent"; e.currentTarget.style.color = "var(--muted)"; }}
          >{label}</a>
        ))}
      </nav>
      <Button size="sm" onClick={onEnroll} iconRight={<window.Icon name="arrow-right" size={16} />}>立即報名</Button>
    </header>
  );
}

function Hero() {
  const c = window.COURSE;
  return (
    <section style={{ position: "relative", overflow: "hidden", color: "#fff" }}>
      <div style={{ position: "absolute", inset: 0 }}>
        <img src="assets/cover.png" alt="" style={{ width: "100%", height: "100%", objectFit: "cover", opacity: 0.28, mixBlendMode: "luminosity" }} />
      </div>
      <div style={{ position: "absolute", inset: 0, background: "linear-gradient(120deg, oklch(20% 0.04 248 / 0.94), oklch(34% 0.07 232 / 0.78))" }} />
      <div style={{ position: "relative", maxWidth: 1100, margin: "0 auto", padding: "clamp(48px, 7vw, 88px) clamp(16px, 4vw, 56px)" }}>
        <Eyebrow>Hahow 線上課程募資中</Eyebrow>
        <h1 style={{ margin: "20px 0 18px", fontSize: "clamp(2.2rem, 5vw, 4rem)", lineHeight: 1.08, fontWeight: 900, maxWidth: 920 }}>{c.titleZh}</h1>
        <p style={{ margin: 0, maxWidth: 760, fontSize: "clamp(1.02rem, 1.6vw, 1.2rem)", lineHeight: 1.9, color: "oklch(96% 0.01 250 / 0.92)" }}>{c.tagline}</p>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 22, marginTop: 30, alignItems: "center" }}>
          <HeroStat value={c.units.length} label="實戰單元" />
          <Divider />
          <HeroStat value="1" label="完整 AI CRM 專案" />
          <Divider />
          <HeroStat value={c.stack.length + "+"} label="技術棧整合" />
          <Divider />
          <HeroStat value={c.pricing.backers.toLocaleString()} label="已報名學員" />
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 30 }}>
          {c.stack.slice(0, 8).map((s) => (
            <span key={s} style={{ padding: "7px 13px", borderRadius: "var(--r-pill)", border: "1px solid oklch(100% 0 0 / 0.2)", background: "oklch(100% 0 0 / 0.1)", fontSize: "0.86rem", fontWeight: 700 }}>{s}</span>
          ))}
        </div>
      </div>
    </section>
  );
}

function HeroStat({ value, label }) {
  return (
    <div>
      <div style={{ fontSize: "1.9rem", fontWeight: 900, lineHeight: 1, color: "var(--accent-2)" }}>{value}</div>
      <div style={{ fontSize: "0.84rem", color: "oklch(94% 0.01 250 / 0.78)", marginTop: 5, fontWeight: 700 }}>{label}</div>
    </div>
  );
}
function Divider() {
  return <div style={{ width: 1, height: 34, background: "oklch(100% 0 0 / 0.18)" }} />;
}

function PurchaseCard({ onEnroll }) {
  const p = window.COURSE.pricing;
  const includes = [
    ["clapperboard", "8 大單元 · 40+ 實戰影片"],
    ["infinity", "購買後終身無限觀看"],
    ["sparkles", "全課 AI Agent 提示詞包"],
    ["file-badge", "結業證書與專案原始碼"],
  ];
  return (
    <Card variant="solid" padding={0} style={{ overflow: "hidden", position: "sticky", top: 88 }}>
      <div style={{ position: "relative" }}>
        <img src="assets/cover.png" alt="課程封面" style={{ width: "100%", height: 168, objectFit: "cover" }} />
        <button onClick={onEnroll} aria-label="播放試看" style={{
          position: "absolute", inset: 0, margin: "auto", width: 58, height: 58, borderRadius: "var(--r-pill)",
          border: "none", cursor: "pointer", background: "color-mix(in oklab, var(--accent) 92%, black)", color: "#fff",
          display: "grid", placeItems: "center", boxShadow: "var(--shadow-lift)",
        }}>
          <window.Icon name="play" size={24} style={{ marginLeft: 3 }} />
        </button>
        <span style={{ position: "absolute", top: 12, left: 12 }}><Badge tone="amber" solid>早鳥募資中</Badge></span>
      </div>
      <div style={{ padding: "20px 22px 24px" }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 10 }}>
          <span style={{ fontSize: "1.9rem", fontWeight: 900, color: "var(--accent-deep)" }}>NT$ {p.early.toLocaleString()}</span>
          <span style={{ color: "var(--muted-2)", textDecoration: "line-through", fontWeight: 700 }}>NT$ {p.list.toLocaleString()}</span>
        </div>
        <div style={{ marginTop: 16 }}>
          <ProgressBar value={p.goalPct} label={p.goalLabel} showValue />
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: 10, fontSize: "0.86rem", color: "var(--muted)", fontWeight: 700 }}>
            <span><b style={{ color: "var(--fg)" }}>{p.backers.toLocaleString()}</b> 人贊助</span>
            <span>倒數 <b style={{ color: "var(--accent-2-deep)" }}>{p.daysLeft}</b> 天</span>
          </div>
        </div>
        <div style={{ display: "grid", gap: 10, marginTop: 20 }}>
          <Button size="lg" onClick={onEnroll} style={{ width: "100%" }}>立即報名 · 鎖定早鳥價</Button>
          <Button size="md" variant="secondary" onClick={onEnroll} style={{ width: "100%" }} iconLeft={<window.Icon name="play" size={16} />}>免費試看單元</Button>
        </div>
        <div style={{ display: "grid", gap: 11, marginTop: 22 }}>
          {includes.map(([icon, label]) => (
            <div key={label} style={{ display: "flex", alignItems: "center", gap: 10, color: "var(--muted)", fontSize: "0.92rem", fontWeight: 600 }}>
              <span style={{ color: "var(--success)" }}><window.Icon name={icon} size={18} /></span>{label}
            </div>
          ))}
        </div>
      </div>
    </Card>
  );
}

Object.assign(window, { Header, Hero, PurchaseCard });
