export function parseAttachmentFromText(text) {
  if (!text) return { cleanText: "", attachment: null };

  const regex = /\[\[ATTACHMENT\|([A-Z]+)\|([^|]+)\|([^\]]+)\]\]/;
  const match = text.match(regex);

  if (!match) return { cleanText: text, attachment: null };

  const fileType = match[1];
  const fileName = match[2];
  const url = match[3];

  const cleanText = text.replace(match[0], "").trim();

  return {
    cleanText,
    attachment: { fileType, fileName, url },
  };
}
