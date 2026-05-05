import { Link, useParams } from 'react-router-dom';

import imgLanding          from '../../img/Landing - Android.png';
import imgMousePairing     from '../../img/Mouse Pairing - Android.png';
import imgMouseDynamic1    from '../../img/Mouse Dynamic 1 - Android.png';
import imgMouseDynamic2    from '../../img/Mouse Dynamic 2 - Android.png';
import imgMouseConfig      from '../../img/Mouse Config - Android.png';
import imgMouseStatic      from '../../img/Mouse Config Static - Android.png';
import imgGamepadConnect   from '../../img/Gamepad Connect - Android.png';
import imgGamepadReconnect from '../../img/Gamepad Reconnect - Android.png';
import imgGamepadLandscape from '../../img/Gamepad Layout Landscape - Android.png';

import imgTabletLanding      from '../../img/Landing Permissions - Android Tablet.png';
import imgTabletMousePairing from '../../img/Touch Mouse Pairing - Android Tablet.png';
import imgTabletMouseDynamic from '../../img/Touch Mouse Dynamic - Android Tablet.png';
import imgTabletMouseConfig  from '../../img/Touch Mouse Configuration - Android Tablet.png';
import imgTabletMouseStatic  from '../../img/Touch Mouse Static - Android Tablet.png';
import imgTabletGamepad      from '../../img/Touch Gamepad - Android Tablet.png';
import imgIosHomeIpad      from '../../img/Simulator Screenshot - iPad (A16) - 2026-05-04 at 18.02.43.png';
import imgIosGamepadIpad   from '../../img/Simulator Screenshot - iPad (A16) - 2026-05-04 at 18.03.10.png';
import imgIosSettingsIpad  from '../../img/Simulator Screenshot - iPad (A16) - 2026-05-04 at 18.03.58.png';
import imgIosTouchIpad     from '../../img/Simulator Screenshot - iPad (A16) - 2026-05-04 at 18.04.43.png';
import imgIosSetupPhone    from '../../img/Simulator Screenshot - iPhone Air - 2026-05-04 at 17.56.49.png';
import imgIosMousePhone    from '../../img/Simulator Screenshot - iPhone Air - 2026-05-04 at 17.56.52.png';
import imgIosGamepadPhone  from '../../img/Simulator Screenshot - iPhone Air - 2026-05-04 at 17.57.01.png';
import imgIosHomePhone     from '../../img/Simulator Screenshot - iPhone Air - 2026-05-04 at 17.58.36.png';
import imgIosAppSettings   from '../../img/Simulator Screenshot - iPhone Air - 2026-05-05 at 09.56.36.png';

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

function Step({ tag, title, imgs, compact, reverse, children }) {
  return (
    <div className={`walkthrough-step${reverse ? ' reverse' : ''}`}>
      <div className={`step-shots${compact ? ' compact' : ''}`}>
        {imgs.map((src, i) => (
          <img key={i} src={src} alt="" />
        ))}
      </div>
      <div className="step-text">
        <span className="step-tag">{tag}</span>
        <div className="step-title">{title}</div>
        <div className="step-desc">{children}</div>
      </div>
    </div>
  );
}

