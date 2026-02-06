export function parseAIResponse(rawText) {
  if (!rawText) return { message: "", attachment: null };

  // try parse json
  try {
    const obj = JSON.parse(rawText);

    // only handle if structure matches
    if (obj && typeof obj === "object" && obj.message) {
      return {
        message: obj.message,
        attachment: obj.attachment || null,
      };
    }
  } catch (e) {
    // not json => ignore
  }

  // fallback plain text
  return { message: rawText, attachment: null };
}