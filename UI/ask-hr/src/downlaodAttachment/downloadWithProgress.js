export async function downloadWithProgress(url, fileName, onProgress) {
  const res = await fetch(url, { method: "GET" });

  if (!res.ok) throw new Error("Download failed");

  const contentLength = res.headers.get("content-length");
  const total = contentLength ? parseInt(contentLength, 10) : null;

  // fallback if stream not available
  if (!res.body) {
    const blob = await res.blob();
    const blobUrl = window.URL.createObjectURL(blob);

    const a = document.createElement("a");
    a.href = blobUrl;
    a.download = fileName || "download";
    document.body.appendChild(a);
    a.click();
    a.remove();

    window.URL.revokeObjectURL(blobUrl);
    return;
  }

  const reader = res.body.getReader();
  let received = 0;
  const chunks = [];

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    chunks.push(value);
    received += value.length;

    if (total) {
      const percent = Math.round((received / total) * 100);
      onProgress?.(percent);
    } else {
      onProgress?.(null);
    }
  }

  const blob = new Blob(chunks);
  const blobUrl = window.URL.createObjectURL(blob);

  const a = document.createElement("a");
  a.href = blobUrl;
  a.download = fileName || "download";
  document.body.appendChild(a);
  a.click();
  a.remove();

  window.URL.revokeObjectURL(blobUrl);
}
