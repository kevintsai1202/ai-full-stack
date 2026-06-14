import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "../course-data.js";
import App from "./App.jsx";
import "../styles.css";

const root = createRoot(document.getElementById("root"));

root.render(
  <StrictMode>
    <App course={window.COURSE} />
  </StrictMode>
);
