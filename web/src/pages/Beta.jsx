import { useState } from 'react';

const TESTFLIGHT_URL = 'https://apps.apple.com/us/app/testflight/id899247664';

export default function Beta() {
  const [form, setForm] = useState({
    firstName: '', lastName: '', email: '',
    platform: '', platformEmail: '',
    osVersion: '', deviceModel: '', comments: '',
    honeypot: '',
  });
  const [status, setStatus]   = useState('idle'); // idle | submitting | success | error
  const [errorMsg, setErrorMsg] = useState('');

  const set = field => e => setForm(f => ({ ...f, [field]: e.target.value }));

  async function handleSubmit(e) {
    e.preventDefault();
    if (form.honeypot) return;
    setStatus('submitting');
    setErrorMsg('');
    try {
      const res  = await fetch('/api/v1/beta-signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      });
      const data = await res.json();
      if (!res.ok) {
        setErrorMsg(data.error ?? 'Something went wrong.');
        setStatus('error');
      } else {
        setStatus('success');
      }
    } catch {
      setErrorMsg('Network error — please try again.');
      setStatus('error');
    }
  }

  if (status === 'success') {
    return (
      <div className="beta-page">
        <div className="beta-hero">
          <div className="beta-success-icon">✓</div>
          <h1>Request Received</h1>
          <p>
            {form.platform === 'testflight'
              ? "We'll add you to TestFlight and you'll receive an invite email from Apple shortly after."
              : "We'll add your Google account to the internal testing track — you should receive access within a day."}
          </p>
          <p className="beta-success-note">
            Questions? Reach out at <a href="mailto:dev@nml.wtf">dev@nml.wtf</a>.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="beta-page">
      <div className="beta-hero">
        <h1>Join Beta Testing</h1>
        <p>Help shape TabletHID by testing pre-release builds. Fill out the form and we'll add you manually.</p>
      </div>

      <form className="beta-form" onSubmit={handleSubmit} noValidate>
        {/* Honeypot — hidden from real users */}
        <input
          type="text"
          name="website"
          value={form.honeypot}
          onChange={set('honeypot')}
          tabIndex={-1}
          aria-hidden="true"
          className="beta-honeypot"
        />

        <div className="beta-row">
          <label className="beta-field">
            <span>First Name</span>
            <input
              type="text"
              required
              maxLength={50}
              autoComplete="given-name"
              placeholder="Jane"
              value={form.firstName}
              onChange={set('firstName')}
            />
          </label>
          <label className="beta-field">
            <span>Last Name</span>
            <input
              type="text"
              required
              maxLength={50}
              autoComplete="family-name"
              placeholder="Smith"
              value={form.lastName}
              onChange={set('lastName')}
            />
          </label>
        </div>

        <label className="beta-field">
          <span>Contact Email</span>
          <input
            type="email"
            required
            maxLength={200}
            autoComplete="email"
            placeholder="jane@example.com"
            value={form.email}
            onChange={set('email')}
          />
          <span className="beta-hint">Used to follow up only — not shared.</span>
        </label>

        <label className="beta-field">
          <span>Platform</span>
          <select required value={form.platform} onChange={e => {
            setForm(f => ({ ...f, platform: e.target.value, platformEmail: '', osVersion: '', deviceModel: '' }));
          }}>
            <option value="" disabled>Select a platform…</option>
            <option value="google_play">Google Play (Android)</option>
            <option value="testflight">Apple TestFlight (iOS)</option>
          </select>
        </label>

        {form.platform === 'google_play' && (
          <>
            <label className="beta-field">
              <span>Google Account Email</span>
              <input
                type="email"
                required
                maxLength={200}
                placeholder="jane@gmail.com"
                value={form.platformEmail}
                onChange={set('platformEmail')}
              />
              <span className="beta-hint">
                Must be the email linked to your Google Play account — this is what gets added to the tester list.
              </span>
            </label>
            <div className="beta-row">
              <label className="beta-field">
                <span>Android Version <em>(optional)</em></span>
                <input
                  type="text"
                  maxLength={20}
                  placeholder="e.g. 14"
                  value={form.osVersion}
                  onChange={set('osVersion')}
                />
              </label>
              <label className="beta-field">
                <span>Device Model <em>(optional)</em></span>
                <input
                  type="text"
                  maxLength={100}
                  placeholder="e.g. Moto G Stylus 2022"
                  value={form.deviceModel}
                  onChange={set('deviceModel')}
                />
              </label>
            </div>
          </>
        )}

        {form.platform === 'testflight' && (
          <>
            <div className="beta-tf-box">
              <p className="beta-tf-title">TestFlight is required</p>
              <p className="beta-tf-desc">
                Install TestFlight on your device first — it's the app Apple uses to distribute beta builds.
              </p>
              <a
                href={TESTFLIGHT_URL}
                target="_blank"
                rel="noreferrer"
                className="beta-tf-link"
              >
                Download TestFlight from the App Store →
              </a>
              <ol className="beta-tf-steps">
                <li>Download TestFlight (free) from the App Store.</li>
                <li>Once added, you'll receive an email from Apple with an invite link.</li>
                <li>Tap the invite link — it opens in TestFlight and installs TabletHID beta.</li>
                <li>Future updates arrive automatically through TestFlight.</li>
              </ol>
            </div>

            <label className="beta-field">
              <span>Apple ID Email</span>
              <input
                type="email"
                required
                maxLength={200}
                placeholder="jane@icloud.com"
                value={form.platformEmail}
                onChange={set('platformEmail')}
              />
              <span className="beta-hint">
                Must match your Apple ID — this is where the TestFlight invite will be sent.
              </span>
            </label>
            <div className="beta-row">
              <label className="beta-field">
                <span>iOS Version <em>(optional)</em></span>
                <input
                  type="text"
                  maxLength={20}
                  placeholder="e.g. 17.4"
                  value={form.osVersion}
                  onChange={set('osVersion')}
                />
              </label>
              <label className="beta-field">
                <span>Device Model <em>(optional)</em></span>
                <input
                  type="text"
                  maxLength={100}
                  placeholder="e.g. iPhone 15"
                  value={form.deviceModel}
                  onChange={set('deviceModel')}
                />
              </label>
            </div>
          </>
        )}

        <label className="beta-field">
          <span>Comments <em>(optional)</em></span>
          <textarea
            maxLength={500}
            rows={3}
            placeholder="Any devices, use cases, or features you'd like to focus on…"
            value={form.comments}
            onChange={set('comments')}
          />
        </label>

        {status === 'error' && (
          <div className="beta-error">{errorMsg}</div>
        )}

        <button
          type="submit"
          className="btn btn-primary beta-submit"
          disabled={status === 'submitting' || !form.platform}
        >
          {status === 'submitting' ? 'Sending…' : 'Request Access'}
        </button>
      </form>
    </div>
  );
}
