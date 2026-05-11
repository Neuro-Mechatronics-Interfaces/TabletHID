import { NavLink } from 'react-router-dom';

export default function Nav() {
  return (
    <nav className="nav">
      <div className="nav-inner">
        <NavLink to="/" className="nav-logo">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="2" y="5" width="20" height="14" rx="3"/>
            <circle cx="8.5" cy="12" r="1.5" fill="currentColor" stroke="none"/>
            <line x1="16" y1="9" x2="16" y2="15"/>
            <line x1="13" y1="12" x2="19" y2="12"/>
          </svg>
          TabletHID
        </NavLink>
        <div className="nav-links">
          <NavLink to="/" end className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>Home</NavLink>
          <NavLink to="/configs" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>Configs</NavLink>
          <NavLink to="/support" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>Support</NavLink>
          <NavLink to="/privacy" className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}>Privacy</NavLink>
        </div>
      </div>
    </nav>
  );
}
