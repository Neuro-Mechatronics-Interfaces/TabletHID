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
          uses network requests only when you browse, apply, or intentionally upload
          shared layouts.
        </p>

        <h2>Data We Collect</h2>
        <p>For core Bluetooth control, we collect nothing. Specifically:</p>
        <ul>
          <li>No names, email addresses, or account information</li>
          <li>No usage analytics or crash telemetry</li>
          <li>No location data</li>
          <li>No advertising identifiers</li>
          <li>No device or config data sent to a TabletHID server unless you use Community Configs</li>
        </ul>

        <h2>Bluetooth Usage</h2>
        <p>
          TabletHID uses your device's Bluetooth hardware to act as a HID peripheral
          (mouse, gamepad, and keyboard macro reports). Bluetooth data (input
          reports) is sent directly to the paired host computer. Both platforms use
          BLE HID-over-GATT; the iOS transport is experimental pending physical-device
          validation. This data never leaves your local network and is never sent to
          any server operated by us or any third party.
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
        <p>
          Android requests <code>INTERNET</code> access for optional Community Config
          browsing, applying, and uploading. iOS uses standard HTTPS networking for
          the same optional Community Config feature and does not show a separate
          Internet permission prompt.
        </p>

        <h2>Local Storage</h2>
        <p>
          The app saves your configuration preferences (sensitivity, button layout,
          keyboard macros, profiles, Bluetooth/HoG server name, onboarding completion
          flag, last connected host address) to your device's local storage
          (SharedPreferences on Android, UserDefaults on iOS). The app also stores
          a local cache of Community Config records you browse so filtering and
          sorting remain fast. Local preferences do not leave your device unless you
          choose to upload a Community Config.
        </p>

        <h2>Community Configs</h2>
        <p>
          Community Configs are optional and user-initiated. Browsing configs requests
          public layout records from the TabletHID server. Applying a config requests
          the selected public record by ID so the app can import the latest copy; that
          request increments the public download count for the record. We do not use
          accounts and do not store who applied a config.
        </p>
        <p>
          Uploading a config sends the selected profile name, layout/config JSON,
          optional description, tags, category, app version, device model or hardware
          identifier, OS version/API level, and screen dimensions/density. Uploaded
          configs are public, including the profile name, description, tags, category,
          device/OS summary, screen-size metadata, and download count. Do not put
          personal information in the profile name, description, tags, or category.
        </p>
        <p>
          Community Configs are user-generated. Because profile names, descriptions,
          tags, and categories can be entered by users and are not pre-screened with a
          profanity filter, public listings may contain offensive or inappropriate
          language that we do not condone. When such content is detected, we may remove
          it, edit metadata, or take other corrective steps, but we cannot guarantee
          that every public listing will be free of inappropriate language.
        </p>
        <p>
          The server uses Cloudflare infrastructure and may process request IP
          addresses for routing, abuse prevention, and rate limiting. Community
          Config browse, apply, and upload requests are not used for advertising or
          tracking.
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
          Community Config uploads are optional and public. Children should not upload
          profile names, descriptions, tags, or categories that contain personal
          information.
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
