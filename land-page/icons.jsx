// Lucide → React icon helper, shared via window.Icon
(function () {
  const toPascal = (s) => s.replace(/(^|-)(\w)/g, (_, __, c) => c.toUpperCase());
  function Icon({ name, size = 22, stroke = 2, style, ...rest }) {
    const L = window.lucide || {};
    const pas = toPascal(name);
    const node = (L.icons && (L.icons[pas] || L.icons[name])) || L[pas] || L[name];
    if (!node) return null;
    // lucide nodes come as ["svg", attrs, [[tag,attrs],...]] or an iconNode array of [tag,attrs]
    let arr;
    if (Array.isArray(node) && node[0] === "svg") arr = node[2] || [];
    else if (Array.isArray(node)) arr = node;
    else arr = node.iconNode || [];
    const children = arr.map((c, i) =>
      React.createElement(c[0], { key: i, ...c[1] })
    );
    return React.createElement(
      "svg",
      {
        width: size,
        height: size,
        viewBox: "0 0 24 24",
        fill: "none",
        stroke: "currentColor",
        strokeWidth: stroke,
        strokeLinecap: "round",
        strokeLinejoin: "round",
        style: { display: "block", flex: "0 0 auto", ...style },
        ...rest,
      },
      children
    );
  }
  window.Icon = Icon;
})();
