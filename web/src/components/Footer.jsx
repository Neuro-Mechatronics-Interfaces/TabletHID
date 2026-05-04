import { Link } from 'react-router-dom';

export default function Footer() {
  return (
    <footer className="footer">
      <div className="footer-links">
        <Link to="/">Home</Link>
        <Link to="/support">Support</Link>
        <Link to="/support/ios">iOS Support</Link>
        <Link to="/support/android">Android Support</Link>
        <Link to="/privacy">Privacy Policy</Link>
        <a href="github.com/Neuro-Mechatronics-Interfaces/TabletHID" target="_blank" rel="noreferrer">GitHub</a>
      </div>
      <p className="footer-copy">© {new Date().getFullYear()} TabletHID. All rights reserved.</p>
    </footer>
  );
}
