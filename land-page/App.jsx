// App composition + enrol toast + mount
function Toast({ show }) {
  return (
    <div style={{
      position: "fixed", left: "50%", bottom: show ? 28 : -80, transform: "translateX(-50%)",
      transition: "bottom var(--dur-med) var(--ease-out)", zIndex: 200,
      display: "flex", alignItems: "center", gap: 12, padding: "14px 22px",
      borderRadius: "var(--r-pill)", background: "var(--fg)", color: "var(--bg)",
      boxShadow: "var(--shadow-lift)", fontWeight: 800,
    }}>
      <window.Icon name="party-popper" size={20} />已加入購物車 · 早鳥價已為你鎖定！
    </div>
  );
}

function App() {
  const [toast, setToast] = React.useState(false);
  const enroll = React.useCallback(() => {
    setToast(true);
    window.clearTimeout(window.__t);
    window.__t = window.setTimeout(() => setToast(false), 2600);
  }, []);

  return (
    <div>
      <window.Header onEnroll={enroll} />
      <window.Hero />
      <div className="hc-body" style={{
        maxWidth: 1180, margin: "0 auto", padding: "clamp(32px,5vw,56px) clamp(16px,4vw,40px)",
        display: "grid", gridTemplateColumns: "minmax(0,1fr) 360px", gap: 40, alignItems: "start",
      }}>
        <main style={{ display: "grid", gap: "clamp(40px, 5vw, 64px)", minWidth: 0 }}>
          <window.Intro />
          <window.Features />
          <window.Audience />
          <window.ProjectShowcase />
          <window.Curriculum />
          <window.Instructor />
          <window.Faq />
          <window.PricingCTA onEnroll={enroll} />
        </main>
        <aside className="hc-aside">
          <window.PurchaseCard onEnroll={enroll} />
        </aside>
      </div>
      <window.Footer />
      <Toast show={toast} />
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("hc-app")).render(<App />);
