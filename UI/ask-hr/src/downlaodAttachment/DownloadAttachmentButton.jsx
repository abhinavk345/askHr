import React from "react";

const API_BASE = "http://localhost:9091/askhr";

function DownloadAttachmentButton({ attachment }) {
  if (!attachment) return null;

  const fullUrl = attachment.url.startsWith("http")
    ? attachment.url
    : `${API_BASE}${attachment.url}`;

  const handleDownload = (e) => {
    e.stopPropagation();
    window.open(fullUrl, "_blank");
  };

  return (
    <button
      onClick={handleDownload}
      className="download-btn"
      title={`Download ${attachment.fileName}`}
    >
      ⬇️
    </button>
  );
}

export default DownloadAttachmentButton;