function LogInstructions({ platform }) {
  if (platform === 'android') {
    return (
      <div className="log-guide">
        <h2>Pulling Local Logs</h2>
        <p>
          Turn on <b>Enable local session logging</b> in TabletHID settings, connect
          to a host, reproduce the issue, then leave the control screen so the log
          can close. Each session writes a <code>.config</code> snapshot and a{' '}
          <code>.log</code> event file.
        </p>
        <div className="log-steps">
          <div>
            <h3>Android with adb</h3>
            <ol>
              <li>Enable USB debugging on the Android tablet and connect it to your laptop.</li>
              <li>Run <code>adb devices</code> and accept the device trust prompt.</li>
              <li>Pull the session folder from app external storage.</li>
            </ol>
            <pre>{`adb pull /sdcard/Android/data/com.tablet.hid/files/sessions ./TabletHID-sessions`}</pre>
          </div>
          <div>
            <h3>Files app fallback</h3>
            <p>
              On devices that expose app storage, browse to{' '}
              <code>Android/data/com.tablet.hid/files/sessions</code> and copy the
              newest <code>.config</code> and <code>.log</code> files to your laptop.
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (platform === 'ios') {
    return (
      <div className="log-guide">
        <h2>Pulling Local Logs</h2>
        <p>
          Turn on <b>Enable local session logging</b> in TabletHID settings, connect
          to a host, reproduce the issue, then leave the control screen so the log
          can close. Logs are written to <code>Files app &gt; TabletHID &gt; sessions</code>.
        </p>
        <div className="log-steps">
          <div>
            <h3>macOS Finder</h3>
            <ol>
              <li>Connect the iPhone or iPad to your Mac with USB and trust the computer.</li>
              <li>Open Finder, select the device in the sidebar, then open the Files tab.</li>
              <li>Expand TabletHID and drag the <code>sessions</code> folder to your Mac.</li>
            </ol>
          </div>
          <div>
            <h3>Files app or Windows</h3>
            <p>
              From the iOS Files app, open <code>On My iPhone/iPad &gt; TabletHID &gt; sessions</code>
              and share or AirDrop the newest files. On Windows, use Apple Devices or
              iTunes File Sharing, select TabletHID, then save the <code>sessions</code> folder.
            </p>
          </div>
        </div>
      </div>
    );
  }

  return null;
}

function IosWalkthrough() {
  return (
    <>
      <div className="walkthrough-block">
        <div className="walkthrough-block-label">Getting started</div>

        <Step tag="Home screen" title="Choose a mode and profile" imgs={[imgIosHomeIpad, imgIosHomePhone]} compact>
          The iOS home screen mirrors the Android flow: choose <b>Touch Mouse</b> for
          a trackpad-style cursor surface or <b>Gamepad</b> for an Xbox-style virtual
          controller. The selected profile controls which layout and bindings are
          loaded before you enter a mode.
          <br /><br />
          <b>Default</b> includes the full control set. <b>Access Basic</b> and{' '}
          <b>Access Advanced</b> provide simplified or expanded accessibility layouts,
          and the <b>+</b> button lets you create a custom profile for a specific host
          or use case.
        </Step>

        <Step tag="App settings" title="Set appearance and orientation" imgs={[imgIosAppSettings]} reverse>
          Tap the <b>gear icon</b> on the home screen to open app-wide settings.
          Choose light, dark, or system appearance; lock control surfaces to portrait
          or landscape; and enable local session logging when you need a diagnostic
          record of HID events.
        </Step>

        <Step tag="Transport" title="Prepare the Bluetooth connection" imgs={[imgIosSetupPhone]}>
          Open either mode and tap <b>Prepare Transport</b>. iOS uses Core Bluetooth
          peripheral mode, so the app prepares the HID-over-GATT advertisement before
          you enter the control surface.
          <br /><br />
          When the status changes to <b>Connected to Development Preview</b>, tap{' '}
          <b>Enter Touch Mouse</b> or enter the gamepad mode. If the host does not
          discover the device, confirm Bluetooth permission for TabletHID in iOS
          Settings, then return to this screen and prepare the transport again.
        </Step>
      </div>

      <div className="walkthrough-block">
        <div className="walkthrough-block-label">Controls</div>

        <Step
          tag="Touch Mouse"
          title="Move the cursor and click"
          imgs={[imgIosMousePhone, imgIosTouchIpad]}
          compact
        >
          The Touch Mouse surface sends relative mouse movement to the connected
          host. Drag across the open area to move the cursor, then use the on-screen{' '}
          <b>L</b> and <b>R</b> controls for left and right click.
          <br /><br />
          On iPad, the same controls expand across the larger canvas, making it easier
          to place click targets where your hands naturally rest.
        </Step>

        <Step
          tag="Gamepad"
          title="Use the virtual controller"
          imgs={[imgIosGamepadIpad, imgIosGamepadPhone]}
          compact
          reverse
        >
          Gamepad mode exposes analog sticks, triggers, shoulder buttons, face buttons,
          D-pad directions, Back, and Start. Profile selection changes which controls
          are visible, so you can keep the layout dense for full control or simpler
          for accessibility-focused play.
          <br /><br />
          The status pill at the top confirms the active Bluetooth connection while
          the surface is sending input to the host.
        </Step>

        <Step tag="Settings" title="Tune the Touch Mouse layout" imgs={[imgIosSettingsIpad]}>
          Tap the <b>gear icon</b> from Touch Mouse to adjust sensitivity and button
          behavior. The settings sheet lets you enable each button, switch between
          static and dynamic zones, choose momentary or latching clicks, and tune
          offsets and radius for comfortable placement.
        </Step>
      </div>
    </>
  );
}

function AndroidWalkthrough() {
  return (
    <>
      {/* ── Getting started ── */}
      <div className="walkthrough-block">
        <div className="walkthrough-block-label">Getting started</div>

        <Step tag="Home screen" title="Choose a mode" imgs={[imgLanding, imgTabletLanding]} compact>
          The home screen presents two HID peripheral types. <b>Touch Mouse</b> turns
          the screen into a relative-movement trackpad with configurable click zones.
          <b> Gamepad</b> presents an Xbox-style virtual controller with a fully
          repositionable layout. Tap a card to open the Setup screen for that mode.
          <br /><br />
          The <b>Accessibility Profile</b> row at the top lets you save distinct
          control configurations. <b>Default</b> gives the full layout; <b>Basic</b>{' '}
          simplifies inputs for one-handed or switch-access use; <b>Advanced</b> adds
          extra bindings. Tap <b>+</b> to create and name a custom profile.
        </Step>

        <Step tag="First pair" title="Connect to a new host" imgs={[imgMousePairing, imgTabletMousePairing]} compact reverse>
          From the Setup screen, tap <b>Make Discoverable (new pair)</b>. The tablet
          advertises itself over Classic Bluetooth for up to 120 seconds. Switch the
          toggle at the top between <b>Windows</b> and <b>macOS</b> — the numbered
          steps update to match your host OS.
          <br /><br />
          On Windows, open <b>Settings → Bluetooth &amp; devices → Add device →
          Bluetooth</b> and wait for <b>TabletHID</b> to appear. Click it to pair —
          no PIN is required. The status bar at the top of the Setup screen will
          confirm the connection, and the <b>Enter Touch Mouse</b> (or Enter Gamepad)
          button becomes active.
        </Step>

        <Step
          tag="Reconnect"
          title="Return to a previously paired host"
          imgs={[imgGamepadConnect, imgGamepadReconnect]}
          compact
        >
          Once you've paired once, the Setup screen shows a <b>Quick connect</b> column
          alongside the new-pair steps. The app remembers up to ten paired hosts by
          name. Tap <b>Reconnect</b> to re-establish the Bluetooth link without going
          through the full pairing flow again.
          <br /><br />
          The status bar updates from idle to <b>Connected to [device name]</b> (right
          screenshot), and the Enter mode button becomes active. Reconnect works even
          after the app is closed and reopened — as long as the host hasn't removed
          the pairing.
        </Step>
      </div>

      {/* ── Touch Mouse ── */}
      <div className="walkthrough-block">
        <div className="walkthrough-block-label">Touch Mouse</div>

        <Step
          tag="Touch area"
          title="Moving the cursor and clicking"
          imgs={[imgMouseDynamic2, imgTabletMouseDynamic]}
          compact
          reverse
        >
          The entire dark area of the screen is the touch surface — slide a finger
          anywhere on it to move the host cursor with relative movement, just like a
          laptop trackpad. The <b>L</b> and <b>R</b> buttons handle left and right
          click respectively.
          <br /><br />
          In <b>Dynamic</b> zone mode (shown here) the buttons float near wherever
          you place your thumb, so you can reach them from any position on screen.
          The left screenshot shows the <b>L button highlighted in cyan</b> while
          it is being pressed, giving clear visual feedback for each click. In
          <b> Latching</b> mode the button stays pressed until you tap it again,
          useful for drag operations. On a tablet the wider canvas gives more room
          to spread click zones comfortably apart.
        </Step>

        <Step tag="Settings" title="Adjusting sensitivity and button behaviour" imgs={[imgMouseConfig, imgTabletMouseConfig]} compact>
          Tap the <b>gear icon</b> in the top-right corner to open <b>Touch Mouse
          Settings</b>. The <b>Mode</b> toggle switches between <b>Touch</b>{' '}
          (relative movement, like a trackpad) and <b>Mouse</b> (absolute positioning
          mapped to the full screen).
          <br /><br />
          The <b>Sensitivity</b> slider (1–10) scales how far the cursor moves per
          millimetre of finger travel. Below that, each button has its own section:
          enable or disable it with the toggle, choose <b>Static</b> or <b>Dynamic</b>{' '}
          zone type, and set <b>Momentary</b> (held while finger is down) or{' '}
          <b>Latching</b> (toggles on/off) click behaviour.
        </Step>

        <Step tag="Zone editing" title="Defining static click zones" imgs={[imgMouseStatic, imgTabletMouseStatic]} compact reverse>
          With <b>Static</b> zone type selected, tap <b>Set Zone (drag on screen)</b>{' '}
          to enter zone-editing mode. A banner at the top confirms you are drawing.
          <br /><br />
          Drag diagonally across the screen to draw a rectangle — the <b>dashed blue
          outline</b> shows the new zone in real time while the solid button shows
          the current saved position. Lift your finger to confirm. The click button
          will be anchored to that region whenever you are in Touch Mouse mode.
          Tap <b>Cancel</b> to discard the change.
        </Step>
      </div>

      {/* ── Gamepad ── */}
      <div className="walkthrough-block">
        <div className="walkthrough-block-label">Gamepad</div>

        <Step
          tag="Gamepad"
          title="Use the virtual controller"
          imgs={[imgGamepadLandscape, imgTabletGamepad]}
          compact
        >
          Gamepad mode presents a full Xbox-style layout in landscape orientation:
          two analog sticks, LT/RT analog triggers, LB/RB shoulder buttons, an ABXY
          face cluster, D-pad, Back, and Start.
          <br /><br />
          Tap <b>Edit Layout</b> in the settings sheet to drag any control to a new
          position or pinch to resize it independently on each axis. Layouts are saved
          per profile, so each use case keeps its own arrangement. On a tablet the
          wider screen gives each control more breathing room and makes it easier to
          reach both sticks without moving your thumbs far from rest.
        </Step>
      </div>
    </>
  );
}

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
        <Link to="/support"         className={`tab${tab === 'common'  ? ' active' : ''}`}>General</Link>
        <Link to="/support/ios"     className={`tab${tab === 'ios'     ? ' active' : ''}`}>iOS</Link>
        <Link to="/support/android" className={`tab${tab === 'android' ? ' active' : ''}`}>Android</Link>
      </div>

      {tab === 'ios' && <IosWalkthrough />}
      {tab === 'android' && <AndroidWalkthrough />}
      <LogInstructions platform={tab} />

      <div className="support-section">
        <h2>
          {tab === 'common'  && 'General Troubleshooting'}
          {tab === 'ios'     && 'iOS — iPad'}
          {tab === 'android' && 'Android — Troubleshooting'}
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
        <a href="github.com/Neuro-Mechatronics-Interfaces/TabletHID/issues" target="_blank" rel="noreferrer">
          Open an issue on GitHub →
        </a>
      </div>
    </div>
  );
}
