import { Link, useParams, useNavigate } from 'react-router-dom';

const FAQS = {
  common: [
    {
      q: 'The host doesn\'t see TabletHID in its Bluetooth device list.',
      a: 'Make sure you tapped "Make Discoverable" in the app first — the tablet only broadcasts for 120 seconds. On Windows, go to Settings → Bluetooth & devices → Add device → Bluetooth. On macOS, open System Settings → Bluetooth. If you\'ve previously paired, use the Reconnect button instead.',
    },
    {
      q: 'I paired successfully but inputs aren\'t registering on the host.',
      a: 'After any app update that changes the HID descriptor you must remove the old pairing on both sides and re-pair: on Windows, click the device → Remove device; on the tablet, go to Bluetooth settings and forget the host. Then pair fresh from the Setup screen.',
    },
    {
      q: 'The cursor or gamepad stops responding after a few minutes.',
      a: 'The device may have gone to sleep or dropped the Bluetooth connection. Return to the Setup screen, tap Reconnect, and the session resumes without re-pairing.',
    },
    {
      q: 'Can I switch between Touch Mouse and Gamepad without re-pairing?',
      a: 'Yes. Both modes share a single Bluetooth bond using a combined HID descriptor. Exit the current mode, choose the other from the Home screen, and tap Reconnect — the host will reattach in the new mode instantly.',
    },
  ],
  ios: [
    {
      q: 'The app shows "Transport unavailable" on my iPad.',
      a: 'TabletHID uses Bluetooth LE peripheral mode (Core Bluetooth). Make sure Bluetooth is enabled in iOS Settings and that you\'ve granted Bluetooth permission to the app (Settings → TabletHID → Bluetooth → Allow).',
    },
    {
      q: 'Does the connection stay active when I lock my iPad?',
      a: 'The app requires the "Acts as a Bluetooth LE accessory" background mode to stay connected while the screen is off. This is enabled in release builds. If you are running a development build without this entitlement, the peripheral will disconnect when the app is backgrounded.',
    },
    {
      q: 'My Mac or PC doesn\'t discover the iPad.',
      a: 'iPad uses Bluetooth LE (HOGP) rather than Classic Bluetooth HID, which some older operating systems don\'t support as a peripheral target. Windows 11 and macOS 13 Ventura or later work reliably. Try tapping "Prepare Transport" again if the timer expires.',
    },
    {
      q: 'Gamepad inputs work in one app but not another on macOS.',
      a: 'Gamepad HID devices on macOS require an app to open the IOHIDDevice before it receives events — unlike a mouse, which the OS consumes automatically. Use a game, Steam, or a HID testing utility (such as Controlly or a WebHID test page) that actively opens gamepad input.',
    },
  ],
  android: [
    {
      q: 'The app shows "Bluetooth permission not granted".',
      a: 'On Android 12+, TabletHID needs BLUETOOTH_CONNECT and BLUETOOTH_SCAN permissions. Go to Settings → Apps → TabletHID → Permissions → Nearby devices → Allow.',
    },
    {
      q: 'My device says Bluetooth HID is not supported.',
      a: 'TabletHID requires the BluetoothHidDevice profile (Android 9 / API 28+). Most Android tablets support it, but a handful of lower-end devices or custom ROMs omit it. Check your device specs for "HID Device profile" support.',
    },
    {
      q: 'Windows asks for a PIN when pairing.',
      a: 'Standard HID devices pair without a PIN. If Windows prompts for one, dismiss the dialog, wait a moment, and try again. This sometimes happens on the very first pairing attempt.',
    },
    {
      q: 'The gamepad layout looks wrong or controls are off-screen.',
      a: 'Enter Edit Mode (tap the settings gear → Edit Layout) and drag controls back into view. Pinch on any control to resize it. Tap Done to save. Layouts are stored per profile — you can create a new profile to start from the default layout.',
    },
  ],
};

export default function Support() {
  const { platform } = useParams();
  const tab = platform === 'ios' ? 'ios' : platform === 'android' ? 'android' : 'common';

  return (
    <div className="page">
      <h1 style={{ fontSize: '1.9rem', fontWeight: 800, marginBottom: 8 }}>Support</h1>
      <p style={{ color: 'var(--text-muted)', marginBottom: 32 }}>
        Troubleshooting guides for TabletHID on iOS and Android.
      </p>

      <div className="platform-tabs">
        <Link to="/support" className={`tab${tab === 'common' ? ' active' : ''}`}>General</Link>
        <Link to="/support/ios" className={`tab${tab === 'ios' ? ' active' : ''}`}>iOS</Link>
        <Link to="/support/android" className={`tab${tab === 'android' ? ' active' : ''}`}>Android</Link>
      </div>

      <div className="support-section">
        <h2>
          {tab === 'common' && 'General Troubleshooting'}
          {tab === 'ios' && 'iOS — iPad'}
          {tab === 'android' && 'Android — Tablet'}
        </h2>
        <div className="faq">
          {FAQS[tab].map((item, i) => (
            <div className="faq-item" key={i}>
              <div className="faq-q">{item.q}</div>
              <div className="faq-a" dangerouslySetInnerHTML={{ __html: item.a }} />
            </div>
          ))}
        </div>
      </div>

      <div className="contact-card">
        <h3>Still stuck?</h3>
        <p>
          Open a GitHub issue and include your device model, OS version, and host OS.
          Bug reports with reproduction steps are resolved fastest.
        </p>
        <a href="https://github.com/maxmurphy/TabletHID/issues" target="_blank" rel="noreferrer">
          Open an issue on GitHub →
        </a>
      </div>
    </div>
  );
}
