import React, { useState } from "react";
import { downloadWithProgress } from "./downloadWithProgress";

const API_BASE = "http://localhost:9091/askhr";

function getBadgeLabel(fileType, fileName) {
  const ft = (fileType || "").toUpperCase();
  if (ft === "EXCEL" || fileName?.toLowerCase().endsWith(".xlsx")) return "EXCEL";
  if (ft === "PDF" || fileName?.toLowerCase().endsWith(".pdf")) return "PDF";
  return "FILE";
}

function getBadgeClass(label) {
  switch (label) {
    case "EXCEL":
      return "badge badge-excel";
    case "PDF":
      return "badge badge-pdf";
    default:
      return "badge badge-file";
  }
}


function DownloadAttachmentButton({ attachment, msgIndex, setChat, retryDownload}) {
  const [downloading, setDownloading] = useState(false);

  if (!attachment) return null;

  const fileName = attachment.fileName || "download";
  const badgeLabel = getBadgeLabel(attachment.fileType, fileName);
  const badgeClass = getBadgeClass(badgeLabel);

  const fullUrl =
    attachment.url?.startsWith("http")
      ? attachment.url
      : `${API_BASE}${attachment.url}`;

  const tooltipText = `${fileName} (${badgeLabel})`;

  const updateInlineToast = (state, message) => {
    setChat((prev) => {
      const updated = [...prev];
      if (!updated[msgIndex]) return prev;

      updated[msgIndex] = {
        ...updated[msgIndex],
        downloadState: state,
        downloadMessage: message,
      };
      return updated;
    });
  };

  const doDownload = async () => {
    if (downloading) return;

    try {
      setDownloading(true);

      updateInlineToast("downloading", "⬇️ Downloading... 0%");

      await downloadWithProgress(fullUrl, fileName, (percent) => {
        if (percent === null) {
          updateInlineToast("downloading", "⬇️ Downloading...");
        } else {
          updateInlineToast("downloading", `⬇️ Downloading... ${percent}%`);
        }
      });

      updateInlineToast("success", "✅ Downloaded successfully");
      setTimeout(() => updateInlineToast("idle", ""), 2000);
    } catch (err) {
      console.error(err);
      updateInlineToast("error", "❌ Download failed. Click Retry.");
    } finally {
      setDownloading(false);
    }
  };

  const handleDownload = async (e) => {
    e.stopPropagation();
    await doDownload();
  };


  return (
    <div className="download-wrapper">
      <span className={badgeClass}>{badgeLabel}</span>

      <button
        onClick={handleDownload}
        className={`download-btn ${downloading ? "download-btn-disabled" : ""}`}
        title={tooltipText}
        disabled={downloading}
      >
        {downloading ? <span className="download-spinner" /> : "⬇️"}
      </button>

      {/* store retry function on message itself (optional) */}
      {/* retry handled from InlineDownloadToast */}
    </div>
  );
}

export default DownloadAttachmentButton;
