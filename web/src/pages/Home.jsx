import { useState } from 'react';
import { Link } from 'react-router-dom';

function IconMouse() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="5" y="2" width="14" height="20" rx="7"/>
      <line x1="12" y1="2" x2="12" y2="10"/>
    </svg>
  );
}

function IconGamepad() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="6" y1="12" x2="10" y2="12"/><line x1="8" y1="10" x2="8" y2="14"/>
      <circle cx="15" cy="11" r=".5" fill="currentColor"/><circle cx="17" cy="13" r=".5" fill="currentColor"/>
      <path d="M21 6H3a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h18a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2z"/>
    </svg>
  );
}

function IconBluetooth() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="6.5 6.5 17.5 17.5 12 23 12 1 17.5 6.5 6.5 17.5"/>
    </svg>
  );
}

function IconProfiles() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/>
    </svg>
  );
}

function AppleLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 814 1000" fill="currentColor">
      <path d="M788.1 340.9c-5.8 4.5-108.2 62.2-108.2 190.5 0 148.4 130.3 200.9 134.2 202.2-.6 3.2-20.7 71.9-68.7 141.9-42.8 61.6-87.5 123.1-155.5 123.1s-85.5-39.5-164-39.5c-76.5 0-103.7 40.8-165.9 40.8s-105-37.5-138.9-100.3C27.6 801.3 0 716.6 0 638.7c0-192.3 124.4-294.1 246.9-294.1 64.1 0 117.4 42.2 158.1 42.2 39.2 0 100.7-44.9 173.6-44.9zm-194.3-221.6c31.1-36.9 53.1-88.1 53.1-139.3 0-7.1-.6-14.3-1.9-20.1-50.6 1.9-110.8 33.7-147.1 75.8-28.5 32.4-55.1 83.6-55.1 135.5 0 7.8 1.3 15.6 1.9 18.1 3.2.6 8.4 1.3 13.6 1.3 45.4 0 102.5-30.4 135.5-71.3z"/>
    </svg>
  );
}

function AndroidLogo() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
      <path d="M17.523 15.341a1 1 0 1 1-2 0 1 1 0 0 1 2 0zm-9.046 0a1 1 0 1 1-2 0 1 1 0 0 1 2 0zM2.869 8.562l1.732-3a.5.5 0 0 1 .865.5L3.734 9.062a.5.5 0 0 1-.865-.5zm18.262 0a.5.5 0 0 1-.865.5l-1.732-3a.5.5 0 0 1 .865-.5l1.732 3zM8.5 3.354l.76-1.316a.5.5 0 1 1 .866.5L9.232 3.9A7.017 7.017 0 0 1 12 3.5c.975 0 1.905.198 2.768.4l-.894-1.362a.5.5 0 1 1 .866-.5l.76 1.316C17.738 4.56 19.5 7.113 19.5 10v6.5a1 1 0 0 1-1 1h-13a1 1 0 0 1-1-1V10c0-2.887 1.762-5.44 4.5-6.646z"/>
    </svg>
  );
}

const PLAY_OPT_IN_KEY = 'play_tester_optin';
const PLAY_TESTING_URL = 'https://play.google.com/apps/testing/com.tablet.hid';
const PLAY_STORE_URL   = 'https://play.google.com/store/apps/details?id=com.tablet.hid';

