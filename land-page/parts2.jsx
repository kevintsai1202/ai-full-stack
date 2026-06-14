// Section heading helper + Intro/Outcomes, Features, Audience
const { Eyebrow: Eb, Card: Cd, Callout: Co, Badge: Bd } = window.DesignSystem_55f34f;

function SectionTitle({ kicker, title, lead, id }) {
  return (
    <div id={id} style={{ scrollMarginTop: 88, marginBottom: 28 }}>
      {kicker ? <Eb tone="accent" icon="●">{kicker}</Eb> : null}
      <h2 style={{ margin: "14px 0 0", fontSize: "clamp(1.6rem, 3vw, 2.1rem)", fontWeight: 900, lineHeight: 1.2 }}>{title}</h2>
      {lead ? <p style={{ margin: "12px 0 0", maxWidth: 680, color: "var(--muted)", lineHeight: 1.9, fontSize: "1.02rem" }}>{lead}</p> : null}
    </div>
  );
}

function Intro() {
  const c = window.COURSE;
  return (
    <section style={{ paddingTop: 8 }}>
      <SectionTitle id="intro" kicker="課程介紹" title="完課後，你會做出什麼？"
        lead="這不是一堆零散範例，而是一套從登入到 AI 對話完整跑通的企業級 AI CRM。以下是你會親手建立的能力：" />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))", gap: 16 }}>
        {c.outcomes.map((o, i) => (
          <Cd key={i} variant="solid" hover padding={22} style={{ boxShadow: "var(--shadow-sm)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 12 }}>
              <span style={{ display: "grid", placeItems: "center", width: 40, height: 40, borderRadius: 12, background: "color-mix(in oklab, var(--accent) 14%, transparent)", color: "var(--accent)", fontWeight: 900 }}>{String(i + 1).padStart(2, "0")}</span>
              <strong style={{ fontSize: "1.04rem", lineHeight: 1.3 }}>{o.t}</strong>
            </div>
            <p style={{ margin: 0, color: "var(--muted)", lineHeight: 1.8, fontSize: "0.95rem" }}>{o.d}</p>
          </Cd>
        ))}
      </div>
    </section>
  );
}

function Features() {
  const c = window.COURSE;
  return (
    <section>
      <SectionTitle kicker="為什麼選這門課" title="不只教你呼叫 LLM，而是讓 AI 真正上線" />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 16 }}>
        {c.features.map((f, i) => (
          <Cd key={i} variant="sunken" padding={24} style={{ boxShadow: "none", display: "flex", gap: 16 }}>
            <span style={{ display: "grid", placeItems: "center", width: 48, height: 48, borderRadius: 14, flex: "0 0 auto", background: "var(--surface)", border: "1px solid var(--border)", color: "var(--accent)" }}>
              <window.Icon name={f.icon} size={24} />
            </span>
            <div>
              <strong style={{ display: "block", fontSize: "1.06rem", marginBottom: 6 }}>{f.t}</strong>
              <p style={{ margin: 0, color: "var(--muted)", lineHeight: 1.8, fontSize: "0.95rem" }}>{f.d}</p>
            </div>
          </Cd>
        ))}
      </div>
    </section>
  );
}

function Audience() {
  const c = window.COURSE;
  return (
    <section>
      <SectionTitle kicker="適合對象" title="這門課為誰而設計" />
      <div style={{ display: "grid", gap: 12 }}>
        {c.audience.map((a, i) => (
          <div key={i} style={{ display: "flex", alignItems: "flex-start", gap: 14, padding: "16px 18px", borderRadius: "var(--r-md)", border: "1px solid var(--border)", background: "var(--surface)" }}>
            <span style={{ color: "var(--success)", marginTop: 1 }}><window.Icon name="circle-check-big" size={22} /></span>
            <span style={{ lineHeight: 1.75, color: "var(--fg)" }}>{a}</span>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 16 }}>
        <Co tone="amber" title="先備知識">需具備基本 Java 語法與 REST API 概念。課程著重企業級分層、整合與 AI 落地，不從零教語法——寫過簡單 API 就能跟上。</Co>
      </div>
    </section>
  );
}

Object.assign(window, { SectionTitle, Intro, Features, Audience });
