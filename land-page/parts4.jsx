// Instructor + FAQ + Pricing CTA + Footer
const { Card: FCard, Accordion: FAccordion, Button: FButton, Eyebrow: FEyebrow, Badge: FBadge } = window.DesignSystem_55f34f;

function Instructor() {
  const creds = [
    "《Spring AI》系列技術作者，鐵人賽 30 天 Spring AI 連載",
    "深耕 Java / Spring Boot 企業開發與 AI 落地教學",
    "經營 AI 全端開發社群，帶領上千名工程師導入 AI Agent 協作",
  ];
  return (
    <section>
      <window.SectionTitle id="instructor" kicker="講師介紹" title="你的講師：凱文大叔" />
      <FCard variant="solid" padding={0} style={{ overflow: "hidden", display: "grid", gridTemplateColumns: "300px 1fr" }} className="hc-instructor">
        <div style={{ background: "linear-gradient(150deg, oklch(34% 0.07 232), oklch(22% 0.04 248))", display: "grid", placeItems: "center", padding: 28 }}>
          <img src="assets/logo.png" alt="凱文大叔" style={{ width: 156, height: 156, borderRadius: "var(--r-pill)", border: "2px solid oklch(100% 0 0 / 0.3)", background: "oklch(100% 0 0 / 0.06)" }} />
        </div>
        <div style={{ padding: "28px 30px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
            <strong style={{ fontSize: "1.4rem" }}>凱文大叔 Uncle Kevin</strong>
            <FBadge tone="amber" solid>Spring AI 作者</FBadge>
          </div>
          <p style={{ margin: "12px 0 18px", color: "var(--muted)", lineHeight: 1.9 }}>
            專注把 Spring Boot、Spring AI 與 React 串成可上線的企業級系統。教學風格務實——不堆砌名詞，帶你一行一行把 AI 放進真實產品。
          </p>
          <div style={{ display: "grid", gap: 10 }}>
            {creds.map((cr, i) => (
              <div key={i} style={{ display: "flex", gap: 10, alignItems: "flex-start", fontSize: "0.95rem", color: "var(--fg)" }}>
                <span style={{ color: "var(--accent)", marginTop: 1 }}><window.Icon name="badge-check" size={19} /></span>{cr}
              </div>
            ))}
          </div>
        </div>
      </FCard>
    </section>
  );
}

function Faq() {
  const c = window.COURSE;
  return (
    <section>
      <window.SectionTitle id="faq" kicker="常見問題" title="報名前，先看這些" />
      <div style={{ display: "grid", gap: 12 }}>
        {c.faq.map((f, i) => (
          <FAccordion key={i} summary={f.q}>{f.a}</FAccordion>
        ))}
      </div>
    </section>
  );
}

function PricingCTA({ onEnroll }) {
  const p = window.COURSE.pricing;
  return (
    <section>
      <FCard padding={0} style={{ overflow: "hidden", position: "relative", color: "#fff", border: "none" }}>
        <div style={{ position: "absolute", inset: 0, background: "linear-gradient(120deg, oklch(30% 0.07 248), oklch(46% 0.12 240))" }} />
        <div style={{ position: "relative", padding: "clamp(32px, 5vw, 56px)", textAlign: "center" }}>
          <FEyebrow>募資限定 · 早鳥優惠</FEyebrow>
          <h2 style={{ margin: "18px 0 10px", fontSize: "clamp(1.7rem, 3.5vw, 2.6rem)", fontWeight: 900 }}>現在報名，鎖定最低早鳥價</h2>
          <p style={{ margin: "0 auto 22px", maxWidth: 560, lineHeight: 1.85, color: "oklch(96% 0.01 250 / 0.9)" }}>
            完課後你會擁有一套可登入、可查資料、可引用知識庫、可產生業務建議的企業級 AI CRM。
          </p>
          <div style={{ display: "flex", alignItems: "baseline", justifyContent: "center", gap: 12, marginBottom: 22 }}>
            <span style={{ fontSize: "2.6rem", fontWeight: 900, color: "var(--accent-2)" }}>NT$ {p.early.toLocaleString()}</span>
            <span style={{ textDecoration: "line-through", opacity: 0.7, fontWeight: 700 }}>NT$ {p.list.toLocaleString()}</span>
          </div>
          <div style={{ display: "flex", gap: 14, justifyContent: "center", flexWrap: "wrap" }}>
            <FButton variant="light" size="lg" onClick={onEnroll} iconRight={<window.Icon name="arrow-right" size={18} />}>立即報名</FButton>
            <FButton variant="ghost" size="lg" onClick={onEnroll} style={{ color: "#fff", border: "1px solid oklch(100% 0 0 / 0.32)" }}>免費試看單元</FButton>
          </div>
        </div>
      </FCard>
    </section>
  );
}

function Footer() {
  return (
    <footer style={{ textAlign: "center", padding: "40px 16px 28px", color: "var(--muted)", lineHeight: 1.9 }}>
      <div style={{ fontWeight: 900, fontSize: "1.05rem", color: "var(--fg)", marginBottom: 6 }}>
        <span style={{ color: "var(--accent)" }}>凱文</span>大叔 · AI 賦能全端開發
      </div>
      <div style={{ fontSize: "0.9rem" }}>AI-Empowered Full-Stack · Building Enterprise-Grade Smart Apps</div>
      <div style={{ fontSize: "0.82rem", marginTop: 12, color: "var(--muted-2)" }}>© 2026 Uncle Kevin. Hahow 課程銷售頁範本 · 內容整理自 ai-full-stack 課綱。</div>
    </footer>
  );
}

Object.assign(window, { Instructor, Faq, PricingCTA, Footer });