export default function Home() {
  const [hasOptedIn, setHasOptedIn] = useState(
    () => localStorage.getItem(PLAY_OPT_IN_KEY) === 'true'
  );

  function handleJoinBeta() {
    localStorage.setItem(PLAY_OPT_IN_KEY, 'true');
    setHasOptedIn(true);
  }

  return (
    <>
      <section className="hero">
        <div className="hero-badge">Bluetooth HID Peripheral</div>
        <h1>Turn Your Tablet Into a<br /><span>Wireless Controller</span></h1>
        <p>
          TabletHID transforms your Android tablet, and experimental iOS builds,
          into a Bluetooth mouse or Xbox-style gamepad — no extra hardware required.
        </p>
        <div className="hero-btns">
          <Link to="/support" className="btn btn-primary">Get Support</Link>
          <a href="https://github.com/Neuro-Mechatronics-Interfaces/TabletHID" className="btn btn-outline" target="_blank" rel="noreferrer">View on GitHub</a>
        </div>
        <div className="platform-badges">
          <a href="https://apps.apple.com/app/tablethid/id6766346670" className="badge">
            <AppleLogo />
            App Store — iOS
          </a>

          {/* Android: two-step opt-in flow */}
          <div className="badge-group">
            <a
              href={PLAY_TESTING_URL}
              className={`badge${hasOptedIn ? ' badge-muted' : ''}`}
              target="_blank"
              rel="noreferrer"
              onClick={handleJoinBeta}
            >
              <AndroidLogo />
              {hasOptedIn ? 'Joined Beta ✓' : 'Join Android Beta'}
            </a>
            {hasOptedIn ? (
              <a
                href={PLAY_STORE_URL}
                className="badge badge-primary"
                target="_blank"
                rel="noreferrer"
              >
                <AndroidLogo />
                Download on Google Play
              </a>
            ) : (
              <span className="badge badge-locked" aria-label="Join the beta above to unlock">
                <AndroidLogo />
                Google Play — join beta first
              </span>
            )}
          </div>
        </div>
      </section>

      <section className="features">
        <div className="page">
          <div className="section-label">
            <h2>Two Modes, One App</h2>
            <p>Android uses one Classic Bluetooth HID bond. iOS uses an experimental BLE HID path while host validation continues.</p>
          </div>
          <div className="cards">
            <div className="card">
              <div className="card-icon"><IconMouse /></div>
              <h3>Touch Mouse</h3>
              <p>Full-screen trackpad with configurable left/right click zones, sensitivity, and drag behaviour.</p>
              <ul>
                <li>Touch and Mouse input modes</li>
                <li>Static, dynamic, or shared follower click zones</li>
                <li>Momentary and latching clicks</li>
                <li>Sensitivity 1–10 slider</li>
                <li>Keyboard shortcut panel when macros are configured</li>
              </ul>
            </div>
            <div className="card">
              <div className="card-icon"><IconGamepad /></div>
              <h3>Gamepad</h3>
              <p>Xbox-style virtual controller with analog sticks, triggers, face buttons, shoulder buttons, and D-pad.</p>
              <ul>
                <li>Analog sticks with deadzone control</li>
                <li>Analog triggers (LT / RT)</li>
                <li>Single-stick layout with L/R output toggle</li>
                <li>Windows and Mac keyboard macro buttons</li>
                <li>Custom labels and configurable haptics</li>
                <li>Android: drag to reposition and pinch to resize</li>
              </ul>
            </div>
            <div className="card">
              <div className="card-icon"><IconBluetooth /></div>
              <h3>Single Bond</h3>
              <p>Android shares one Bluetooth pairing across modes. iOS exposes the same report map through experimental BLE HID.</p>
              <ul>
                <li>Combined mouse, gamepad, and keyboard report map</li>
                <li>Smart reconnect to last host</li>
                <li>Windows 11 &amp; macOS targets</li>
                <li>iOS physical-device validation pending</li>
              </ul>
            </div>
            <div className="card">
              <div className="card-icon"><IconProfiles /></div>
              <h3>Profiles</h3>
              <p>Built-in and custom profiles for different use cases, including accessibility presets.</p>
              <ul>
                <li>Default, Access Basic, Access Advanced</li>
                <li>Per-profile layouts &amp; config</li>
                <li>Custom profiles with any name</li>
                <li>Persisted across app restarts</li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      <section>
        <div className="page" style={{ textAlign: 'center' }}>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: 8 }}>Need help getting set up?</h2>
          <p style={{ color: 'var(--text-muted)', marginBottom: 24 }}>
            Step-by-step pairing guides for Windows and macOS are in the app.<br />
            For troubleshooting, visit the support page.
          </p>
          <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap' }}>
            <Link to="/support/ios" className="btn btn-primary">iOS Support</Link>
            <Link to="/support/android" className="btn btn-outline">Android Support</Link>
          </div>
        </div>
      </section>
    </>
  );
}
