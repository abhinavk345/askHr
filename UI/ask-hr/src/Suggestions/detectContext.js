export function detectContext(text) {
  if (!text) return "DEFAULT";

  const t = text.toLowerCase();

  // ticket context
  if (
    t.includes("ticket") ||
    t.includes("status") ||
    t.includes("resolve") ||
    t.includes("update my ticket") ||
    t.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i)
  ) {
    return "TICKET";
  }

  // leave context
  if (
    t.includes("leave") ||
    t.includes("apply leave") ||
    t.includes("leave policy") ||
    t.includes("planned leave") ||
    t.includes("paternity") ||
    t.includes("maternity")
  ) {
    return "LEAVE";
  }

  // policy context
  if (
    t.includes("policy") ||
    t.includes("holiday") ||
    t.includes("insurance") ||
    t.includes("wfh")
  ) {
    return "POLICY";
  }

  return "DEFAULT";
}
