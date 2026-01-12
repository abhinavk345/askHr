import React, { useState } from "react";

export default function AIButtonWithTooltip() {
  const [showTooltip, setShowTooltip] = useState(false);

  return (
    <div style={{ position: "fixed", right: "24px", bottom: "24px", zIndex: 2000 }}>
      <div
        className="ai-button"
        onMouseEnter={() => setShowTooltip(true)}
        onMouseLeave={() => setShowTooltip(false)}
        onClick={() => alert("Open AI Modal")}
      >
        AI
      </div>

      {/* Tooltip */}
      <div className={`help-tooltip ${showTooltip ? "show" : ""}`}>
        <span className="tooltip-close" onClick={() => setShowTooltip(false)}>×</span>
        Hello! Click here to start chatting with AI.
      </div>
    </div>
  );
}
