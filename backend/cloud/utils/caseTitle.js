/**
 * Derives a short display title for a case from its original narrative --
 * used by list-cases operations so a case list has a title without storing
 * one redundantly; the narrative is always already persisted at creation
 * time, so this is computed fresh from that single source of truth rather
 * than cached on the case itself.
 */
function deriveTitle(narrative) {
  const singleLine = (narrative || '').replace(/\n/g, ' ').trim();
  if (singleLine.length > 60) {
    return `${singleLine.slice(0, 60)}…`;
  }
  return singleLine;
}

module.exports = { deriveTitle };
