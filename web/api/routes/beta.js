import { stripControlChars } from '../sanitize.js';

const NOTIFY_TO   = 'dev@nml.wtf';
const NOTIFY_FROM = 'TabletHID Beta <tablet@nml.wtf>';

function isEmail(s) {
  return typeof s === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim()) && s.length <= 200;
}

// Coerce untrusted body fields to strings. Non-string values (numbers, objects,
// arrays) previously threw on .trim() inside this async handler, which Express 4
// does not catch, producing an unhandled rejection that terminates the process.
// Single-line fields also drop CR/LF/TAB, since names land in the email subject.
function str(v, maxLen = 1000) {
  return typeof v === 'string' ? stripControlChars(v).replace(/[\r\n\t]/g, ' ').slice(0, maxLen) : '';
}

export async function betaSignup(req, res) {
  const raw = req.body && typeof req.body === 'object' ? req.body : {};
  const honeypot      = str(raw.honeypot);
  const firstName     = str(raw.firstName, 200);
  const lastName      = str(raw.lastName, 200);
  const email         = str(raw.email, 400);
  const platform      = str(raw.platform, 40);
  const platformEmail = str(raw.platformEmail, 400);
  const osVersion     = str(raw.osVersion);
  const deviceModel   = str(raw.deviceModel);
  const comments      = typeof raw.comments === 'string' ? raw.comments.replace(/[\x00-\x09\x0B\x0C\x0E-\x1F]/g, '') : '';

  // Silent bot discard
  if (honeypot) {
    res.status(200).json({ ok: true });
    return;
  }

  const errors = [];
  if (!firstName.trim())                                  errors.push('First name is required.');
  if (!lastName.trim())                                   errors.push('Last name is required.');
  if (!isEmail(email))                                    errors.push('Valid contact email is required.');
  if (!['google_play', 'testflight'].includes(platform)) errors.push('Please select a platform.');
  if (!isEmail(platformEmail))                            errors.push(
    platform === 'testflight'
      ? 'Valid Apple ID email is required.'
      : 'Valid Google Account email is required.',
  );
  if (firstName.length > 100 || lastName.length > 100) errors.push('Name too long.');
  if (comments.length > 500)    errors.push('Comments must be 500 characters or fewer.');
  if (osVersion.length > 20)    errors.push('OS version too long.');
  if (deviceModel.length > 100) errors.push('Device model too long.');

  if (errors.length) {
    res.status(400).json({ error: errors[0] });
    return;
  }

  const apiKey = process.env.RESEND_API_KEY;
  if (!apiKey) {
    console.error('[beta-signup] RESEND_API_KEY not set');
    res.status(500).json({ error: 'Email service not configured on the server.' });
    return;
  }

  const platformLabel      = platform === 'google_play' ? 'Google Play (Android)' : 'Apple TestFlight (iOS)';
  const platformEmailLabel = platform === 'google_play' ? 'Google Account Email'  : 'Apple ID Email';
  const addSteps = platform === 'google_play'
    ? 'Play Console → Testing → Internal testing → Manage testers\n   → Add email listed below'
    : 'App Store Connect → TestFlight → External Testers → + Add Tester\n   → Add email listed below';

  const body = [
    'New beta tester request via tablet.nml.wtf',
    '',
    `Platform:      ${platformLabel}`,
    `Name:          ${firstName.trim()} ${lastName.trim()}`,
    `Contact Email: ${email.trim()}`,
    '',
    '── To add this tester ───────────────────────────────',
    `   ${addSteps}`,
    '',
    `── ${platformEmailLabel} ─────────────────────────────`,
    `   ${platformEmail.trim()}`,
    ...(osVersion.trim()  ? [`   OS Version: ${osVersion.trim()}`]  : []),
    ...(deviceModel.trim() ? [`   Device:      ${deviceModel.trim()}`] : []),
    '',
    ...(comments.trim()
      ? ['── Comments ─────────────────────────────────────────',
         `   ${comments.trim().replace(/\n/g, '\n   ')}`,
         '']
      : []),
    '── Submitted ────────────────────────────────────────',
    `   ${new Date().toISOString()}`,
    `   IP: ${req.ip ?? 'unknown'}`,
  ].join('\n');

  try {
    const emailRes = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        from:     NOTIFY_FROM,
        to:       [NOTIFY_TO],
        reply_to: email.trim(),
        subject:  `Beta Request — ${platformLabel} — ${firstName.trim()} ${lastName.trim()}`,
        text:     body,
      }),
    });

    if (!emailRes.ok) {
      const errText = await emailRes.text();
      console.error('[beta-signup] Resend error', emailRes.status, errText);
      res.status(500).json({ error: 'Failed to send notification. Please try again.' });
      return;
    }

    res.status(200).json({ ok: true });
  } catch (err) {
    console.error('[beta-signup] fetch error', err);
    res.status(500).json({ error: 'Failed to send notification. Please try again.' });
  }
}
