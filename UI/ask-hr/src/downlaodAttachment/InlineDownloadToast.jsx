import React from "react";
import "./inlineDownloadToast.css";

function InlineDownloadToast({ state, message, onRetry }) {
  if (!state || state === "idle") return null;

  return (
    <div className={`inline-toast inline-toast-${state}`}>
      <div className="inline-toast-row">
        <span>{message}</span>

        {state === "error" && (
          <button className="inline-toast-retry" onClick={onRetry}>
            Retry
          </button>
        )}
      </div>
    </div>
  );
}

export default InlineDownloadToast;
