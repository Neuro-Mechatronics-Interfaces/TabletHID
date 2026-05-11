export default function Privacy() {
  return (
    <div className="page-narrow">
      <div className="prose">
        <h1>Privacy Policy</h1>
        <p className="lead">Last updated: May 2026</p>

        <h2>Overview</h2>
        <p>
          TabletHID is a Bluetooth HID peripheral app. Core input control is local
          Bluetooth between your device and your host computer. There are no accounts,
          no analytics, and no advertising. The optional Community Configs feature
          uses network requests only when you browse or intentionally upload shared
          layouts.
        </p>

        <h2>Data We Collect</h2>
        <p>We collect nothing. Specifically:</p>
        <ul>
          <li>No names, email addresses, or account information</li>
          <li>No usage analytics or crash telemetry</li>
          <li>No location data</li>
          <li>No advertising identifiers</li>
          <li>No device identifiers sent to any server unless you explicitly upload a public Community Config</li>
        </ul>

        <h2>Bluetooth Usage</h2>
        <p>
          TabletHID uses your device's Bluetooth hardware to act as a HID peripheral
          (mouse, gamepad, and keyboard macro reports). Bluetooth data (input
          reports) is sent directly to the paired host computer. Android uses the
          platform Bluetooth HID device APIs; iOS uses an experimental BLE
          HID-over-GATT transport. This data never leaves your local network and is
          never sent to any server operated by us or any third party.
        </p>
        <p>
          On Android, TabletHID requests <code>BLUETOOTH_ADVERTISE</code>,{' '}
          <code>BLUETOOTH_CONNECT</code>, and <code>BLUETOOTH_SCAN</code> permissions
          to broadcast the device as a Bluetooth LE peripheral and connect to your
          host computer. On iOS, the app requests Bluetooth access for the same
          purpose.
        </p>
        <p>
          On Android, a foreground service runs while the app is active to keep the
          Bluetooth connection alive when the screen is off or the app is in the
          background. This service displays a persistent notification for the duration
          of the session and performs no network activity.
        </p>

        <h2>Local Storage</h2>
        <p>
          The app saves your configuration preferences (sensitivity, button layout,
          keyboard macros, profiles, Bluetooth/HoG server name, onboarding completion
          flag, last connected host address) to your device's
          local storage (SharedPreferences on Android, UserDefaults on iOS). This
          data never leaves your device.
        </p>

        <h2>Community Configs</h2>
        <p>
          Community Configs are optional and user-initiated. Browsing configs requests
          public layout records from the TabletHID server. Uploading a config sends
          the selected profile name, layout/config JSON, optional description, tags,
          category, app version, device model, OS version, and screen dimensions.
          Uploaded configs are public, so do not put personal information in the
          profile name or description.
        </p>
        <p>
          The server uses Cloudflare infrastructure and may process request IP
          addresses for routing, abuse prevention, and rate limiting. Community
          Config uploads are not used for advertising or tracking.
        </p>

        <h2>Third-Party Services</h2>
        <p>
          TabletHID does not integrate with third-party SDKs, analytics services, or
          advertising networks. The optional Community Configs server is proxied
          through Cloudflare for hosting, routing, and rate limiting.
        </p>

        <h2>Children's Privacy</h2>
        <p>
          TabletHID does not knowingly collect any information from children under 13.
          Since we collect no personal data from anyone, no special handling for
          children is required.
        </p>

        <h2>Changes to This Policy</h2>
        <p>
          If we update this policy, the revised version will be posted at this URL
          with an updated date. Material changes will be noted in the app's release notes.
        </p>

        <h2>Contact</h2>
        <p>
          Questions about this privacy policy can be directed to the project's GitHub
          repository at{' '}
          <a href="https://github.com/Neuro-Mechatronics-Interfaces/TabletHID" target="_blank" rel="noreferrer"
            style={{ color: 'var(--primary)' }}>
            github.com/Neuro-Mechatronics-Interfaces/TabletHID
          </a>.
        </p>

        <p className="updated">
          This policy applies to TabletHID for Android and TabletHID for iOS.
        </p>
      </div>
    </div>
  );
}
