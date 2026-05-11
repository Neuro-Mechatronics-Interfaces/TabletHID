import { Routes, Route, Navigate } from 'react-router-dom';
import Nav from './components/Nav.jsx';
import Footer from './components/Footer.jsx';
import Home from './pages/Home.jsx';
import Support from './pages/Support.jsx';
import Privacy from './pages/Privacy.jsx';
import Configs from './pages/Configs.jsx';
import CloneConfigPage from './pages/configs/CloneConfigPage.jsx';
import Admin from './pages/Admin.jsx';

export default function App() {
  return (
    <div className="site">
      <Nav />
      <main className="main">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/support" element={<Support />} />
          <Route path="/support/:platform" element={<Support />} />
          <Route path="/privacy" element={<Privacy />} />
          <Route path="/configs" element={<Configs />} />
          <Route path="/configs/clone/:id" element={<CloneConfigPage />} />
          <Route path="/admin" element={<Admin />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <Footer />
    </div>
  );
}
