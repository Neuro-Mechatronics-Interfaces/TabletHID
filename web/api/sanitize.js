export function trimAndClamp(s, maxLen) {
  if (typeof s !== 'string') return '';
  return s.trim().slice(0, maxLen);
}

export function sanitizeTags(tags) {
  if (!Array.isArray(tags)) return [];
  return tags
    .map((t) => trimAndClamp(t, 32))
    .filter((t) => t.length > 0)
    .slice(0, 8);
}

export function isValidMode(s) {
  return s === 'touch_mouse' || s === 'gamepad';
}

export function isValidPlatform(s) {
  return s === 'android' || s === 'ios';
}

export function isValidIso8601(s) {
  return typeof s === 'string' && s.length > 0 && !isNaN(Date.parse(s));
}

export function stripControlChars(s) {
  if (typeof s !== 'string') return '';
  return s.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F]/g, '');
}
