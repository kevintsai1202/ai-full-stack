// Project showcase (AI CRM) + Curriculum accordion
const { Card: PCard, Badge: PBadge, Accordion: PAccordion } = window.DesignSystem_55f34f;

function ProjectShowcase() {
  const modules = [
    ["認證與權限", "登入、JWT、角色權限、API 保護", "Spring Security"],
    ["客戶管理", "客戶、聯絡人、標籤、分級", "Spring MVC + JPA"],
    ["商機管理", "階段、金額、預計成交日、風險狀態", "JPA + Specification"],
    ["AI 摘要", "客戶摘要、風險訊號、下一步建議", "Spring AI"],
    ["RAG 知識庫", "產品文件、銷售話術、條款檢索", "pgvector"],
    ["工具呼叫", "讀真實資料、算商機健康分數", "Tool Calling"],
  ];
  return (
    <section>
      <window.SectionTitle id="project" kicker="貫穿案例" title="一套 B2B AI CRM 智慧業務助理"
        lead="從基本 CRM 功能開始，逐步加入 AI 能力。AI 不替代決策，而是提供可追蹤的建議——金額與權限由 Java 算，摘要與草稿由模型寫。" />
      <div style={{ display: "grid", gridTemplateColumns: "1.15fr 1fr", gap: 22, alignItems: "stretch" }} className="hc-project">
        <PCard variant="solid" padding={0} style={{ overflow: "hidden" }}>
          <img src="assets/cover.png" alt="AI CRM Dashboard" style={{ width: "100%", height: 260, objectFit: "cover", display: "block" }} />
          <div style={{ padding: "18px 20px" }}>
            <strong style={{ display: "block", marginBottom: 6 }}>業務打開客戶頁，系統自動整理近況</strong>
            <p style={{ margin: 0, color: "var(--muted)", lineHeight: 1.8, fontSize: "0.94rem" }}>客戶是否活躍？有哪些風險訊號？是否可能流失？該推薦什麼方案？下一步該寄信、約會議還是請主管介入？</p>
          </div>
        </PCard>
        <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: 10 }}>
          {modules.map(([t, d, tech]) => (
            <div key={t} style={{ display: "flex", alignItems: "center", gap: 14, padding: "13px 16px", borderRadius: "var(--r-md)", border: "1px solid var(--border)", background: "var(--surface)" }}>
              <div style={{ flex: 1 }}>
                <strong style={{ fontSize: "0.98rem" }}>{t}</strong>
                <div style={{ color: "var(--muted)", fontSize: "0.86rem", marginTop: 2 }}>{d}</div>
              </div>
              <PBadge tone="accent">{tech}</PBadge>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function Curriculum() {
  const c = window.COURSE;
  return (
    <section>
      <window.SectionTitle id="curriculum" kicker="課程大綱" title="8 大單元，章章相連"
        lead="每個單元都有明確產出，且全部接到同一套 AI CRM 專案。可拆成 4 天工作坊或 Hahow 線上章節。" />
      <div style={{ display: "grid", gap: 12 }}>
        {c.units.map((u, i) => (
          <PAccordion key={u.n} defaultOpen={i === 0}
            summary={
              <span style={{ display: "flex", alignItems: "center", gap: 14, flexWrap: "wrap" }}>
                <span style={{ fontSize: "1.5rem", fontWeight: 900, color: "var(--accent-2)", lineHeight: 1 }}>{u.n}</span>
                <span style={{ fontWeight: 800 }}>{u.t}</span>
              </span>
            }>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 8, margin: "2px 0 16px" }}>
              {u.tags.map((t) => <PBadge key={t} tone="neutral">{t}</PBadge>)}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10, marginBottom: 16 }}>
              {u.points.map((p, j) => (
                <div key={j} style={{ display: "flex", gap: 9, alignItems: "flex-start", fontSize: "0.92rem", color: "var(--fg)" }}>
                  <span style={{ color: "var(--accent)", marginTop: 1 }}><window.Icon name="check" size={16} /></span>{p}
                </div>
              ))}
            </div>
            <div style={{ display: "inline-flex", alignItems: "center", gap: 8, padding: "8px 14px", borderRadius: "var(--r-pill)", background: "color-mix(in oklab, var(--success) 12%, transparent)", color: "var(--success)", fontWeight: 800, fontSize: "0.9rem" }}>
              <window.Icon name="package-check" size={17} />核心產出 · {u.out}
            </div>
          </PAccordion>
        ))}
      </div>
    </section>
  );
}

Object.assign(window, { ProjectShowcase, Curriculum });
